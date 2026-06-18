package com.ethan.quickcrop.ui.edit.video

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ethan.base.BaseActivity
import com.ethan.quickcrop.MainActivity
import com.ethan.quickcrop.R
import com.ethan.quickcrop.extension.finishActivity
import com.ethan.quickcrop.ui.media.MediaPickActivity
import com.ethan.quickcrop.ui.media.MediaPickType
import com.ethan.quickcrop.ui.theme.QuickCropTheme
import kotlinx.coroutines.delay

private const val TAG = "CropVideoActivity"
private const val DEFAULT_VIDEO_DURATION_MS = 45_000L

class CropVideoActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoUri = getStringExtra(EXTRA_VIDEO_URI)?.let(Uri::parse)
        setContent {
            QuickCropTheme {
                VideoEditorPage(videoUri = videoUri)
            }
        }
    }

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
    }
}

@Composable
private fun VideoEditorPage(videoUri: Uri?) {
    val context = LocalContext.current
    var editorStep by remember { mutableStateOf(VideoEditorStep.Trim) }
    var trimStartFraction by remember { mutableFloatStateOf(0f) }
    var trimEndFraction by remember { mutableFloatStateOf(0.8f) }
    var selectedSpeed by remember { mutableFloatStateOf(2f) }
    var exportFormat by remember { mutableStateOf("MP4") }
    var exportResolution by remember { mutableStateOf("1080p") }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(DEFAULT_VIDEO_DURATION_MS) }

    val player = remember(videoUri) {
        videoUri?.let { uri ->
            ExoPlayer.Builder(context).build().apply {
                // Media3 ExoPlayer 走系统硬件解码，作为编辑预览层；导出阶段复用相同参数交给 Transformer。
                setMediaItem(MediaItem.fromUri(uri))
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
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val resolvedDuration = player.duration
                    if (resolvedDuration > 0) {
                        durationMs = resolvedDuration
                    }
                }

                override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                    isPlaying = isPlayingNow
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }
    }

    LaunchedEffect(player, selectedSpeed) {
        player?.playbackParameters = PlaybackParameters(selectedSpeed)
    }

    LaunchedEffect(player) {
        while (true) {
            playbackPositionMs = player?.currentPosition ?: 0L
            val resolvedDuration = player?.duration ?: 0L
            if (resolvedDuration > 0) {
                durationMs = resolvedDuration
            }
            delay(500)
        }
    }

    fun goBack() {
        when (editorStep) {
            VideoEditorStep.Trim -> context.finishActivity()
            VideoEditorStep.Merge -> editorStep = VideoEditorStep.Trim
            VideoEditorStep.Speed -> editorStep = VideoEditorStep.Merge
            VideoEditorStep.Export -> editorStep = VideoEditorStep.Speed
            VideoEditorStep.Success -> context.finishActivity()
        }
    }

    BackHandler(true) {
        goBack()
    }

    val selectedDurationMs = ((durationMs * (trimEndFraction - trimStartFraction)) / selectedSpeed).toLong().coerceAtLeast(1_000L)
    val exportEstimate = remember(selectedDurationMs, exportResolution) {
        estimateExportSizeMb(selectedDurationMs = selectedDurationMs, resolution = exportResolution)
    }
    val exportPlan = remember(trimStartFraction, trimEndFraction, selectedSpeed, exportFormat, exportResolution, durationMs) {
        VideoExportPlan(
            sourceUri = videoUri,
            trimStartMs = (durationMs * trimStartFraction).toLong(),
            trimEndMs = (durationMs * trimEndFraction).toLong(),
            speed = selectedSpeed,
            format = exportFormat,
            resolution = exportResolution
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0C0C0F)).statusBarsPadding()) {
        if (editorStep != VideoEditorStep.Success) {
            VideoTopBar(
                title = editorStep.title,
                actionText = if (editorStep == VideoEditorStep.Export) "" else "下一步",
                onBack = { goBack() },
                onAction = {
                    editorStep = when (editorStep) {
                        VideoEditorStep.Trim -> VideoEditorStep.Merge
                        VideoEditorStep.Merge -> VideoEditorStep.Speed
                        VideoEditorStep.Speed -> VideoEditorStep.Export
                        else -> editorStep
                    }
                }
            )
            StepIndicator(activeIndex = editorStep.activeStepIndex)
        }

        Box(modifier = Modifier.weight(1f)) {
            when (editorStep) {
                VideoEditorStep.Trim -> TrimContent(
                    player = player,
                    isPlaying = isPlaying,
                    playbackPositionMs = playbackPositionMs,
                    durationMs = durationMs,
                    trimStartFraction = trimStartFraction,
                    trimEndFraction = trimEndFraction,
                    onPlayToggle = {
                        player?.let { currentPlayer ->
                            if (currentPlayer.isPlaying) currentPlayer.pause() else currentPlayer.play()
                        }
                    },
                    onTrimStartChange = { next ->
                        trimStartFraction = next.coerceIn(0f, trimEndFraction - 0.05f)
                    },
                    onTrimEndChange = { next ->
                        trimEndFraction = next.coerceIn(trimStartFraction + 0.05f, 1f)
                    },
                    onShortcut = { shortcut ->
                        when (shortcut) {
                            "删除前段" -> trimStartFraction = (playbackPositionMs.toFloat() / durationMs).coerceIn(0f, trimEndFraction - 0.05f)
                            "删除后段" -> trimEndFraction = (playbackPositionMs.toFloat() / durationMs).coerceIn(trimStartFraction + 0.05f, 1f)
                            "倒放" -> Log.d(TAG, "倒放轨道将在 Transformer 管线中扩展: $exportPlan")
                        }
                    },
                    onNext = { editorStep = VideoEditorStep.Merge }
                )
                VideoEditorStep.Merge -> MergeContent(
                    player = player,
                    selectedDurationMs = selectedDurationMs,
                    onNext = { editorStep = VideoEditorStep.Speed }
                )
                VideoEditorStep.Speed -> SpeedContent(
                    player = player,
                    selectedSpeed = selectedSpeed,
                    selectedDurationMs = selectedDurationMs,
                    onSpeedChange = { selectedSpeed = it },
                    onNext = { editorStep = VideoEditorStep.Export }
                )
                VideoEditorStep.Export -> ExportContent(
                    player = player,
                    selectedDurationMs = selectedDurationMs,
                    exportFormat = exportFormat,
                    exportResolution = exportResolution,
                    estimateSizeMb = exportEstimate,
                    onFormatChange = { exportFormat = it },
                    onResolutionChange = { exportResolution = it },
                    onExport = {
                        // Phase 2 先完成参数收集和 UI 闭环；后端渲染由 Media3 Transformer 按 exportPlan 接入。
                        Log.d(TAG, "准备导出视频: $exportPlan")
                        editorStep = VideoEditorStep.Success
                    }
                )
                VideoEditorStep.Success -> ExportSuccessContent(
                    player = player,
                    selectedDurationMs = selectedDurationMs,
                    exportResolution = exportResolution,
                    estimateSizeMb = exportEstimate,
                    onBackHome = {
                        BaseActivity.navigateTo(
                            context = context,
                            targetActivity = MainActivity::class.java,
                            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                        context.finishActivity()
                    },
                    onContinueEdit = {
                        MediaPickActivity.launch(context, MediaPickType.VIDEO)
                        context.finishActivity()
                    }
                )
            }
        }
    }
}

