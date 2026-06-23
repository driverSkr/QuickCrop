package com.ethan.quickcrop.ui.edit.audio.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethan.quickcrop.R
import com.ethan.quickcrop.core.audio.AudioRecordingState
import com.ethan.quickcrop.core.audio.AudioRecordingStatus

/**
 * 录音中内容，展示实时波形、暂停/继续、停止和添加标记等控制。
 */
@Composable
fun AudioRecordingContent(
    state: AudioRecordingState,
    onPauseOrResume: () -> Unit,
    onStop: () -> Unit,
    onAddMarker: () -> Unit
) {
    val isRecording = state.status == AudioRecordingStatus.Recording
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RecordingStatusIndicator(status = state.status)
        Text(
            text = if (isRecording) "正在录音" else "录音已暂停",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            text = if (isRecording) "点击下方按钮停止录音" else "点击继续按钮恢复录音",
            color = Color(0xFF9CA3AF),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 24.dp)
        )

        LiveWaveformPanel(
            waveform = state.liveWaveform,
            peakDb = state.peakDb,
            isRecording = isRecording
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AudioRoundButton(
                iconRes = if (isRecording) R.drawable.fa_pause else R.drawable.fa_play,
                backgroundColor = if (isRecording) Color(0xFF374151) else Color(0xFF2563EB),
                contentDescription = if (isRecording) "暂停录音" else "继续录音",
                size = 56,
                onClick = onPauseOrResume
            )
            AudioRoundButton(
                iconRes = R.drawable.fa_stop,
                backgroundColor = Color(0xFFEF4444),
                contentDescription = "停止录音",
                size = 68,
                onClick = onStop
            )
            AudioRoundButton(
                iconRes = R.drawable.fa_bookmark,
                backgroundColor = Color(0xFF374151),
                contentDescription = "添加标记",
                size = 56,
                enabled = isRecording,
                onClick = onAddMarker
            )
        }

        Text(
            text = "录音结束后可进行裁剪和播放预览",
            color = Color(0xFF6B7280),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 24.dp)
        )
        if (state.markers.isNotEmpty()) {
            // 标记数量即时反馈，帮助用户确认点击添加标记已经生效。
            Text(
                text = "已添加 ${state.markers.size} 个标记",
                color = Color(0xFF3B82F6),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}