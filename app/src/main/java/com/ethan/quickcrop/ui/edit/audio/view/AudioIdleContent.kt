package com.ethan.quickcrop.ui.edit.audio.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethan.quickcrop.R
import com.ethan.quickcrop.core.audio.AudioRecordingStatus

/**
 * 空闲或错误状态内容，负责提示当前录音准备状态并提供开始录音入口。
 */
@Composable
fun AudioIdleContent(
    hasError: Boolean,
    onStartRecording: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
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