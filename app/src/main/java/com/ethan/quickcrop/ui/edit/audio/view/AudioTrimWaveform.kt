package com.ethan.quickcrop.ui.edit.audio.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

enum class AudioTrimHandle {
    None,
    Start,
    End
}

/**
 * 可拖拽的裁剪波形，负责绘制采样柱、选区遮罩、左右手柄和播放头。
 */
@Composable
fun AudioTrimWaveform(
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

private fun List<Float>.toDisplayWaveform(maxPointCount: Int): List<Float> {
    if (maxPointCount !in 1..<size) {
        return this
    }
    val chunkSize = (size + maxPointCount - 1) / maxPointCount
    // 每组取峰值可以保留瞬态声音特征，同时限制柱子数量，避免短录音波形互相覆盖成实线。
    return chunked(chunkSize).map { chunk -> chunk.maxOrNull() ?: 0.04F }
}