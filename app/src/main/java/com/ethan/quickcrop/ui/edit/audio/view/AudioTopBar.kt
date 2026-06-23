package com.ethan.quickcrop.ui.edit.audio.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethan.quickcrop.R
import com.ethan.quickcrop.core.audio.AudioRecordingStatus
import java.util.Locale

/**
 * 顶部导航栏，展示返回按钮、当前录音状态标题和录音时长。
 */
@Composable
fun AudioTopBar(
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

fun formatRecordingDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}