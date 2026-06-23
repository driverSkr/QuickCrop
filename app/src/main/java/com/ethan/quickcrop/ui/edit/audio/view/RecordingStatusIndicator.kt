package com.ethan.quickcrop.ui.edit.audio.view

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethan.quickcrop.R
import com.ethan.quickcrop.core.audio.AudioRecordingStatus

/**
 * 录音状态指示器，通过颜色和脉冲动画表达空闲、录制中和暂停状态。
 */
@Composable
fun RecordingStatusIndicator(status: AudioRecordingStatus) {
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
            modifier = Modifier.size(80.dp).clip(CircleShape).background(centerColor),
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