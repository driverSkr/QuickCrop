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

/**
 * 音频编辑页入口，负责连接录音服务、处理权限申请、页面退出确认和最终导出。
 */
@Composable
fun AudioEditPage(
    onExportCompleted: (Uri) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val audioService = rememberAudioRecordingService()
    // 服务尚未绑定完成时使用空状态兜底，避免页面首次组合时状态流为空。
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

    // 统一的导出入口：校验源文件后在 IO 线程裁剪并写入系统 Music 目录。
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

    // 同一个权限启动器同时处理录音权限和 Android 9 及以下的写存储权限。
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
        // 已授权时直接启动服务，未授权则记录待执行动作，等待权限回调继续。
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
        // Android 10 起通过 MediaStore 写入音乐目录，旧系统才需要额外申请写存储权限。
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

    // 有录音内容或录音正在进行时，返回前需要二次确认，避免误删临时文件。
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

    // 系统返回键与顶部返回按钮走同一套退出确认逻辑。
    BackHandler(true) {
        requestLeavePage()
    }

    // 服务层错误以 Toast 暴露给用户，避免错误状态只停留在日志里。
    LaunchedEffect(recordingState.errorMessage) {
        recordingState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // 新录音文件生成后重置裁剪范围，避免沿用上一段音频的裁剪位置。
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

        // 根据录音状态切换主内容，保持页面级状态与业务状态同步。
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
        // 麦克风权限被拒绝后引导用户进入系统设置手动开启。
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
        // 退出确认会清理录音缓存，避免用户以为临时录音仍可恢复。
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

/**
 * 顶部导航栏，展示返回按钮、当前录音状态标题和录音时长。
 */
@Composable
private fun AudioTopBar(
    status: AudioRecordingStatus,
    durationMs: Long,
    onBack: () -> Unit
) {
    // 完成态用绿色时长强调录音已经可进入导出流程。
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

/**
 * 空闲或错误状态内容，负责提示当前录音准备状态并提供开始录音入口。
 */
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

/**
 * 录音中内容，展示实时波形、暂停/继续、停止和添加标记等控制。
 */
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
            // 标记数量即时反馈，帮助用户确认点击添加标记已经生效。
            Text(
                text = "已添加 ${state.markers.size} 个标记",
                color = Color(0xFF3B82F6),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * 录音状态指示器，通过颜色和脉冲动画表达空闲、录制中和暂停状态。
 */
@Composable
private fun RecordingStatusIndicator(status: AudioRecordingStatus) {
    val isRecording = status == AudioRecordingStatus.Recording
    val isPaused = status == AudioRecordingStatus.Paused
    // 中心圆颜色与业务状态绑定，用户可快速辨认当前是否正在录制。
    val centerColor = when {
        isRecording -> Color(0xFFEF4444)
        isPaused -> Color(0xFFF59E0B)
        else -> Color(0xFF4B5563)
    }
    // 仅录制中展示外圈脉冲，用无限动画驱动半透明圆扩散。
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

/**
 * 实时录音波形面板，绘制当前采样振幅并提示峰值是否过载。
 */
@Composable
private fun LiveWaveformPanel(
    waveform: List<Float>,
    peakDb: Float,
    isRecording: Boolean
) {
    // 接近 0dB 时容易削波，使用红色状态提醒用户降低输入音量。
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
            // 还没有采样数据时使用低幅度占位，避免面板空白。
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

/**
 * 录音完成后的编辑内容，负责播放预览、裁剪范围调整、标记跳转和导出入口。
 */
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
    // 源文件变化时重建 ExoPlayer，确保播放的是当前录音文件。
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
            // 监听播放器状态，用于同步播放按钮和处理播放结束后的回到裁剪起点。
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
                // 页面离开或音频源切换时释放播放器，避免后台继续占用音频资源。
                player.removeListener(listener)
                player.release()
            }
        }
    }

    LaunchedEffect(player, trimStartFraction, trimEndFraction, playerDurationMs) {
        while (true) {
            // 定时同步播放进度，并在播放越过裁剪终点时自动回到起点。
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
        // 外部传入的是 0-1 的相对位置，这里统一换算为播放器毫秒位置。
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
                    // 播放只在裁剪区间内进行，当前位置在区间外时先跳回裁剪起点。
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
                // 起点不能越过终点，并至少保留一段可导出的最小时长。
                val safeValue = next.coerceIn(0F, trimEndFraction - minGap)
                onTrimStartChanged(safeValue)
                seekToFraction(safeValue)
            },
            onEndChanged = { next ->
                val minGap = minimumTrimFraction(playerDurationMs)
                // 终点不能早于起点，约束后立即跳转方便用户听到边界位置。
                val safeValue = next.coerceIn(trimStartFraction + minGap, 1F)
                onTrimEndChanged(safeValue)
                seekToFraction(safeValue)
            },
            onDeleteBefore = {
                // “删除前段”将裁剪起点移动到当前播放头位置。
                val playheadFraction = (playbackPositionMs.toFloat() / playerDurationMs).coerceIn(0F, 1F)
                val minGap = minimumTrimFraction(playerDurationMs)
                val safeValue = playheadFraction.coerceIn(trimStartFraction, trimEndFraction - minGap)
                onTrimStartChanged(safeValue)
                seekToFraction(safeValue)
            },
            onDeleteAfter = {
                // “删除后段”将裁剪终点移动到当前播放头位置。
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
                    // 点击标记点直接跳到记录位置，便于快速定位重要片段。
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

/**
 * 播放预览面板，展示文件信息、播放/暂停按钮、进度条和当前播放时间。
 */
@Composable
private fun AudioPreviewPanel(
    fileName: String,
    durationMs: Long,
    playbackPositionMs: Long,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onSeek: (Float) -> Unit
) {
    // 将毫秒进度转换成 Slider 需要的 0-1 进度值。
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

/**
 * 裁剪控制面板，组合波形拖拽区域、时间信息和快捷删除前/后段按钮。
 */
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
            // 播放头位置使用相对比例传入波形画布，避免画布直接依赖毫秒单位。
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
            // 三段时间分别展示裁剪起点、已选时长和裁剪终点，方便精确确认。
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

/**
 * 可拖拽的裁剪波形，负责绘制采样柱、选区遮罩、左右手柄和播放头。
 */
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
    // 拖拽回调发生在 pointerInput 协程中，使用最新状态避免闭包拿到旧值。
    val latestStartFraction by rememberUpdatedState(startFraction)
    val latestEndFraction by rememberUpdatedState(endFraction)
    val latestOnStartChanged by rememberUpdatedState(onStartChanged)
    val latestOnEndChanged by rememberUpdatedState(onEndChanged)
    // 波形点数过多时会压缩为固定数量，保证小屏幕上仍能看清每根柱子。
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
                        // 根据按下位置离哪个边界更近来决定拖动起点还是终点手柄。
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
                        // 拖拽位置转换为 0-1 的比例后交给上层统一做最小时长约束。
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
        // 先绘制整段波形，选区内外使用不同颜色帮助识别保留片段。
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
        // 选区底色覆盖在波形上方，突出最终会导出的音频范围。
        drawRoundRect(
            color = Color(0xFF3B82F6).copy(alpha = 0.16F),
            topLeft = Offset(selectionLeft, 0F),
            size = Size((selectionRight - selectionLeft).coerceAtLeast(0F), size.height),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        // 左右粗线既是视觉边界，也是用户拖拽的裁剪手柄。
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
        // 白色播放头显示当前预览位置，不参与裁剪范围计算。
        drawLine(
            color = Color.White,
            start = Offset(playheadX, 0F),
            end = Offset(playheadX, size.height),
            strokeWidth = 2.dp.toPx()
        )
    }
}

/**
 * 标记点列表面板，展示录音期间添加的标记并支持点击跳转。
 */
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

/**
 * 裁剪快捷操作按钮，用于一键删除播放头之前或之后的片段。
 */
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

/**
 * 圆形图标按钮，统一录音、暂停、停止、播放等主要操作的样式。
 */
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

/**
 * 底部文本操作按钮，承载重新录音和导出这类宽按钮操作。
 */
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

/**
 * 图标渲染组件，统一加载矢量资源并套用指定颜色。
 */
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

/**
 * 绑定并记住音频录制服务实例，页面退出时自动解绑服务连接。
 */
@Composable
private fun rememberAudioRecordingService(): AudioRecordingService? {
    val context = LocalContext.current
    var service by remember { mutableStateOf<AudioRecordingService?>(null) }
    DisposableEffect(context) {
        // 通过本地 Binder 获取服务对象，让 Compose 页面可以直接订阅服务状态流。
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
                // 只在绑定成功后解绑，避免 bindService 失败时再次触发系统异常。
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
