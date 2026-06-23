package com.ethan.quickcrop.ui.edit.audio.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * 圆形图标按钮，统一录音、暂停、停止、播放等主要操作的样式。
 */
@Composable
fun AudioRoundButton(
    iconRes: Int,
    backgroundColor: Color,
    contentDescription: String,
    size: Int,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(backgroundColor.copy(alpha = if (enabled) 1F else 0.4F))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AudioIcon(
            iconRes = iconRes,
            tint = Color.White.copy(alpha = if (enabled) 1F else 0.45F),
            contentDescription = contentDescription,
            modifier = Modifier.size((size * 0.36F).dp)
        )
    }
}