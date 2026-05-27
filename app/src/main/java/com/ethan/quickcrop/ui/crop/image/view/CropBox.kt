package com.ethan.quickcrop.ui.crop.image.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.ethan.quickcrop.extension.moveInsideCanvas

/**
 * 自定义图片裁剪框组件。
 *
 * 这个 Composable 只负责绘制和拖动裁剪框本身：
 * 1. 在图片上方盖一层半透明黑色遮罩，让裁剪区域以外的部分变暗。
 * 2. 用 BlendMode.Clear 把裁剪区域“挖空”，露出底下的图片。
 * 3. 绘制白色边框和九宫格辅助线，帮助用户对齐构图。
 *
 * @param modifier 由外部传入的布局修饰符。保留这个参数是 Compose 组件的常见写法，
 *                 方便调用方控制大小、位置、padding、点击区域等外部布局行为。
 */
@Composable
fun CropBox(modifier: Modifier = Modifier) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // cropRect 保存当前裁剪框在 Canvas 坐标系中的位置和大小，这段 Rect 指的是 在当前绘制区域里的矩形坐标，也就是相对于 Canvas 左上角的坐标。
    var cropRect by remember {
        mutableStateOf(
            Rect(//左上角：(100, 200) 右上角：(1000, 200) 左下角：(100, 1100) 右下角：(1000, 1100)
                left = 100f,    // 左边界距离 Canvas 左边 100 像素
                top = 200f,     // 上边界距离 Canvas 顶部 200 像素
                right = 1000f,   // 右边界距离 Canvas 左边 1000 像素
                bottom = 1100f   // 下边界距离 Canvas 顶部 1100 像素
            )
        )
    }

    Canvas(
        modifier = modifier
            // 让 Canvas 填满父布局，这样遮罩、裁剪框和手势检测都覆盖完整图片区域。
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            // 使用离屏图层进行合成。
            // 原因：BlendMode.Clear 会清除当前绘制目标上的像素。如果直接作用在窗口画布上，
            // 可能把窗口背景也清掉，导致裁剪区域显示异常（例如变黑）。
            // Offscreen 会先把遮罩画到独立图层，再在这个图层里挖空裁剪区域，最后整体合成到界面上。
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            // 监听拖拽手势，让用户可以移动整个裁剪框。
            // Unit 作为 key 表示这个 pointerInput 生命周期不依赖外部参数变化。
            .pointerInput(canvasSize) {
                detectDragGestures { change, dragAmount ->
                    // 消费本次手势事件，避免拖动事件继续向下传递，引起底层组件同时响应。
                    change.consume()
                    // dragAmount 是本次手指移动的增量。
                    // translate 会在不改变宽高的情况下平移矩形，从而实现“拖动裁剪框”的效果。
//                    cropRect = cropRect.translate(dragAmount.x, dragAmount.y)
                    cropRect = cropRect.moveInsideCanvas(
                        dragAmount = dragAmount,
                        canvasSize = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                    )
                }
            }
    ) {
        // 1. 绘制覆盖整个 Canvas 的半透明黑色遮罩。
        // 这样裁剪框外的图片会被压暗，用户的注意力会集中在裁剪框内的保留区域。
        drawRect(color = Color.Black.copy(alpha = 0.55f), size = size)

        // 2. 清空裁剪框对应的矩形区域，让底下的图片透出来。
        // topLeft 决定挖空区域的左上角，size 决定挖空区域的宽高。
        // BlendMode.Clear 的作用是把这个区域的遮罩像素清除，而不是再画一个透明色矩形覆盖上去。
        drawRect(
            color = Color.Transparent,
            topLeft = Offset(cropRect.left, cropRect.top),
            size = Size(cropRect.width, cropRect.height),
            blendMode = BlendMode.Clear
        )

        // 3. 绘制裁剪框白色边框。
        // 使用 Stroke 表示只画矩形轮廓，不填充内部；内部已经在上一步被挖空，用来显示图片。
        drawRect(
            color = Color.White,
            topLeft = Offset(cropRect.left, cropRect.top),
            size = Size(cropRect.width, cropRect.height),
            style = Stroke(width = 4f)
        )

        // 4. 计算九宫格辅助线的间距。
        // 把裁剪框宽高各分成三等份，就能得到两条竖线和两条横线的位置。
        // 这种三分线常用于图片裁剪和构图，方便用户把主体对齐到视觉重点位置。
        val oneThirdWidth = cropRect.width / 3f
        val oneThirdHeight = cropRect.height / 3f

        // i 取 1 和 2，分别代表 1/3 与 2/3 位置。
        // 每次循环同时绘制一条竖向辅助线和一条横向辅助线。
        for (i in 1..2) {
            // 当前竖线的 x 坐标：裁剪框左边界 + 三分之一宽度的倍数。
            val x = cropRect.left + oneThirdWidth * i
            drawLine(
                // 辅助线使用半透明白色，能被用户看见，但不会像边框一样抢眼。
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(x, cropRect.top),
                end = Offset(x, cropRect.bottom),
                strokeWidth = 2f
            )

            // 当前横线的 y 坐标：裁剪框上边界 + 三分之一高度的倍数。
            val y = cropRect.top + oneThirdHeight * i
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(cropRect.left, y),
                end = Offset(cropRect.right, y),
                strokeWidth = 2f
            )
        }
    }
}