@Composable
private fun VideoTopBar(title: String, actionText: String, onBack: () -> Unit, onAction: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp)) {
        FaIcon(
            iconRes = R.drawable.fa_arrow_left,
            tint = Color(0xFF9CA3AF),
            modifier = Modifier.align(Alignment.CenterStart).size(18.dp).clickable { onBack() }
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
        if (actionText.isNotEmpty()) {
            Text(
                text = actionText,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2563EB))
                    .clickable { onAction() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun StepIndicator(activeIndex: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = if (index == activeIndex) 24.dp else 8.dp, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (index == activeIndex) Color(0xFF3B82F6) else Color(0xFF3F3F46))
            )
        }
    }
}

@Composable
private fun TrimContent(
    player: ExoPlayer?,
    isPlaying: Boolean,
    playbackPositionMs: Long,
    durationMs: Long,
    trimStartFraction: Float,
    trimEndFraction: Float,
    onPlayToggle: () -> Unit,
    onTrimStartChange: (Float) -> Unit,
    onTrimEndChange: (Float) -> Unit,
    onShortcut: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        VideoPreview(
            player = player,
            modifier = Modifier.weight(1f),
            overlay = {
                PlayOverlay(isPlaying = isPlaying, onClick = onPlayToggle)
                ProgressOverlay(positionMs = playbackPositionMs, durationMs = durationMs)
            }
        )
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(text = "拖动左右手柄设置起止点", color = Color(0xFF9CA3AF), fontSize = 12.sp)
            TimelineTrimControl(
                durationMs = durationMs,
                playbackPositionMs = playbackPositionMs,
                startFraction = trimStartFraction,
                endFraction = trimEndFraction,
                onStartChange = onTrimStartChange,
                onEndChange = onTrimEndChange
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("删除前段", "删除后段", "倒放").forEach { label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1F2937))
                            .clickable { onShortcut(label) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton(text = "下一步：拼接 →", onClick = onNext)
        }
    }
}

