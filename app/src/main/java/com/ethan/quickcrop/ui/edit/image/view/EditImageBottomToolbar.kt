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

/**
 * 图片编辑底部工具类型，决定当前展示裁剪、滤镜还是调节面板。
 */
enum class EditImageTool(val label: String, val iconRes: Int) {
    Crop(label = "裁剪", iconRes = R.drawable.fa_crop),
    Filter(label = "滤镜", iconRes = R.drawable.fa_palette),
    Adjust(label = "调节", iconRes = R.drawable.fa_adjust)
}

/**
 * 底部工具栏，承载裁剪、滤镜和调节三个一级编辑模式。
 */
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

/**
 * 底部工具按钮，带有选中态背景和按压缩放反馈。
 */
@Composable
private fun EditImageToolButton(
    tool: EditImageTool,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // 选中态、按压态和默认态使用同一套动画过渡，避免模式切换生硬。
    val targetBackgroundColor = when {
        selected -> Color.White
        isPressed -> Color(0xFF212121)
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
    val iconColor = if (selected) Color.Black else Color.White
    val textColor = if (selected) Color.Black else Color.White

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
        Image(
            painter = painterResource(tool.iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(iconColor)
        )
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