package com.ethan.quickcrop.ui.edit.audio.view

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ethan.quickcrop.R
import com.ethan.quickcrop.core.audio.AudioRecordingMarker
import com.ethan.quickcrop.core.audio.AudioRecordingState
import com.ethan.quickcrop.ui.edit.audio.page.formatShortDuration
import kotlinx.coroutines.delay
import java.io.File
import kotlin.collections.forEach

private const val MIN_TRIM_DURATION_MS = 500L

/**
 * 录音完成后的编辑内容，负责播放预览、裁剪范围调整、标记跳转和导出入口。
 */
@Composable
fun AudioCompletedContent(
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF22C55E)),
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
            Column(modifier = Modifier.weight(1F).padding(horizontal = 12.dp)) {
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
            modifier = Modifier.fillMaxWidth().height(30.dp)
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
            modifier = Modifier.fillMaxWidth().height(88.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
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

fun minimumTrimFraction(durationMs: Long): Float {
    if (durationMs <= 0L) {
        return 0.01F
    }
    return (MIN_TRIM_DURATION_MS.toFloat() / durationMs).coerceIn(0.005F, 1F)
}