@Composable
private fun MergeContent(player: ExoPlayer?, selectedDurationMs: Long, onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        VideoPreview(
            player = player,
            modifier = Modifier.weight(1f),
            overlay = {
                Text(
                    text = "已选 1 段 · ${formatDuration(selectedDurationMs)}",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        )
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(text = "拖拽排序 · 点击 + 添加片段", color = Color(0xFF9CA3AF), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(10.dp))
            TrackItem(title = "视频_001.mp4", subtitle = "00:00 – ${formatDuration(selectedDurationMs)} · 1080p", trailingIcon = R.drawable.fa_bars)
            Spacer(modifier = Modifier.height(8.dp))
            AddTrackItem()
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton(text = "下一步：速度 →", onClick = onNext)
        }
    }
}

@Composable
private fun SpeedContent(
    player: ExoPlayer?,
    selectedSpeed: Float,
    selectedDurationMs: Long,
    onSpeedChange: (Float) -> Unit,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        VideoPreview(
            player = player,
            modifier = Modifier.weight(1f),
            overlay = {
                Text(
                    text = "${selectedSpeed.formatSpeed()} 加速",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2563EB)).padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        )
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)) {
            Text(
                text = "当前速度：${selectedSpeed.formatSpeed()}　时长：${formatDuration(selectedDurationMs)}",
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 0.75f, 2f, 4f).forEach { speed ->
                    SpeedCard(speed = speed, selected = speed == selectedSpeed, onClick = { onSpeedChange(speed) }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            SpeedCurveCard()
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton(text = "下一步：导出 →", onClick = onNext)
        }
    }
}

@Composable
private fun ExportContent(
    player: ExoPlayer?,
    selectedDurationMs: Long,
    exportFormat: String,
    exportResolution: String,
    estimateSizeMb: Int,
    onFormatChange: (String) -> Unit,
    onResolutionChange: (String) -> Unit,
    onExport: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        VideoPreview(
            player = player,
            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)),
            overlay = {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatDuration(selectedDurationMs), color = Color.White, fontSize = 10.sp)
                    Text(text = exportResolutionLabel(exportResolution), color = Color.White, fontSize = 10.sp)
                    Text(text = exportFormat, color = Color.White, fontSize = 10.sp)
                }
            }
        )
        Spacer(modifier = Modifier.height(14.dp))
        OptionGroup(title = "输出格式", options = listOf("MP4", "MOV"), selected = exportFormat, recommended = "MP4", onSelect = onFormatChange)
        Spacer(modifier = Modifier.height(12.dp))
        OptionGroup(title = "分辨率", options = listOf("1080p", "4K", "720p"), selected = exportResolution, recommended = "1080p", onSelect = onResolutionChange)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF111827)).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "预估大小", color = Color(0xFF9CA3AF), fontSize = 12.sp)
            Text(text = "约 $estimateSizeMb MB", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.weight(1f))
        BlueButton(text = "开始导出", iconRes = R.drawable.fa_download, onClick = onExport)
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun ExportSuccessContent(
    player: ExoPlayer?,
    selectedDurationMs: Long,
    exportResolution: String,
    estimateSizeMb: Int,
    onBackHome: () -> Unit,
    onContinueEdit: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color(0x3316A34A)), contentAlignment = Alignment.Center) {
            FaIcon(iconRes = R.drawable.fa_check, tint = Color(0xFF4ADE80), modifier = Modifier.size(38.dp))
        }
        Text(text = "导出完成！", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        Text(text = "视频已保存到相册", color = Color(0xFF9CA3AF), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
        VideoPreview(
            player = player,
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, Color(0xFF374151), RoundedCornerShape(16.dp)),
            overlay = {}
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetaItem(title = "时长", value = formatDuration(selectedDurationMs))
            MetaDivider()
            MetaItem(title = "分辨率", value = exportResolution)
            MetaDivider()
            MetaItem(title = "大小", value = "$estimateSizeMb MB")
        }
        Spacer(modifier = Modifier.weight(1f))
        PrimaryButton(text = "返回首页", onClick = onBackHome)
        Spacer(modifier = Modifier.height(12.dp))
        DarkButton(text = "继续编辑", onClick = onContinueEdit)
    }
}

