package com.ethan.quickcrop.ui.edit.audio.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 实时录音波形面板，绘制当前采样振幅并提示峰值是否过载。
 */
@Composable
fun LiveWaveformPanel(
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
        Canvas(modifier = Modifier.fillMaxWidth().height(126.dp)) {
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