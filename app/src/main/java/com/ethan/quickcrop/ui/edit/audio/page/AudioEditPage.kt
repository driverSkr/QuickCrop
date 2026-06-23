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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.ethan.quickcrop.core.audio.AudioRecordingService
import com.ethan.quickcrop.core.audio.AudioRecordingState
import com.ethan.quickcrop.core.audio.AudioRecordingStatus
import com.ethan.quickcrop.core.audio.WavAudioProcessor
import com.ethan.quickcrop.extension.finishActivity
import com.ethan.quickcrop.ui.edit.audio.view.AudioCompletedContent
import com.ethan.quickcrop.ui.edit.audio.view.AudioIdleContent
import com.ethan.quickcrop.ui.edit.audio.view.AudioRecordingContent
import com.ethan.quickcrop.ui.edit.audio.view.AudioTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "AudioEditPage"

/**
 * 音频编辑页入口，负责连接录音服务、处理权限申请、页面退出确认和最终导出。
 */
@Composable
fun AudioEditPage(onExportCompleted: (Uri) -> Unit) {
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

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0C0C0F)).statusBarsPadding().navigationBarsPadding()) {
        AudioTopBar(
            status = recordingState.status,
            durationMs = recordingState.elapsedMs,
            onBack = { requestLeavePage() }
        )

        // 根据录音状态切换主内容，保持页面级状态与业务状态同步。
        AnimatedContent(
            targetState = recordingState.status,
            modifier = Modifier.weight(1F).fillMaxWidth(),
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

fun formatShortDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}