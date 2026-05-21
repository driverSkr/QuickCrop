package com.ethan.quickcrop.feature.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private enum class DragMode {
    None,
    StartHandle,
    EndHandle,
    Selection
}

@Composable
fun VideoTrimTimeline(
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    playbackPositionMs: Long,
    thumbnails: List<com.ethan.quickcrop.core.model.ThumbnailFrame>,
    onRangeChange: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var timelineSize by remember { mutableStateOf(IntSize.Zero) }
    var dragMode by remember { mutableStateOf(DragMode.None) }
    val handleTouchRadius = with(density) { 22.dp.toPx() }
    val minRangeMs = max(500L, durationMs / 30L)
    val timelineBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    val trackBackground = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val selectedRangeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val handleColor = MaterialTheme.colorScheme.primary
    val playheadColor = MaterialTheme.colorScheme.tertiary

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "视频帧轨道",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "向左或向右拖动手柄调整裁剪区间，水平手势只作用在轨道上，不会和页面纵向滚动抢手势。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = timelineBackground
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .onSizeChanged { timelineSize = it }
                        .pointerInput(durationMs, trimStartMs, trimEndMs, timelineSize) {
                            // 仅响应水平拖拽，减少与外层纵向滚动、列表手势的冲突。
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    dragMode = detectDragMode(
                                        offset = offset.x,
                                        size = timelineSize,
                                        durationMs = durationMs,
                                        startMs = trimStartMs,
                                        endMs = trimEndMs,
                                        handleTouchRadius = handleTouchRadius
                                    )
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    if (dragMode == DragMode.None || timelineSize.width == 0) {
                                        return@detectHorizontalDragGestures
                                    }
                                    change.consumeAllChanges()
                                    val deltaMs = xToTimeDelta(
                                        dragAmountX = dragAmount,
                                        size = timelineSize,
                                        durationMs = durationMs
                                    )
                                    val newRange = when (dragMode) {
                                        DragMode.StartHandle -> {
                                            val start = (trimStartMs + deltaMs).coerceIn(0L, trimEndMs - minRangeMs)
                                            start to trimEndMs
                                        }
                                        DragMode.EndHandle -> {
                                            val end = (trimEndMs + deltaMs).coerceIn(trimStartMs + minRangeMs, durationMs)
                                            trimStartMs to end
                                        }
                                        DragMode.Selection -> {
                                            val rangeDuration = trimEndMs - trimStartMs
                                            val candidateStart = trimStartMs + deltaMs
                                            val clampedStart = candidateStart.coerceIn(0L, durationMs - rangeDuration)
                                            clampedStart to (clampedStart + rangeDuration)
                                        }
                                        DragMode.None -> trimStartMs to trimEndMs
                                    }
                                    onRangeChange(newRange.first, newRange.second)
                                },
                                onDragEnd = {
                                    dragMode = DragMode.None
                                },
                                onDragCancel = {
                                    dragMode = DragMode.None
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(112.dp)) {
                        val width = size.width
                        val height = size.height
                        if (width <= 0f || durationMs <= 0L) {
                            return@Canvas
                        }

                        val trackTop = height * 0.34f
                        val trackHeight = height * 0.32f
                        val trackRadius = trackHeight / 2f
                        val startX = timeToX(trimStartMs, width, durationMs)
                        val endX = timeToX(trimEndMs, width, durationMs)
                        val playheadX = timeToX(playbackPositionMs.coerceIn(0L, durationMs), width, durationMs)

                        drawRoundRect(
                            color = trackBackground,
                            topLeft = Offset(0f, trackTop),
                            size = androidx.compose.ui.geometry.Size(width, trackHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackRadius, trackRadius)
                        )

                        drawRoundRect(
                            color = selectedRangeColor,
                            topLeft = Offset(startX, trackTop),
                            size = androidx.compose.ui.geometry.Size(endX - startX, trackHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackRadius, trackRadius)
                        )

                        drawLine(
                            color = handleColor,
                            start = Offset(startX, trackTop - 8.dp.toPx()),
                            end = Offset(startX, trackTop + trackHeight + 8.dp.toPx()),
                            strokeWidth = 4.dp.toPx()
                        )
                        drawLine(
                            color = handleColor,
                            start = Offset(endX, trackTop - 8.dp.toPx()),
                            end = Offset(endX, trackTop + trackHeight + 8.dp.toPx()),
                            strokeWidth = 4.dp.toPx()
                        )

                        drawCircle(
                            color = handleColor,
                            radius = 11.dp.toPx(),
                            center = Offset(startX, height / 2f)
                        )
                        drawCircle(
                            color = handleColor,
                            radius = 11.dp.toPx(),
                            center = Offset(endX, height / 2f)
                        )

                        if (playbackPositionMs in 0L..durationMs) {
                            drawLine(
                                color = playheadColor,
                                start = Offset(playheadX, 6.dp.toPx()),
                                end = Offset(playheadX, height - 6.dp.toPx()),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawCircle(
                                color = playheadColor,
                                radius = 7.dp.toPx(),
                                center = Offset(playheadX, 10.dp.toPx())
                            )
                        }
                    }

                    Text(
                        text = if (durationMs > 0L) {
                            "播放指示：${playbackPositionMs.coerceIn(0L, durationMs)}ms"
                        } else {
                            "播放指示：0ms"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 12.dp)
                    )
                }
            }
        }

        Text(
            text = "拖动左右手柄调整裁剪区间，选中区域会在时间轴上高亮。"
        )

        Text(
            text = "缩略图轨道",
            style = MaterialTheme.typography.titleSmall
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(thumbnails) { frame ->
                Card(
                    modifier = Modifier.size(width = 96.dp, height = 72.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box {
                        Image(
                            bitmap = frame.bitmap,
                            contentDescription = "视频缩略图 ${frame.timeMs}ms",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${frame.timeMs / 1000}s",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        AssistChip(
            onClick = {},
            label = {
                Text(
                    text = "当前缩略图数量：${thumbnails.size}，范围长度：${trimEndMs - trimStartMs}ms"
                )
            }
        )
    }
}

private fun detectDragMode(
    offset: Float,
    size: IntSize,
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    handleTouchRadius: Float
): DragMode {
    if (size.width <= 0 || durationMs <= 0L) {
        return DragMode.None
    }
    val startX = timeToX(startMs, size.width.toFloat(), durationMs)
    val endX = timeToX(endMs, size.width.toFloat(), durationMs)
    return when {
        abs(offset - startX) <= handleTouchRadius -> DragMode.StartHandle
        abs(offset - endX) <= handleTouchRadius -> DragMode.EndHandle
        offset in startX..endX -> DragMode.Selection
        else -> DragMode.None
    }
}

private fun xToTimeDelta(
    dragAmountX: Float,
    size: IntSize,
    durationMs: Long
): Long {
    if (size.width <= 0 || durationMs <= 0L) {
        return 0L
    }
    return (dragAmountX / size.width.toFloat() * durationMs).toLong()
}

private fun timeToX(
    timeMs: Long,
    widthPx: Float,
    durationMs: Long
): Float {
    if (durationMs <= 0L) {
        return 0f
    }
    return widthPx * timeMs / durationMs.toFloat()
}
