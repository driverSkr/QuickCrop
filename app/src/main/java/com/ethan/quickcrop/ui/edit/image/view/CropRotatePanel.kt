package com.ethan.quickcrop.ui.edit.image.view

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

private const val TAG = "CropRotatePanel"
private const val DEFAULT_ROTATE_ANGLE_LIMIT = 45F

/**
 * 固定比例裁剪的方向，用于把 16:9 等比例切换为横向或纵向。
 */
enum class CropAspectOrientation(val label: String) {
    Portrait(label = "纵向"),
    Landscape(label = "横向")
}

/**
 * 裁剪工具面板，组合镜像、90 度旋转、微调旋转、方向和比例选择。
 */
@Composable
fun CropRotatePanel(
    rotateAngle: Float,
    rotateScaleState: ArcValueScaleState,
    aspectRatioList: List<String>,
    selectedAspectRatio: String,
    selectedCropOrientation: CropAspectOrientation,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onMirrorClick: () -> Unit,
    onRotateRightAngleClick: () -> Unit,
    onCropOrientationClick: (CropAspectOrientation) -> Unit,
    onAspectRatioClick: (String) -> Unit
) {
    val displayAngle = rotateAngle.roundToInt()
    val progressFraction = (abs(rotateAngle) / DEFAULT_ROTATE_ANGLE_LIMIT).coerceIn(0F, 1F)

    // 中间数值指示器展示微调旋转角度，两侧按钮处理离散变换。
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.svg_switch),
                contentDescription = "镜像图片",
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onMirrorClick
                    )
            )
            Spacer(modifier = Modifier.width(24.dp))
            NumericValueIndicator(
                value = displayAngle,
                progressFraction = progressFraction,
                isNegative = rotateAngle < 0F,
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
                // 拖动刻度时保持数字和圆环即时反馈，避免动画造成轻微滞后感。
                animateProgress = false
            )
            Spacer(modifier = Modifier.width(24.dp))
            Image(
                painter = painterResource(R.drawable.svg_rotate),
                contentDescription = "旋转 90 度",
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onRotateRightAngleClick
                    )
            )
        }

        ArcValueScale(
            state = rotateScaleState,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            onStartMove = {
                Log.d(TAG, "开始调整图片旋转角度: ${rotateScaleState.currentValue}")
            },
            onEndMove = {
                Log.d(TAG, "结束调整图片旋转角度: ${rotateScaleState.currentValue}")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        CropOrientationSelector(
            selectedOrientation = selectedCropOrientation,
            enabled = enabled,
            onOrientationClick = onCropOrientationClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        CropAspectRatioSelector(
            aspectRatioList = aspectRatioList,
            selectedAspectRatio = selectedAspectRatio,
            enabled = enabled,
            onAspectRatioClick = onAspectRatioClick
        )
    }
}

/**
 * 裁剪方向选择器，提供纵向和横向两种固定比例解释方式。
 */
@Composable
private fun CropOrientationSelector(
    selectedOrientation: CropAspectOrientation,
    enabled: Boolean,
    onOrientationClick: (CropAspectOrientation) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CropAspectOrientation.entries.forEachIndexed { index, orientation ->
            CropOrientationButton(
                orientation = orientation,
                selected = orientation == selectedOrientation,
                enabled = enabled,
                onClick = { onOrientationClick(orientation) }
            )
            if (index != CropAspectOrientation.entries.lastIndex) {
                Spacer(modifier = Modifier.width(12.dp))
            }
        }
    }
}

/**
 * 裁剪方向按钮，用矩形轮廓表达当前比例方向。
 */
@Composable
private fun CropOrientationButton(
    orientation: CropAspectOrientation,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) Color.White else Color(0xFF212121)
    val iconColor = if (selected) Color.Black else Color.White
    val borderColor = if (selected) Color.White else Color(0x33FFFFFF)
    val iconWidth = if (orientation == CropAspectOrientation.Portrait) 13.dp else 26.dp
    val iconHeight = if (orientation == CropAspectOrientation.Portrait) 26.dp else 13.dp

    Box(
        modifier = Modifier
            .width(52.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor, RoundedCornerShape(14.dp))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = iconWidth, height = iconHeight)
                .clip(RoundedCornerShape(2.dp))
                // 方向按钮只用填充矩形表达横竖方向，避免和比例模式文字争抢视觉层级。
                .background(iconColor.copy(alpha = if (enabled) 1F else 0.45F))
        )
    }
}

/**
 * 裁剪比例横向列表，展示自由、原始和常用固定比例。
 */
@Composable
private fun CropAspectRatioSelector(
    aspectRatioList: List<String>,
    selectedAspectRatio: String,
    enabled: Boolean,
    onAspectRatioClick: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(aspectRatioList.size) { index ->
            val aspectRatio = aspectRatioList[index]
            CropAspectRatioButton(
                text = aspectRatio,
                selected = aspectRatio == selectedAspectRatio,
                enabled = enabled,
                onClick = { onAspectRatioClick(aspectRatio) }
            )
        }
    }
}

/**
 * 单个裁剪比例按钮，负责展示选中态并触发比例切换。
 */
@Composable
private fun CropAspectRatioButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) Color.White else Color(0xFF212121)
    val textColor = if (selected) Color.Black else Color.White
    val borderColor = if (selected) Color.White else Color(0x33FFFFFF)

    Text(
        text = text,
        color = textColor.copy(alpha = if (enabled) 1F else 0.45F),
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor, RoundedCornerShape(18.dp))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(18.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            // 裁剪比例按钮固定高度和横向内边距，避免模式文案切换时排版跳动。
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}