@Composable
private fun VideoPreview(player: ExoPlayer?, modifier: Modifier = Modifier, overlay: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (player != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                    }
                },
                update = { view -> view.player = player }
            )
        } else {
            Text(text = "未选择视频", color = Color(0xFF9CA3AF), fontSize = 14.sp)
        }
        overlay()
    }
}

@Composable
private fun PlayOverlay(isPlaying: Boolean, onClick: () -> Unit) {
    if (!isPlaying) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            FaIcon(iconRes = R.drawable.fa_play, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    } else {
        Box(modifier = Modifier.fillMaxSize().clickable { onClick() })
    }
}

@Composable
private fun ProgressOverlay(positionMs: Long, durationMs: Long) {
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0xFF374151))) {
            Box(modifier = Modifier.fillMaxWidth(progress).height(3.dp).background(Color(0xFF3B82F6)))
        }
        Text(
            text = "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TimelineTrimControl(
    durationMs: Long,
    playbackPositionMs: Long,
    startFraction: Float,
    endFraction: Float,
    onStartChange: (Float) -> Unit,
    onEndChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Waveform(modifier = Modifier.fillMaxWidth().height(34.dp))
        Box(modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF111827))) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 根据真实宽度绘制裁剪选区，避免不同屏幕下出现固定 dp 偏移。
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF60A5FA))),
                    topLeft = Offset(size.width * startFraction, 0f),
                    size = Size(size.width * (endFraction - startFraction), size.height),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
                val playheadX = size.width * (playbackPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                drawLine(Color.White, Offset(playheadX, 0f), Offset(playheadX, size.height), strokeWidth = 3f)
                drawLine(Color.White, Offset(size.width * startFraction, 0f), Offset(size.width * startFraction, size.height), strokeWidth = 12f, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(size.width * endFraction, 0f), Offset(size.width * endFraction, size.height), strokeWidth = 12f, cap = StrokeCap.Round)
            }
        }
        Slider(value = startFraction, onValueChange = onStartChange, valueRange = 0f..1f, modifier = Modifier.height(28.dp))
        Slider(value = endFraction, onValueChange = onEndChange, valueRange = 0f..1f, modifier = Modifier.height(28.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = formatDuration((durationMs * startFraction).toLong()), color = Color(0xFF6B7280), fontSize = 10.sp)
            Text(text = formatDuration((durationMs * endFraction).toLong()), color = Color(0xFF6B7280), fontSize = 10.sp)
        }
    }
}

