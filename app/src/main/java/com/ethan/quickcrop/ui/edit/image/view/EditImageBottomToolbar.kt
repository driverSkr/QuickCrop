package com.ethan.quickcrop.ui.edit.image.view

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethan.quickcrop.R

enum class EditImageTool(
    val label: String,
    val iconRes: Int
) {
    Crop(label = "裁剪", iconRes = R.drawable.fa_crop),
    Filter(label = "滤镜", iconRes = R.drawable.fa_palette),
    Adjust(label = "调节", iconRes = R.drawable.fa_adjust)
}

@Composable
fun EditImageBottomToolbar(
    selectedTool: EditImageTool,
    onToolClick: (EditImageTool) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 底部工具栏需要避开系统导航栏，避免按钮被手势条遮挡。
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditImageTool.entries.forEach { tool ->
            EditImageToolButton(
                tool = tool,
                selected = tool == selectedTool,
                modifier = Modifier.weight(1f),
                onClick = { onToolClick(tool) }
            )
        }
    }
}

@Composable
private fun EditImageToolButton(
    tool: EditImageTool,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val targetBackgroundColor = when {
        selected -> Color(0xFF7C3AED)
        isPressed -> Color(0xFF273244)
        else -> Color.Transparent
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        label = "toolBackgroundColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        label = "toolPressedScale"
    )
    val iconColor = if (selected) Color.White else Color(0xFFD1D5DB)
    val textColor = if (selected) Color.White else Color(0xFF9CA3AF)

    Column(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .height(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FaIcon(iconRes = tool.iconRes, tint = iconColor, modifier = Modifier.size(22.dp))
        Text(
            text = tool.label,
            color = textColor,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun FaIcon(iconRes: Int, tint: Color, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint)
    )
}