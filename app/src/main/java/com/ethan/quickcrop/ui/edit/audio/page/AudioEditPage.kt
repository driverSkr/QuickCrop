package com.ethan.quickcrop.ui.edit.audio.page

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ethan.quickcrop.R
import com.ethan.quickcrop.core.audio.AudioRecordingMarker
import com.ethan.quickcrop.core.audio.AudioRecordingService
import com.ethan.quickcrop.core.audio.AudioRecordingState
import com.ethan.quickcrop.core.audio.AudioRecordingStatus
import com.ethan.quickcrop.core.audio.MIN_AUDIO_DB
import com.ethan.quickcrop.core.audio.WavAudioProcessor
import com.ethan.quickcrop.extension.finishActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

private const val TAG = "AudioEditPage"
private const val MIN_TRIM_DURATION_MS = 500L

@Composable
fun AudioEditPage(
    onExportCompleted: (Uri) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val audioService = rememberAudioRecordingService()
    val fallbackState = remember { MutableStateFlow(AudioRecordingState()) }
    val stateFlow = audioService?.state ?: fallbackState
    val recordingState by stateFlow.collectAsState()
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var pendingStartAfterPermission by remember { mutableStateOf(false) }
    var pendingExportAfterPermission by remember { mutableStateOf(false) }
    var trimStartFraction by remember { mutableFloatStateOf(0F) }
    var trimEndFraction by remember { mutableFloatStateOf(1F) }

    val performExport: () -> Unit = {
        val sourceFile = recordingState.outputFile
        if (sourceFile == null || !sourceFile.exists()) {
            Toast.makeText(context, "录音文件不存在，请重新录制", Toast.LENGTH_SHORT).show()
        } else {
            isExporting = true
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    WavAudioProcessor.exportSelectionToMusic(
                        context = context.applicationContext,
                        sourceFile = sourceFile,
                        displayName = recordingState.displayName,
                        startMs = (recordingState.elapsedMs * trimStartFraction).toLong(),
                        endMs = (recordingState.elapsedMs * trimEndFraction).toLong()
                    )
                }
                isExporting = false
                result.onSuccess { outputUri ->
                    Log.d(TAG, "音频导出成功: $outputUri")
                    Toast.makeText(context, "音频已保存到 Music/QuickCrop", Toast.LENGTH_SHORT).show()
                    // 导出完成后删除应用缓存并结束录音服务，系统音乐目录中的成品不受影响。
                    AudioRecordingService.discard(context)
                    onExportCompleted(outputUri)
                }.onFailure { throwable ->
                    Log.e(TAG, "音频导出失败", throwable)
                    Toast.makeText(context, "音频保存失败，请稍后重试", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionResult ->
        val audioGranted = permissionResult[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (audioGranted && pendingStartAfterPermission) {
            pendingStartAfterPermission = false
            AudioRecordingService.start(context)
        } else if (!audioGranted) {
            pendingStartAfterPermission = false
            showPermissionDialog = true
            Log.w(TAG, "用户拒绝录音权限")
        }

        if (pendingExportAfterPermission) {
            pendingExportAfterPermission = false
            val storageGranted = Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
                permissionResult[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            if (storageGranted) {
                performExport()
            } else {
                Toast.makeText(context, "需要存储权限才能保存到系统音乐目录", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun requestStartRecording() {
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasAudioPermission) {
            AudioRecordingService.start(context)
            return
        }

        pendingStartAfterPermission = true
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    fun requestExport() {
        val requiresLegacyStoragePermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (requiresLegacyStoragePermission) {
            pendingExportAfterPermission = true
            permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
        } else {
            performExport()
        }
    }

    val hasUnsavedRecording = recordingState.status == AudioRecordingStatus.Recording ||
        recordingState.status == AudioRecordingStatus.Paused ||
        recordingState.status == AudioRecordingStatus.Completed

    fun requestLeavePage() {
        if (hasUnsavedRecording) {
            showDiscardDialog = true
        } else {
            context.finishActivity()
        }
    }

    BackHandler(true) {
        requestLeavePage()
    }

    LaunchedEffect(recordingState.errorMessage) {
        recordingState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(recordingState.outputFile) {
        trimStartFraction = 0F
        trimEndFraction = 1F
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0F))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AudioTopBar(
            status = recordingState.status,
            durationMs = recordingState.elapsedMs,
            onBack = { requestLeavePage() }
        )

        AnimatedContent(
            targetState = recordingState.status,
            modifier = Modifier
                .weight(1F)
                .fillMaxWidth(),
            label = "audioEditorState"
        ) { status ->
            when (status) {
                AudioRecordingStatus.Idle,
                AudioRecordingStatus.Error -> {
                    AudioIdleContent(
                        hasError = status == AudioRecordingStatus.Error,
                        onStartRecording = { requestStartRecording() }
                    )
                }

                AudioRecordingStatus.Recording,
                AudioRecordingStatus.Paused -> {
                    AudioRecordingContent(
                        state = recordingState,
                        onPauseOrResume = {
                            if (status == AudioRecordingStatus.Recording) {
                                AudioRecordingService.pause(context)
                            } else {
                                AudioRecordingService.resume(context)
                            }
                        },
                        onStop = { AudioRecordingService.stop(context) },
                        onAddMarker = {
                            AudioRecordingService.addMarker(context)
                            Toast.makeText(context, "已添加标记", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                AudioRecordingStatus.Completed -> {
                    AudioCompletedContent(
                        state = recordingState,
                        trimStartFraction = trimStartFraction,
                        trimEndFraction = trimEndFraction,
                        isExporting = isExporting,
                        onTrimStartChanged = { trimStartFraction = it },
                        onTrimEndChanged = { trimEndFraction = it },
                        onRerecord = {
                            coroutineScope.launch {
                                AudioRecordingService.discard(context)
                                delay(120L)
                                requestStartRecording()
                            }
                        },
                        onExport = { requestExport() }
                    )
                }
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            containerColor = Color(0xFF18181B),
            title = { Text(text = "需要麦克风权限", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "录制音频需要访问麦克风，请前往系统设置开启权限后重试。",
                    color = Color(0xFFD1D5DB)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        }.onFailure { throwable ->
                            Log.w(TAG, "打开应用权限设置失败", throwable)
                            Toast.makeText(context, "无法打开系统设置", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(text = "去设置", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(text = "取消", color = Color(0xFF9CA3AF))
                }
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            containerColor = Color(0xFF18181B),
            title = { Text(text = "放弃当前录音？", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "退出后当前录音和裁剪设置都会被删除。",
                    color = Color(0xFFD1D5DB)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        AudioRecordingService.discard(context)
                        context.finishActivity()
                    }
                ) {
                    Text(text = "确认放弃", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(text = "继续编辑", color = Color(0xFF9CA3AF))
                }
            }
        )
    }
}

@Composable
private fun AudioTopBar(
    status: AudioRecordingStatus,
    durationMs: Long,
    onBack: () -> Unit
) {
    val title = if (status == AudioRecordingStatus.Completed) "录音完成" else "录音"
    val durationColor = if (status == AudioRecordingStatus.Completed) Color(0xFF4ADE80) else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AudioIcon(
            iconRes = R.drawable.svg_icon_back,
            tint = Color(0xFF9CA3AF),
            contentDescription = "返回",
            modifier = Modifier
                .size(24.dp)
                .clickable(role = Role.Button, onClick = onBack)
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp)
        )
        Spacer(modifier = Modifier.weight(1F))
        Text(
            text = formatRecordingDuration(durationMs),
            color = durationColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1F2937))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun AudioIdleContent(
    hasError: Boolean,
    onStartRecording: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.7F))
        RecordingStatusIndicator(status = AudioRecordingStatus.Idle)
        Text(
            text = if (hasError) "录音未能开始" else "准备录音",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            text = if (hasError) "请检查麦克风权限或设备状态后重试" else "使用 48kHz 高质量 WAV 格式录制",
            color = Color(0xFF9CA3AF),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(48.dp))
        AudioRoundButton(
            iconRes = R.drawable.fa_microphone,
            backgroundColor = Color(0xFF2563EB),
            contentDescription = "开始录音",
            size = 72,
            onClick = onStartRecording
        )
        Text(
            text = "点击开始录音",
            color = Color(0xFFD1D5DB),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 14.dp)
        )
        Spacer(modifier = Modifier.weight(1F))
        Text(
            text = "最长可录制 60 分钟，来电或音频焦点被占用时会自动暂停",
            color = Color(0xFF6B7280),
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 28.dp)
        )
    }
}

@Composable
private fun AudioRecordingContent(
    state: AudioRecordingState,
    onPauseOrResume: () -> Unit,
    onStop: () -> Unit,
    onAddMarker: () -> Unit
) {
    val isRecording = state.status == AudioRecordingStatus.Recording
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RecordingStatusIndicator(status = state.status)
        Text(
            text = if (isRecording) "正在录音" else "录音已暂停",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            text = if (isRecording) "点击下方按钮停止录音" else "点击继续按钮恢复录音",
            color = Color(0xFF9CA3AF),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 24.dp)
        )

        LiveWaveformPanel(
            waveform = state.liveWaveform,
            peakDb = state.peakDb,
            isRecording = isRecording
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AudioRoundButton(
                iconRes = if (isRecording) R.drawable.fa_pause else R.drawable.fa_play,
                backgroundColor = if (isRecording) Color(0xFF374151) else Color(0xFF2563EB),
                contentDescription = if (isRecording) "暂停录音" else "继续录音",
                size = 56,
                onClick = onPauseOrResume
            )
            AudioRoundButton(
                iconRes = R.drawable.fa_stop,
                backgroundColor = Color(0xFFEF4444),
                contentDescription = "停止录音",
                size = 68,
                onClick = onStop
            )
            AudioRoundButton(
                iconRes = R.drawable.fa_bookmark,
                backgroundColor = Color(0xFF374151),
                contentDescription = "添加标记",
                size = 56,
                enabled = isRecording,
                onClick = onAddMarker
            )
        }

        Text(
            text = "录音结束后可进行裁剪和播放预览",
            color = Color(0xFF6B7280),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 24.dp)
        )
        if (state.markers.isNotEmpty()) {
            Text(
                text = "已添加 ${state.markers.size} 个标记",
                color = Color(0xFF3B82F6),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun RecordingStatusIndicator(status: AudioRecordingStatus) {
    val isRecording = status == AudioRecordingStatus.Recording
    val isPaused = status == AudioRecordingStatus.Paused
    val centerColor = when {
        isRecording -> Color(0xFFEF4444)
        isPaused -> Color(0xFFF59E0B)
        else -> Color(0xFF4B5563)
    }
    val infiniteTransition = rememberInfiniteTransition(label = "recordingPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0F,
        targetValue = 1F,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "recordingPulseValue"
    )

    Box(
        modifier = Modifier.size(112.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isRecording) {
                drawCircle(
                    color = Color(0xFFEF4444).copy(alpha = 0.34F * (1F - pulse)),
                    radius = 40.dp.toPx() + 18.dp.toPx() * pulse,
                    center = center
                )
            }
        }
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(centerColor),
            contentAlignment = Alignment.Center
        ) {
            AudioIcon(
                iconRes = if (isPaused) R.drawable.fa_pause else R.drawable.fa_microphone,
                tint = Color.White,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
        }
        if (isRecording) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "REC", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LiveWaveformPanel(
    waveform: List<Float>,
    peakDb: Float,
    isRecording: Boolean
) {
    val isOverload = peakDb > -3F
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111827))
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
        ) {
            val bars = waveform.ifEmpty { List(18) { 0.08F } }
            val gap = size.width / (bars.size * 2F)
            bars.forEachIndexed { index, amplitude ->
                val barHeight = size.height * if (isRecording) amplitude.coerceIn(0.08F, 1F) else 0.08F
                val x = gap + index * gap * 2F
                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF60A5FA), Color(0xFF2563EB))
                    ),
                    start = Offset(x, (size.height - barHeight) / 2F),
                    end = Offset(x, (size.height + barHeight) / 2F),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "-∞ dB", color = Color(0xFF6B7280), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(
                text = if (isRecording) "${peakDb.toInt()} dB 峰值" else "已暂停",
                color = if (isOverload) Color(0xFFEF4444) else Color(0xFF3B82F6),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(text = "0 dB", color = Color(0xFF6B7280), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        if (isOverload) {
            Text(
                text = "音量过高，请适当远离麦克风",
                color = Color(0xFFEF4444),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun AudioCompletedContent(
    state: AudioRecordingState,
    trimStartFraction: Float,
    trimEndFraction: Float,
    isExporting: Boolean,
    onTrimStartChanged: (Float) -> Unit,
    onTrimEndChanged: (Float) -> Unit,
    onRerecord: () -> Unit,
    onExport: () -> Unit
) {
    val context = LocalContext.current
    val sourceFile = state.outputFile
    var isPlaying by remember { mutableStateOf(false) }
    var playbackPositionMs by remember { mutableLongStateOf(0L) }
    var playerDurationMs by remember { mutableLongStateOf(state.elapsedMs.coerceAtLeast(1L)) }
    val player = remember(sourceFile) {
        sourceFile?.takeIf(File::exists)?.let { file ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                playWhenReady = false
                prepare()
            }
        }
    }

    DisposableEffect(player) {
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                    isPlaying = isPlayingNow
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (player.duration > 0L) {
                        playerDurationMs = player.duration
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        player.seekTo((playerDurationMs * trimStartFraction).toLong())
                    }
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }
    }

    LaunchedEffect(player, trimStartFraction, trimEndFraction, playerDurationMs) {
        while (true) {
            val currentPosition = player?.currentPosition ?: 0L
            playbackPositionMs = currentPosition
            val trimEndMs = (playerDurationMs * trimEndFraction).toLong()
            if (player?.isPlaying == true && currentPosition >= trimEndMs) {
                player.pause()
                player.seekTo((playerDurationMs * trimStartFraction).toLong())
            }
            delay(100L)
        }
    }

    fun seekToFraction(fraction: Float) {
        player?.seekTo((playerDurationMs * fraction.coerceIn(0F, 1F)).toLong())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF22C55E)),
            contentAlignment = Alignment.Center
        ) {
            AudioIcon(
                iconRes = R.drawable.fa_check,
                tint = Color.White,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = "录音已完成",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            text = "可进行裁剪和播放预览",
            color = Color(0xFF9CA3AF),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 20.dp)
        )

        AudioPreviewPanel(
            fileName = state.displayName.ifBlank { "录音" },
            durationMs = playerDurationMs,
            playbackPositionMs = playbackPositionMs,
            isPlaying = isPlaying,
            onPlayToggle = {
                player?.let { currentPlayer ->
                    val trimStartMs = (playerDurationMs * trimStartFraction).toLong()
                    val trimEndMs = (playerDurationMs * trimEndFraction).toLong()
                    if (currentPlayer.isPlaying) {
                        currentPlayer.pause()
                    } else {
                        if (currentPlayer.currentPosition !in trimStartMs until trimEndMs) {
                            currentPlayer.seekTo(trimStartMs)
                        }
                        currentPlayer.play()
                    }
                }
            },
            onSeek = { fraction -> seekToFraction(fraction) }
        )
        Spacer(modifier = Modifier.height(12.dp))

        AudioTrimPanel(
            waveform = state.recordedWaveform,
            durationMs = playerDurationMs,
            playbackPositionMs = playbackPositionMs,
            startFraction = trimStartFraction,
            endFraction = trimEndFraction,
            onStartChanged = { next ->
                val minGap = minimumTrimFraction(playerDurationMs)
                val safeValue = next.coerceIn(0F, trimEndFraction - minGap)
                onTrimStartChanged(safeValue)
                seekToFraction(safeValue)
            },
            onEndChanged = { next ->
                val minGap = minimumTrimFraction(playerDurationMs)
                val safeValue = next.coerceIn(trimStartFraction + minGap, 1F)
                onTrimEndChanged(safeValue)
                seekToFraction(safeValue)
            },
            onDeleteBefore = {
                val playheadFraction = (playbackPositionMs.toFloat() / playerDurationMs).coerceIn(0F, 1F)
                val minGap = minimumTrimFraction(playerDurationMs)
                val safeValue = playheadFraction.coerceIn(trimStartFraction, trimEndFraction - minGap)
                onTrimStartChanged(safeValue)
                seekToFraction(safeValue)
            },
            onDeleteAfter = {
                val playheadFraction = (playbackPositionMs.toFloat() / playerDurationMs).coerceIn(0F, 1F)
                val minGap = minimumTrimFraction(playerDurationMs)
                val safeValue = playheadFraction.coerceIn(trimStartFraction + minGap, trimEndFraction)
                onTrimEndChanged(safeValue)
                seekToFraction(safeValue)
            }
        )

        if (state.markers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            AudioMarkerPanel(
                markers = state.markers,
                onMarkerClick = { marker ->
                    player?.seekTo(marker.positionMs.coerceIn(0L, playerDurationMs))
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AudioActionButton(
                text = "重新录音",
                backgroundColor = Color(0xFF1F2937),
                textColor = Color.White,
                enabled = !isExporting,
                modifier = Modifier.weight(0.42F),
                onClick = onRerecord
            )
            AudioActionButton(
                text = if (isExporting) "正在保存..." else "完成，进入编辑",
                backgroundColor = Color.White,
                textColor = Color.Black,
                enabled = !isExporting,
                modifier = Modifier.weight(0.58F),
                onClick = onExport
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun AudioPreviewPanel(
    fileName: String,
    durationMs: Long,
    playbackPositionMs: Long,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onSeek: (Float) -> Unit
) {
    val progress = if (durationMs > 0L) {
        (playbackPositionMs.toFloat() / durationMs).coerceIn(0F, 1F)
    } else {
        0F
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111827))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2563EB)),
                contentAlignment = Alignment.Center
            ) {
                AudioIcon(
                    iconRes = R.drawable.fa_music,
                    tint = Color.White,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1F)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = fileName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = "${formatShortDuration(durationMs)} · WAV · 48kHz",
                    color = Color(0xFF6B7280),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            AudioRoundButton(
                iconRes = if (isPlaying) R.drawable.fa_pause else R.drawable.fa_play,
                backgroundColor = Color(0xFF2563EB),
                contentDescription = if (isPlaying) "暂停播放" else "播放录音",
                size = 46,
                onClick = onPlayToggle
            )
        }
        Slider(
            value = progress,
            onValueChange = onSeek,
            valueRange = 0F..1F,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF3B82F6),
                inactiveTrackColor = Color(0xFF374151)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatShortDuration(playbackPositionMs), color = Color(0xFF6B7280), fontSize = 10.sp)
            Text(text = formatShortDuration(durationMs), color = Color(0xFF6B7280), fontSize = 10.sp)
        }
    }
}

@Composable
private fun AudioTrimPanel(
    waveform: List<Float>,
    durationMs: Long,
    playbackPositionMs: Long,
    startFraction: Float,
    endFraction: Float,
    onStartChanged: (Float) -> Unit,
    onEndChanged: (Float) -> Unit,
    onDeleteBefore: () -> Unit,
    onDeleteAfter: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111827))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "音频裁剪", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = "拖动手柄裁剪片段", color = Color(0xFF9CA3AF), fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        AudioTrimWaveform(
            waveform = waveform,
            playbackFraction = (playbackPositionMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0F, 1F),
            startFraction = startFraction,
            endFraction = endFraction,
            onStartChanged = onStartChanged,
            onEndChanged = onEndChanged,
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatShortDuration((durationMs * startFraction).toLong()),
                color = Color(0xFF6B7280),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "已选 ${formatShortDuration((durationMs * (endFraction - startFraction)).toLong())}",
                color = Color(0xFF3B82F6),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = formatShortDuration((durationMs * endFraction).toLong()),
                color = Color(0xFF6B7280),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AudioTrimShortcut(
                text = "删除前段",
                modifier = Modifier.weight(1F),
                onClick = onDeleteBefore
            )
            AudioTrimShortcut(
                text = "删除后段",
                modifier = Modifier.weight(1F),
                onClick = onDeleteAfter
            )
        }
    }
}

@Composable
private fun AudioTrimWaveform(
    waveform: List<Float>,
    playbackFraction: Float,
    startFraction: Float,
    endFraction: Float,
    onStartChanged: (Float) -> Unit,
    onEndChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeHandle by remember { mutableStateOf(AudioTrimHandle.None) }
    val latestStartFraction by rememberUpdatedState(startFraction)
    val latestEndFraction by rememberUpdatedState(endFraction)
    val latestOnStartChanged by rememberUpdatedState(onStartChanged)
    val latestOnEndChanged by rememberUpdatedState(onEndChanged)
    val bars = remember(waveform) {
        waveform.ifEmpty {
            listOf(0.35F, 0.6F, 0.85F, 0.55F, 0.9F, 0.72F, 0.48F, 0.78F, 0.66F, 0.42F, 0.84F, 0.55F)
        }.toDisplayWaveform(maxPointCount = 64)
    }
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1F2937))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { touch ->
                        val touchFraction = (touch.x / size.width).coerceIn(0F, 1F)
                        activeHandle = if (
                            abs(touchFraction - latestStartFraction) <=
                            abs(touchFraction - latestEndFraction)
                        ) {
                            AudioTrimHandle.Start
                        } else {
                            AudioTrimHandle.End
                        }
                    },
                    onDragEnd = { activeHandle = AudioTrimHandle.None },
                    onDragCancel = { activeHandle = AudioTrimHandle.None },
                    onDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / size.width).coerceIn(0F, 1F)
                        when (activeHandle) {
                            AudioTrimHandle.Start -> latestOnStartChanged(fraction)
                            AudioTrimHandle.End -> latestOnEndChanged(fraction)
                            AudioTrimHandle.None -> Unit
                        }
                    }
                )
            }
    ) {
        val selectionLeft = size.width * startFraction
        val selectionRight = size.width * endFraction
        val gap = size.width / (bars.size * 2F)
        bars.forEachIndexed { index, amplitude ->
            val x = gap + index * gap * 2F
            val barHeight = size.height * amplitude.coerceIn(0.08F, 0.9F)
            drawLine(
                color = if (x in selectionLeft..selectionRight) Color(0xFF60A5FA) else Color(0xFF4B5563),
                start = Offset(x, (size.height - barHeight) / 2F),
                end = Offset(x, (size.height + barHeight) / 2F),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        drawRoundRect(
            color = Color(0xFF3B82F6).copy(alpha = 0.16F),
            topLeft = Offset(selectionLeft, 0F),
            size = Size((selectionRight - selectionLeft).coerceAtLeast(0F), size.height),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        drawLine(
            color = Color(0xFF3B82F6),
            start = Offset(selectionLeft, 0F),
            end = Offset(selectionLeft, size.height),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF3B82F6),
            start = Offset(selectionRight, 0F),
            end = Offset(selectionRight, size.height),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )
        val playheadX = size.width * playbackFraction
        drawLine(
            color = Color.White,
            start = Offset(playheadX, 0F),
            end = Offset(playheadX, size.height),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
private fun AudioMarkerPanel(
    markers: List<AudioRecordingMarker>,
    onMarkerClick: (AudioRecordingMarker) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111827))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "标记点", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = "共 ${markers.size} 个", color = Color(0xFF9CA3AF), fontSize = 11.sp)
        }
        markers.forEach { marker ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1F2937))
                    .clickable { onMarkerClick(marker) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AudioIcon(
                    iconRes = R.drawable.fa_bookmark,
                    tint = Color(0xFF3B82F6),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = formatShortDuration(marker.positionMs),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 10.dp)
                )
                Text(
                    text = marker.label,
                    color = Color(0xFF6B7280),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun AudioTrimShortcut(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1F2937))
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AudioIcon(
            iconRes = R.drawable.fa_scissors,
            tint = Color(0xFF9CA3AF),
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            color = Color(0xFFD1D5DB),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun AudioRoundButton(
    iconRes: Int,
    backgroundColor: Color,
    contentDescription: String,
    size: Int,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(backgroundColor.copy(alpha = if (enabled) 1F else 0.4F))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AudioIcon(
            iconRes = iconRes,
            tint = Color.White.copy(alpha = if (enabled) 1F else 0.45F),
            contentDescription = contentDescription,
            modifier = Modifier.size((size * 0.36F).dp)
        )
    }
}

