package com.ethan.quickcrop.core.audio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.StatFs
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ethan.quickcrop.R
import com.ethan.quickcrop.ui.edit.audio.AudioEditActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class AudioRecordingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binder = LocalBinder()
    private val mutableState = MutableStateFlow(AudioRecordingState())
    private val stateLock = Any()
    private val waveformHistory = mutableListOf<Float>()
    private val markers = mutableListOf<AudioRecordingMarker>()

    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var outputFile: File? = null
    private var output: RandomAccessFile? = null
    private var pcmDataSize = 0L
    private var audioFocusRequest: AudioFocusRequest? = null
    private var lastNotificationSecond = -1L
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener(::handleAudioFocusChange)
    private val isReadInterrupted = AtomicBoolean(false)

    val state: StateFlow<AudioRecordingState> = mutableState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "音频录制服务已创建")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Android 8+ 要求前台服务启动后尽快展示通知，录音初始化放到通知之后执行。
                startForeground(NOTIFICATION_ID, buildNotification("正在准备录音", isPaused = false))
                startRecording()
            }
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
            ACTION_ADD_MARKER -> addMarker()
            ACTION_DISCARD -> discardRecording()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        synchronized(stateLock) {
            runCatching { audioRecord?.stop() }
            audioRecord?.release()
            audioRecord = null
            runCatching { output?.close() }
            output = null
        }
        abandonAudioFocus()
        serviceScope.cancel()
        Log.d(TAG, "音频录制服务已销毁")
        super.onDestroy()
    }

    private fun startRecording() {
        synchronized(stateLock) {
            if (mutableState.value.status == AudioRecordingStatus.Recording) {
                Log.d(TAG, "录音已经开始，忽略重复启动")
                return
            }
            if (mutableState.value.status == AudioRecordingStatus.Paused) {
                resumeRecording()
                return
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                failRecording("缺少录音权限，请授权后重试")
                return
            }
            if (!hasEnoughStorage()) {
                failRecording("存储空间不足，至少需要 10 MB 可用空间")
                return
            }

            runCatching {
                clearPreviousRecording(deleteFile = true)
                requestAudioFocus()
                val minBufferSize = AudioRecord.getMinBufferSize(
                    AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                check(minBufferSize > 0) { "设备不支持当前录音参数: $minBufferSize" }
                val safeBufferSize = maxOf(minBufferSize * 2, AUDIO_SAMPLE_RATE / 10)
                val recorder = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(AUDIO_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(safeBufferSize)
                    .build()
                check(recorder.state == AudioRecord.STATE_INITIALIZED) { "麦克风初始化失败" }

                val recordingDirectory = File(cacheDir, "audio_recordings").apply {
                    check(exists() || mkdirs()) { "创建录音缓存目录失败" }
                }
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
                val displayName = "录音_$timeStamp"
                val recordingFile = File(recordingDirectory, "$displayName.wav")
                val randomAccessFile = RandomAccessFile(recordingFile, "rw").apply {
                    setLength(0L)
                    WavAudioProcessor.writeEmptyHeader(this)
                }

                audioRecord = recorder
                outputFile = recordingFile
                output = randomAccessFile
                pcmDataSize = 0L
                waveformHistory.clear()
                markers.clear()
                mutableState.value = AudioRecordingState(
                    status = AudioRecordingStatus.Recording,
                    displayName = displayName
                )
                isReadInterrupted.set(false)
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "麦克风未进入录音状态" }
                recordingJob = serviceScope.launch {
                    // 每次读取约 21ms 音频，实时波形刷新可稳定达到 30fps 以上。
                    readAudioLoop(bufferSize = 1_024)
                }
                updateNotification(force = true)
                Log.d(TAG, "开始录音: ${recordingFile.absolutePath}")
            }.onFailure { throwable ->
                Log.e(TAG, "开始录音失败", throwable)
                failRecording(throwable.message ?: "开始录音失败")
            }
        }
    }

    private suspend fun readAudioLoop(bufferSize: Int) {
        val shortBuffer = ShortArray(bufferSize.coerceAtLeast(1_024))
        var consecutiveReadErrors = 0
        try {
            while (serviceScope.isActive) {
                val currentStatus = mutableState.value.status
                if (currentStatus == AudioRecordingStatus.Paused) {
                    delay(40L)
                    continue
                }
                if (currentStatus != AudioRecordingStatus.Recording) {
                    break
                }

                val recorder = synchronized(stateLock) { audioRecord } ?: break
                val readCount = recorder.read(shortBuffer, 0, shortBuffer.size, AudioRecord.READ_BLOCKING)
                when {
                    readCount > 0 -> {
                        consecutiveReadErrors = 0
                        processAudioBuffer(shortBuffer, readCount)
                    }
                    readCount == AudioRecord.ERROR_INVALID_OPERATION && (
                        isReadInterrupted.get() ||
                            mutableState.value.status != AudioRecordingStatus.Recording ||
                            recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING
                        ) -> {
                        // pause/stop 会主动打断阻塞读取，此返回码属于正常状态切换，不应提示录音失败。
                        consecutiveReadErrors = 0
                        delay(40L)
                    }
                    readCount < 0 -> {
                        consecutiveReadErrors += 1
                        if (consecutiveReadErrors >= MAX_CONSECUTIVE_READ_ERRORS) {
                            error("读取麦克风数据失败: $readCount")
                        }
                        // 部分设备在录音状态切换边缘会短暂返回负值，先重试，避免一次抖动终止整段录音。
                        Log.w(
                            TAG,
                            "麦克风读取暂时失败，准备重试: code=$readCount, count=$consecutiveReadErrors, " +
                                "status=${mutableState.value.status}, recordState=${recorder.recordingState}"
                        )
                        delay(30L)
                    }
                }
            }
        } catch (throwable: Throwable) {
            if (
                mutableState.value.status == AudioRecordingStatus.Recording &&
                !isReadInterrupted.get()
            ) {
                Log.e(TAG, "录音读取循环异常", throwable)
                failRecording(throwable.message ?: "录音过程发生异常")
            }
        }
    }

    private fun processAudioBuffer(buffer: ShortArray, readCount: Int) {
        if (mutableState.value.status != AudioRecordingStatus.Recording || isReadInterrupted.get()) {
            return
        }
        val currentOutput = synchronized(stateLock) { output } ?: return
        val byteBuffer = ByteArray(readCount * 2)
        var sumSquares = 0.0
        var maxAmplitude = 0
        repeat(readCount) { index ->
            val sample = buffer[index].toInt()
            byteBuffer[index * 2] = (sample and 0xFF).toByte()
            byteBuffer[index * 2 + 1] = (sample shr 8 and 0xFF).toByte()
            val absoluteSample = abs(sample)
            maxAmplitude = maxOf(maxAmplitude, absoluteSample)
            sumSquares += sample.toDouble() * sample.toDouble()
        }
        synchronized(stateLock) {
            currentOutput.write(byteBuffer)
            pcmDataSize += byteBuffer.size
        }

        val rms = sqrt(sumSquares / readCount.coerceAtLeast(1))
        val normalizedAmplitude = (rms / Short.MAX_VALUE).toFloat().coerceIn(0F, 1F)
        val peakNormalized = (maxAmplitude.toFloat() / Short.MAX_VALUE).coerceIn(0F, 1F)
        val peakDb = if (peakNormalized <= 0F) {
            MIN_AUDIO_DB
        } else {
            (20F * log10(peakNormalized)).coerceIn(MIN_AUDIO_DB, 0F)
        }
        appendWaveformSample(normalizedAmplitude)

        val elapsedMs = pcmDataSize * 1_000L / AUDIO_BYTES_PER_SECOND
        mutableState.value = mutableState.value.copy(
            status = AudioRecordingStatus.Recording,
            elapsedMs = elapsedMs,
            peakDb = peakDb,
            liveWaveform = buildLiveWaveform(buffer, readCount),
            recordedWaveform = waveformHistory.toList(),
            markers = markers.toList(),
            outputFile = outputFile,
            errorMessage = null
        )
        updateNotification(force = false)

        if (elapsedMs >= AUDIO_MAX_DURATION_MS) {
            Log.i(TAG, "录音已达到 60 分钟上限，自动停止")
            stopRecording(completionMessage = "录音已达到 60 分钟上限，已自动停止并保存")
        }
    }

    private fun pauseRecording() {
        synchronized(stateLock) {
            if (mutableState.value.status != AudioRecordingStatus.Recording) {
                return
            }
            isReadInterrupted.set(true)
            // 先切换状态再停止 AudioRecord，避免阻塞读取返回 -3 时仍被误判为录音中异常。
            mutableState.value = mutableState.value.copy(
                status = AudioRecordingStatus.Paused,
                peakDb = MIN_AUDIO_DB,
                liveWaveform = List(LIVE_WAVEFORM_BAR_COUNT) { 0.08F }
            )
            runCatching { audioRecord?.stop() }
                .onFailure { throwable -> Log.w(TAG, "暂停 AudioRecord 失败", throwable) }
            updateNotification(force = true)
            Log.d(TAG, "录音已暂停: ${mutableState.value.elapsedMs}ms")
        }
    }

    private fun resumeRecording() {
        synchronized(stateLock) {
            if (mutableState.value.status != AudioRecordingStatus.Paused) {
                return
            }
            runCatching {
                val recorder = requireNotNull(audioRecord) { "录音器已释放" }
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "麦克风恢复失败" }
                isReadInterrupted.set(false)
                mutableState.value = mutableState.value.copy(status = AudioRecordingStatus.Recording)
                updateNotification(force = true)
                Log.d(TAG, "继续录音: ${mutableState.value.elapsedMs}ms")
            }.onFailure { throwable ->
                Log.e(TAG, "继续录音失败", throwable)
                failRecording(throwable.message ?: "继续录音失败")
            }
        }
    }

    private fun stopRecording(completionMessage: String? = null) {
        synchronized(stateLock) {
            val status = mutableState.value.status
            if (status != AudioRecordingStatus.Recording && status != AudioRecordingStatus.Paused) {
                return
            }
            isReadInterrupted.set(true)
            mutableState.value = mutableState.value.copy(status = AudioRecordingStatus.Completed)
            runCatching { audioRecord?.stop() }
            audioRecord?.release()
            audioRecord = null
            isReadInterrupted.set(true)
            recordingJob?.cancel()
            recordingJob = null
            runCatching {
                output?.let { currentOutput ->
                    WavAudioProcessor.finalizeHeader(currentOutput, pcmDataSize)
                    currentOutput.close()
                }
            }.onFailure { throwable ->
                Log.e(TAG, "写入 WAV 文件头失败", throwable)
                failRecording("录音文件保存失败")
                return
            }
            output = null
            abandonAudioFocus()
            mutableState.value = mutableState.value.copy(
                status = AudioRecordingStatus.Completed,
                peakDb = MIN_AUDIO_DB,
                liveWaveform = List(LIVE_WAVEFORM_BAR_COUNT) { 0.08F },
                recordedWaveform = waveformHistory.toList(),
                markers = markers.toList(),
                outputFile = outputFile,
                // 自动停止提示沿用页面的一次性消息通道，完成态仍可正常播放和裁剪。
                errorMessage = completionMessage
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            Log.d(TAG, "录音已完成: file=${outputFile?.absolutePath}, duration=${mutableState.value.elapsedMs}ms")
        }
    }

    private fun addMarker() {
        synchronized(stateLock) {
            if (mutableState.value.status != AudioRecordingStatus.Recording) {
                return
            }
            val marker = AudioRecordingMarker(
                positionMs = mutableState.value.elapsedMs,
                label = "标记 ${markers.size + 1}"
            )
            markers += marker
            mutableState.value = mutableState.value.copy(markers = markers.toList())
            Log.d(TAG, "新增录音标记: $marker")
        }
    }

    private fun discardRecording() {
        synchronized(stateLock) {
            isReadInterrupted.set(true)
            runCatching { audioRecord?.stop() }
            audioRecord?.release()
            audioRecord = null
            recordingJob?.cancel()
            recordingJob = null
            runCatching { output?.close() }
            output = null
            abandonAudioFocus()
            clearPreviousRecording(deleteFile = true)
            mutableState.value = AudioRecordingState()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Log.d(TAG, "已丢弃当前录音")
        }
    }

    private fun failRecording(message: String) {
        synchronized(stateLock) {
            isReadInterrupted.set(true)
            runCatching { audioRecord?.stop() }
            audioRecord?.release()
            audioRecord = null
            recordingJob?.cancel()
            recordingJob = null
            runCatching { output?.close() }
            output = null
            abandonAudioFocus()
            outputFile?.delete()
            outputFile = null
            mutableState.value = AudioRecordingState(
                status = AudioRecordingStatus.Error,
                errorMessage = message
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun clearPreviousRecording(deleteFile: Boolean) {
        if (deleteFile) {
            outputFile?.let { file ->
                if (file.exists() && !file.delete()) {
                    Log.w(TAG, "旧录音缓存删除失败: ${file.absolutePath}")
                }
            }
        }
        outputFile = null
        output = null
        pcmDataSize = 0L
        waveformHistory.clear()
        markers.clear()
        lastNotificationSecond = -1L
    }

    private fun buildLiveWaveform(buffer: ShortArray, readCount: Int): List<Float> {
        if (readCount <= 0) {
            return List(LIVE_WAVEFORM_BAR_COUNT) { 0.08F }
        }
        val segmentSize = (readCount / LIVE_WAVEFORM_BAR_COUNT).coerceAtLeast(1)
        return List(LIVE_WAVEFORM_BAR_COUNT) { barIndex ->
            val start = barIndex * segmentSize
            val end = minOf(readCount, start + segmentSize)
            if (start >= end) {
                0.08F
            } else {
                var maxValue = 0
                for (index in start until end) {
                    maxValue = maxOf(maxValue, abs(buffer[index].toInt()))
                }
                (maxValue.toFloat() / Short.MAX_VALUE)
                    .coerceIn(0.08F, 1F)
            }
        }
    }

    private fun appendWaveformSample(amplitude: Float) {
        waveformHistory += amplitude.coerceIn(0.04F, 1F)
        if (waveformHistory.size > MAX_RECORDED_WAVEFORM_POINTS * 2) {
            val compressed = waveformHistory
                .chunked(2)
                .map { pair -> pair.maxOrNull() ?: 0.04F }
            waveformHistory.clear()
            waveformHistory.addAll(compressed)
        }
    }

    private fun hasEnoughStorage(): Boolean {
        return runCatching {
            val statFs = StatFs(cacheDir.absolutePath)
            statFs.availableBytes >= MIN_AVAILABLE_STORAGE_BYTES
        }.onFailure { throwable ->
            Log.w(TAG, "检查存储空间失败，继续尝试录音", throwable)
        }.getOrDefault(true)
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
        }
    }

    private fun abandonAudioFocus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        if (
            focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            // 来电或其他应用抢占音频焦点时自动暂停，避免录入系统提示音或通话内容。
            pauseRecording()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "音频录制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示 QuickCrop 正在录制音频"
                setSound(null, null)
            }
        )
    }

    private fun updateNotification(force: Boolean) {
        val elapsedSecond = mutableState.value.elapsedMs / 1_000L
        if (!force && elapsedSecond == lastNotificationSecond) {
            return
        }
        lastNotificationSecond = elapsedSecond
        val isPaused = mutableState.value.status == AudioRecordingStatus.Paused
        val notification = buildNotification(
            content = if (isPaused) "录音已暂停 · ${formatNotificationDuration(mutableState.value.elapsedMs)}" else "正在录音 · ${formatNotificationDuration(mutableState.value.elapsedMs)}",
            isPaused = isPaused
        )
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(content: String, isPaused: Boolean): Notification {
        val openActivityIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN_ACTIVITY,
            Intent(this, AudioEditActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseOrResumeAction = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        val pauseOrResumeTitle = if (isPaused) "继续" else "暂停"
        val pauseOrResumeIcon = if (isPaused) R.drawable.fa_play else R.drawable.fa_pause
        val pauseOrResumeIntent = PendingIntent.getService(
            this,
            REQUEST_PAUSE_RESUME,
            Intent(this, AudioRecordingService::class.java).setAction(pauseOrResumeAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, AudioRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.fa_music)
            .setContentTitle("QuickCrop 录音")
            .setContentText(content)
            .setContentIntent(openActivityIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(pauseOrResumeIcon, pauseOrResumeTitle, pauseOrResumeIntent)
            .addAction(R.drawable.fa_stop, "停止", stopIntent)
            .build()
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioRecordingService = this@AudioRecordingService
    }

    companion object {
        private const val TAG = "AudioRecordingService"
        private const val NOTIFICATION_CHANNEL_ID = "quickcrop_audio_recording"
        private const val NOTIFICATION_ID = 2_601
        private const val REQUEST_OPEN_ACTIVITY = 1
        private const val REQUEST_PAUSE_RESUME = 2
        private const val REQUEST_STOP = 3
        private const val MIN_AVAILABLE_STORAGE_BYTES = 10L * 1024L * 1024L
        private const val MAX_RECORDED_WAVEFORM_POINTS = 240
        private const val MAX_CONSECUTIVE_READ_ERRORS = 3
        private const val ACTION_START = "com.ethan.quickcrop.audio.START"
        private const val ACTION_PAUSE = "com.ethan.quickcrop.audio.PAUSE"
        private const val ACTION_RESUME = "com.ethan.quickcrop.audio.RESUME"
        private const val ACTION_STOP = "com.ethan.quickcrop.audio.STOP"
        private const val ACTION_ADD_MARKER = "com.ethan.quickcrop.audio.ADD_MARKER"
        private const val ACTION_DISCARD = "com.ethan.quickcrop.audio.DISCARD"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AudioRecordingService::class.java).setAction(ACTION_START)
            )
        }

        fun pause(context: Context) {
            context.startService(Intent(context, AudioRecordingService::class.java).setAction(ACTION_PAUSE))
        }

        fun resume(context: Context) {
            context.startService(Intent(context, AudioRecordingService::class.java).setAction(ACTION_RESUME))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AudioRecordingService::class.java).setAction(ACTION_STOP))
        }

        fun addMarker(context: Context) {
            context.startService(Intent(context, AudioRecordingService::class.java).setAction(ACTION_ADD_MARKER))
        }

        fun discard(context: Context) {
            context.startService(Intent(context, AudioRecordingService::class.java).setAction(ACTION_DISCARD))
        }

        private fun formatNotificationDuration(durationMs: Long): String {
            val totalSeconds = durationMs / 1_000L
            val hours = totalSeconds / 3_600L
            val minutes = totalSeconds % 3_600L / 60L
            val seconds = totalSeconds % 60L
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        }
    }
}
