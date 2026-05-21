package com.ethan.quickcrop.feature.video

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import com.ethan.quickcrop.core.media.VideoExportRepository
import com.ethan.quickcrop.core.media.VideoPreviewRepository
import com.ethan.quickcrop.core.model.TrimRange
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.max

private const val TAG = "VideoEditorScreen"

private enum class VideoPanel(
    val title: String
) {
    Video("视频"),
    Cut("剪裁"),
    Preset("预设")
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(
    sourceUri: Uri?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var previewMetadata by remember { mutableStateOf<VideoPreviewRepository.PreviewMetadata?>(null) }
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(0L) }
    var playbackPositionMs by remember { mutableLongStateOf(0L) }
    var isExporting by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("请先选择一个视频素材。") }
    var selectedPanel by remember { mutableStateOf(VideoPanel.Video) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
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

    LaunchedEffect(sourceUri) {
        val currentUri = sourceUri
        if (currentUri == null) {
            previewMetadata = null
            trimStartMs = 0L
            trimEndMs = 0L
            playbackPositionMs = 0L
            statusMessage = "没有可编辑的视频。"
            return@LaunchedEffect
        }

        try {
            // 这里仍然复用已有的视频预览与导出能力，只把入口改成了首页选择。
            previewMetadata = VideoPreviewRepository.loadPreviewFrames(context, currentUri)
            val safeDuration = previewMetadata?.durationMs ?: 0L
            trimStartMs = 0L
            trimEndMs = safeDuration.takeIf { it > 0L } ?: 0L
            playbackPositionMs = 0L
            player.setMediaItem(MediaItem.fromUri(currentUri))
            player.prepare()
            player.seekTo(0L)
            player.playWhenReady = false
            statusMessage = if (safeDuration > 0L) {
                "视频已加载，可以拖动时间轴裁剪片段。"
            } else {
                "视频已加载，但暂时无法读取时长。"
            }
            Log.i(TAG, "视频素材加载完成：$currentUri")
        } catch (throwable: Throwable) {
            Log.e(TAG, "加载视频失败", throwable)
            previewMetadata = null
            statusMessage = "视频加载失败：${throwable.message ?: "未知错误"}"
        }
    }

    LaunchedEffect(sourceUri, isPlaying) {
        val currentUri = sourceUri ?: return@LaunchedEffect
        if (!isPlaying) {
            playbackPositionMs = player.currentPosition
            return@LaunchedEffect
        }

        while (sourceUri == currentUri && isPlaying) {
            playbackPositionMs = player.currentPosition
            delay(120L)
        }
    }

    LaunchedEffect(isExporting, sourceUri, trimStartMs, trimEndMs) {
        val currentUri = sourceUri ?: return@LaunchedEffect
        if (!isExporting) {
            return@LaunchedEffect
        }

        try {
            val output = VideoExportRepository.exportTrimmedVideo(
                context = context,
                sourceUri = currentUri,
                trimRange = TrimRange(trimStartMs, trimEndMs)
            )
            statusMessage = "视频已保存到相册：${output.lastPathSegment ?: output}"
            Log.i(TAG, "视频导出完成：$output")
        } catch (throwable: Throwable) {
            Log.e(TAG, "导出视频失败", throwable)
            statusMessage = "视频导出失败：${throwable.message ?: "未知错误"}"
        } finally {
            isExporting = false
        }
    }

    val durationMs = previewMetadata?.durationMs ?: 0L
    val canExport = sourceUri != null && trimEndMs > trimStartMs && !isExporting

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val previewHeight = (maxHeight * 0.50f).coerceIn(280.dp, 460.dp)
        val timelineHeight = (maxHeight * 0.24f).coerceIn(170.dp, 260.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeaderBar(
                title = "视频编辑",
                subtitle = statusMessage,
                onBack = onBack
            )

            if (sourceUri == null) {
                EmptyVideoState(onBack = onBack)
            } else {
                Card(
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight)
                            .background(Color.Black)
                            .clickable {
                                if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    player.playWhenReady = true
                                    player.play()
                                }
                            }
                    ) {
                        val aspectRatio = previewMetadata?.displayAspectRatio ?: (9f / 16f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(previewHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayerSurface(
                                player = player,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Surface(
                            onClick = {
                                statusMessage = "已添加片段按钮被点击，后续可接入片段库。"
                            },
                            color = Color(0xCC2A2A2A),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "+ 添加片段",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }

                        Surface(
                            color = Color(0x99000000),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Text(
                                text = if (isPlaying) "暂停" else "播放",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        Surface(
                            color = Color(0x99000000),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 14.dp)
                        ) {
                            Text(
                                text = "${formatTime(playbackPositionMs)} / ${formatTime(max(durationMs, 0L))}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }

                        if (isPlaying) {
                            Text(
                                text = "正在预览当前视频片段",
                                color = Color(0xFFA2A2A2),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(14.dp)
                            )
                        } else {
                            Text(
                                text = "点击画面即可播放或暂停",
                                color = Color(0xFFA2A2A2),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(14.dp)
                            )
                        }

                        Text(
                            text = if (aspectRatio > 1f) "横屏视频" else "竖屏视频",
                            color = Color(0xFFB9B9B9),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(14.dp)
                        )
                    }
                }

                ActionStrip(
                    actions = listOf(
                        "稳定" to {
                            statusMessage = "稳定功能已收到，后续可接入防抖处理。"
                        },
                        "美化" to {
                            statusMessage = "美化功能已收到，后续可接入滤镜或调色。"
                        },
                        "导出视频帧" to {
                            statusMessage = "导出视频帧功能先保留入口，后续再实现批量帧导出。"
                        },
                        "自动剪辑" to {
                            if (durationMs > 0L) {
                                trimStartMs = 0L
                                trimEndMs = minOf(durationMs, max(3000L, durationMs / 3L))
                                statusMessage = "已自动选择一段建议区间。"
                            } else {
                                statusMessage = "当前视频没有有效时长，无法自动剪辑。"
                            }
                        }
                    )
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "裁剪时间轴",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "拖动左右手柄确定导出片段，时间轴逻辑沿用现有实现。",
                            color = Color(0xFFB8B8B8),
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (previewMetadata != null && durationMs > 0L) {
                            VideoTrimTimeline(
                                durationMs = durationMs,
                                trimStartMs = trimStartMs,
                                trimEndMs = trimEndMs,
                                playbackPositionMs = playbackPositionMs,
                                thumbnails = previewMetadata?.frames.orEmpty(),
                                onRangeChange = { newStart, newEnd ->
                                    trimStartMs = newStart
                                    trimEndMs = newEnd
                                    statusMessage = "已更新裁剪区间：${formatTime(newStart)} - ${formatTime(newEnd)}"
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(timelineHeight)
                            )
                        } else {
                            Text(
                                text = "未能读取视频时长，无法显示时间轴。",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                PanelTabs(
                    selectedPanel = selectedPanel,
                    onPanelSelected = { selectedPanel = it }
                )

                when (selectedPanel) {
                    VideoPanel.Video -> VideoModeCard(
                        title = "视频模式",
                        description = "这里保留视频播放、添加片段和导出的主要入口。"
                    )

                    VideoPanel.Cut -> VideoModeCard(
                        title = "剪裁模式",
                        description = "当前支持的核心能力是拖动时间轴选择片段。"
                    )

                    VideoPanel.Preset -> VideoModeCard(
                        title = "预设模式",
                        description = "预设页先保留空位，后续可以再接滤镜、转场或模板。"
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    Button(
                        enabled = canExport,
                        onClick = {
                            isExporting = true
                            statusMessage = "正在保存副本，请稍候..."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存副本")
                    }
                }
            }

            if (isExporting) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            text = "视频导出处理中",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) {
                Text("返回相册")
            }
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.size(1.dp))
        }

        Text(
            text = subtitle,
            color = Color(0xFFB8B8B8),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun EmptyVideoState(
    onBack: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "还没有进入视频编辑",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "从相册页点击一个视频后，这里就会加载播放器、时间轴和保存副本入口。",
                color = Color(0xFFB8B8B8),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onBack) {
                Text("返回相册")
            }
        }
    }
}

@Composable
private fun ActionStrip(
    actions: List<Pair<String, () -> Unit>>
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        actions.forEach { (title, onClick) ->
            Surface(
                onClick = onClick,
                color = Color(0xFF2A1C18),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF4A2F28))
                    )
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelTabs(
    selectedPanel: VideoPanel,
    onPanelSelected: (VideoPanel) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        VideoPanel.entries.forEach { panel ->
            Surface(
                onClick = { onPanelSelected(panel) },
                color = if (selectedPanel == panel) Color(0xFF7A5E52) else Color(0xFF1D1D1D),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = panel.title,
                    color = if (selectedPanel == panel) Color.White else Color(0xFFB9B9B9),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun VideoModeCard(
    title: String,
    description: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(description, color = Color(0xFFB8B8B8), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatTime(positionMs: Long): String {
    val safePosition = positionMs.coerceAtLeast(0L)
    val totalSeconds = safePosition / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.CHINA, "%02d:%02d", minutes, seconds)
}