@Composable
private fun AudioActionButton(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor.copy(alpha = if (enabled) 1F else 0.4F))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor.copy(alpha = if (enabled) 1F else 0.55F),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AudioIcon(
    iconRes: Int,
    tint: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint)
    )
}

@Composable
private fun rememberAudioRecordingService(): AudioRecordingService? {
    val context = LocalContext.current
    var service by remember { mutableStateOf<AudioRecordingService?>(null) }
    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as? AudioRecordingService.LocalBinder)?.getService()
                Log.d(TAG, "已连接音频录制服务")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                Log.w(TAG, "音频录制服务连接已断开")
            }
        }
        val bound = runCatching {
            context.bindService(
                Intent(context, AudioRecordingService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
        }.onFailure { throwable ->
            Log.e(TAG, "绑定音频录制服务失败", throwable)
        }.getOrDefault(false)

        onDispose {
            if (bound) {
                runCatching { context.unbindService(connection) }
                    .onFailure { throwable -> Log.w(TAG, "解绑音频录制服务失败", throwable) }
            }
        }
    }
    return service
}

private enum class AudioTrimHandle {
    None,
    Start,
    End
}

private fun minimumTrimFraction(durationMs: Long): Float {
    if (durationMs <= 0L) {
        return 0.01F
    }
    return (MIN_TRIM_DURATION_MS.toFloat() / durationMs)
        .coerceIn(0.005F, 1F)
}

private fun List<Float>.toDisplayWaveform(maxPointCount: Int): List<Float> {
    if (size <= maxPointCount || maxPointCount <= 0) {
        return this
    }
    val chunkSize = (size + maxPointCount - 1) / maxPointCount
    // 每组取峰值可以保留瞬态声音特征，同时限制柱子数量，避免短录音波形互相覆盖成实线。
    return chunked(chunkSize).map { chunk -> chunk.maxOrNull() ?: 0.04F }
}

private fun formatRecordingDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun formatShortDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
