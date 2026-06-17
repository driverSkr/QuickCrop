package com.ethan.quickcrop.custom

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Immutable
data class RotateAngleIndicatorColors(
    val background: Color = Color(0xFF212121),
    val positiveProgress: Color = Color(0xFFFFBC2C),
    val positiveTrack: Color = Color(0x26FFBC2C),
    val negativeProgress: Color = Color.White,
    val negativeTrack: Color = Color(0x26FFFFFF)
)

/**
 * Compose 版旋转角度数值指示器。
 *
 * 该组件只负责“圆环进度 + 数字”的展示，不绑定裁剪页业务，后续 Compose 页面可以独立复用。
 */
@Composable
fun RotateAngleIndicator(
    angle: Int,
    progressFraction: Float,
    isNegative: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 2.dp,
    colors: RotateAngleIndicatorColors = RotateAngleIndicatorColors(),
    animateProgress: Boolean = true
) {
    val signedProgressTarget = if (isNegative) {
        -progressFraction.coerceIn(0F, 1F)
    } else {
        progressFraction.coerceIn(0F, 1F)
    }
    val signedProgress by animateFloatAsState(
        targetValue = signedProgressTarget,
        animationSpec = tween(durationMillis = if (animateProgress) 120 else 0),
        label = "RotateAngleIndicator"
    )
    val resolvedNegative = signedProgress < 0F
    val progressColor = if (resolvedNegative) colors.negativeProgress else colors.positiveProgress
    val trackColor = if (resolvedNegative) colors.negativeTrack else colors.positiveTrack

    Box(
        modifier = modifier
            .size(size)
            .background(colors.background, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidthPx = strokeWidth.toPx()
            val inset = strokeWidthPx / 2F
            val arcSize = Size(
                width = this.size.width - strokeWidthPx,
                height = this.size.height - strokeWidthPx
            )
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = 0F,
                sweepAngle = 360F,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )

            val sweepAngle = abs(signedProgress).coerceIn(0F, 1F) * 360F
            if (sweepAngle > 0.01F) {
                drawArc(
                    color = progressColor,
                    startAngle = -90F,
                    sweepAngle = if (signedProgress < 0F) -sweepAngle else sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }
        }

        Text(
            text = angle.toString(),
            color = if (isNegative) colors.negativeProgress else colors.positiveProgress,
            fontSize = 14.sp,
            fontWeight = FontWeight.W400,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