@Composable
private fun Waveform(modifier: Modifier = Modifier) {
    val bars = listOf(0.35f, 0.6f, 0.85f, 0.55f, 0.9f, 0.72f, 0.48f, 0.78f, 0.66f, 0.42f, 0.84f, 0.55f, 0.72f, 0.9f, 0.48f)
    Canvas(modifier = modifier) {
        val gap = size.width / (bars.size * 2f)
        bars.forEachIndexed { index, scale ->
            val x = gap + index * gap * 2f
            val barHeight = size.height * scale
            drawLine(
                color = Color(0xFF3B82F6),
                start = Offset(x, (size.height - barHeight) / 2f),
                end = Offset(x, (size.height + barHeight) / 2f),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun TrackItem(title: String, subtitle: String, trailingIcon: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1F2937)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF334155)))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(text = title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = Color(0xFF9CA3AF), fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
        FaIcon(iconRes = trailingIcon, tint = Color(0xFF6B7280), modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun AddTrackItem() {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1F2937)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x553B82F6)), contentAlignment = Alignment.Center) {
            FaIcon(iconRes = R.drawable.fa_plus, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Text(text = "点击添加第二段视频", color = Color(0xFF9CA3AF), fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SpeedCard(speed: Float, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = when (speed) {
        0.5f -> "慢速"
        0.75f -> "较慢"
        2f -> "快速"
        else -> "极快"
    }
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) Color(0xFF2563EB) else Color(0xFF1F2937)).clickable { onClick() }.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = speed.formatSpeed(), color = if (selected) Color.White else Color(0xFF9CA3AF), fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Text(text = label, color = if (selected) Color(0xFFBFDBFE) else Color(0xFF6B7280), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SpeedCurveCard() {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF111827)).padding(12.dp)) {
        Text(text = "速度曲线", color = Color(0xFF6B7280), fontSize = 10.sp)
        Canvas(modifier = Modifier.fillMaxWidth().height(80.dp).padding(top = 8.dp)) {
            drawLine(
                color = Color(0xFF3B82F6),
                start = Offset(0f, size.height - 6f),
                end = Offset(size.width, 6f),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
            drawCircle(color = Color(0xFF3B82F6), radius = 8f, center = Offset(size.width, 6f), style = Stroke(width = 2f))
        }
    }
}

@Composable
private fun OptionGroup(title: String, options: List<String>, selected: String, recommended: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF111827)).padding(14.dp)) {
        Text(text = title, color = Color(0xFF9CA3AF), fontSize = 12.sp)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (selected == option) Color(0xFF2563EB) else Color(0xFF1F2937)).clickable { onSelect(option) }.padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = option, color = if (selected == option) Color.White else Color(0xFF9CA3AF), fontSize = 12.sp, fontWeight = if (selected == option) FontWeight.Bold else FontWeight.Normal)
                    if (option == recommended) {
                        Text(text = if (title == "输出格式") "推荐" else exportResolutionLabel(option), color = Color(0xFFBFDBFE), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaItem(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, color = Color(0xFF9CA3AF), fontSize = 12.sp)
        Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MetaDivider() {
    Box(modifier = Modifier.width(1.dp).height(34.dp).background(Color(0xFF374151)))
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BlueButton(text: String, iconRes: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF2563EB)).clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FaIcon(iconRes = iconRes, tint = Color.White, modifier = Modifier.size(15.dp))
        Text(text = text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun DarkButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1F2937)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FaIcon(iconRes: Int, tint: Color, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint)
    )
}

private enum class VideoEditorStep(val title: String, val activeStepIndex: Int) {
    Trim("视频剪辑", 0),
    Merge("视频拼接", 1),
    Speed("速度调整", 2),
    Export("导出设置", 3),
    Success("导出完成", 4)
}

private data class VideoExportPlan(
    val sourceUri: Uri?,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val speed: Float,
    val format: String,
    val resolution: String
)

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun Float.formatSpeed(): String {
    return "%.1fx".format(this)
}

private fun exportResolutionLabel(resolution: String): String {
    return when (resolution) {
        "4K" -> "3840×2160"
        "720p" -> "1280×720"
        else -> "1920×1080"
    }
}

private fun estimateExportSizeMb(selectedDurationMs: Long, resolution: String): Int {
    val bitRateMbps = when (resolution) {
        "4K" -> 35f
        "720p" -> 5f
        else -> 12f
    }
    val seconds = selectedDurationMs / 1000f
    return ((bitRateMbps * seconds) / 8f).toInt().coerceAtLeast(1)
}
