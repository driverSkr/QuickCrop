package com.ethan.quickcrop.ui.edit.image.view

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethan.quickcrop.R
import com.ethan.quickcrop.custom.ArcValueScale
import com.ethan.quickcrop.custom.ArcValueScaleState
import com.ethan.quickcrop.custom.NumericValueIndicator
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "AdjustEditorContent"
const val DEFAULT_ADJUSTMENT_LIMIT = 50F

/**
 * 基础调节面板，使用弧形刻度盘调整当前选中的亮度、对比度等参数。
 */
@Composable
fun AdjustEditorContent(
    selectedType: EditImageAdjustmentType,
    adjustments: EditImageAdjustments,
    scaleState: ArcValueScaleState,
    enabled: Boolean,
    onTypeClick: (EditImageAdjustmentType) -> Unit,
    onValueChanged: (EditImageAdjustmentType, Int) -> Unit
) {
    val currentValue = scaleState.currentValue
        .roundToInt()
        .coerceIn(-DEFAULT_ADJUSTMENT_LIMIT.toInt(), DEFAULT_ADJUSTMENT_LIMIT.toInt())
    val progressFraction = (abs(scaleState.currentValue) / DEFAULT_ADJUSTMENT_LIMIT).coerceIn(0F, 1F)

    // 刻度盘和调节项按钮分离，切换调节项时只同步刻度值，不清空其他参数。
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NumericValueIndicator(
            value = currentValue,
            progressFraction = progressFraction,
            isNegative = currentValue < 0,
            modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
            // 调节拖动时需要数字和圆环跟手，不等待过渡动画。
            animateProgress = false
        )

        ArcValueScale(
            state = scaleState,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            onValueChanged = { nextValue ->
                onValueChanged(selectedType, nextValue.roundToInt())
            },
            onStartMove = {
                Log.d(TAG, "开始调整基础参数: type=${selectedType.label}, value=${scaleState.currentValue}")
            },
            onEndMove = {
                Log.d(TAG, "结束调整基础参数: type=${selectedType.label}, value=${scaleState.currentValue}")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val adjustmentTypes = EditImageAdjustmentType.entries
            items(adjustmentTypes.size) { index ->
                val type = adjustmentTypes[index]
                AdjustmentOptionButton(
                    type = type,
                    value = adjustments.valueOf(type),
                    selected = type == selectedType,
                    onClick = { onTypeClick(type) }
                )
            }
        }
    }
}

/**
 * 单个基础调节项按钮，展示图标、名称和当前数值。
 */
@Composable
private fun AdjustmentOptionButton(
    type: EditImageAdjustmentType,
    value: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) Color.White else Color(0xFF212121)
    val contentColor = if (selected) Color.Black else Color.White
    val borderColor = if (selected) Color.White else Color(0x33FFFFFF)

    Column(
        modifier = Modifier
            .width(72.dp)
            .height(66.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(type.iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            // 调节项图标只使用黑白两色，避免每个功能项出现彩色视觉干扰。
            colorFilter = ColorFilter.tint(contentColor)
        )
        Text(
            text = type.label,
            color = contentColor,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(top = 5.dp)
        )
        Text(
            text = formatAdjustmentValue(value),
            color = contentColor,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            maxLines = 1
        )
    }
}

/**
 * 基础调节项类型，每个类型对应底部调节按钮和一个数值通道。
 */
enum class EditImageAdjustmentType(
    val label: String,
    val iconRes: Int
) {
    Brightness(label = "亮度", iconRes = R.drawable.fa_sun),
    Contrast(label = "对比度", iconRes = R.drawable.fa_adjust),
    Saturation(label = "饱和度", iconRes = R.drawable.fa_palette),
    Temperature(label = "色温", iconRes = R.drawable.fa_temperature_half),
    Clarity(label = "清晰度", iconRes = R.drawable.fa_bolt)
}

/**
 * 格式化调节数值，正数前补加号便于用户区分增减方向。
 */
private fun formatAdjustmentValue(value: Int): String {
    return if (value > 0) "+$value" else value.toString()
}

/**
 * 图片基础调节参数集合，范围由编辑页面的刻度盘统一限制。
 */
data class EditImageAdjustments(
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val temperature: Int = 0,
    val clarity: Int = 0
) {
    /**
     * 获取指定调节项当前值，用于同步按钮显示和刻度盘位置。
     */
    fun valueOf(type: EditImageAdjustmentType): Int {
        return when (type) {
            EditImageAdjustmentType.Brightness -> brightness
            EditImageAdjustmentType.Contrast -> contrast
            EditImageAdjustmentType.Saturation -> saturation
            EditImageAdjustmentType.Temperature -> temperature
            EditImageAdjustmentType.Clarity -> clarity
        }
    }

    /**
     * 返回更新指定调节项后的新状态，保持数据类不可变更新。
     */
    fun withValue(type: EditImageAdjustmentType, value: Int): EditImageAdjustments {
        return when (type) {
            EditImageAdjustmentType.Brightness -> copy(brightness = value)
            EditImageAdjustmentType.Contrast -> copy(contrast = value)
            EditImageAdjustmentType.Saturation -> copy(saturation = value)
            EditImageAdjustmentType.Temperature -> copy(temperature = value)
            EditImageAdjustmentType.Clarity -> copy(clarity = value)
        }
    }
}