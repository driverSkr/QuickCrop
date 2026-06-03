package com.ethan.quickcrop.ui.crop.image.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.ethan.quickcrop.extension.moveInsideBounds
import com.ethan.quickcrop.extension.resizeFree
import com.ethan.quickcrop.extension.resizeWithAspectRatio
import com.ethan.quickcrop.ui.crop.image.model.DragMode
import kotlin.math.min

/**
 * 支持四角缩放 + 固定比例的裁剪框
 */
@Composable
fun ResizableCropBox(
    modifier: Modifier = Modifier,
    cropBounds: Rect = Rect.Zero,
    aspectRatio: Float? = 1f,   // null 表示自由比例；1f 表示 1:1；16f / 9f 表示 16:9
    minSize: Float = 160f,
    // 所有裁剪框变化都会回调，页面用它保存最新矩形并参与最终导出。
    onCropRectChanged: (Rect) -> Unit = {},
    // 只有用户主动拖动/拉伸时回调，页面用它点亮裁剪按钮。
    onCropRectUserChanged: (Rect) -> Unit = {},
) {
    // 记录画布尺寸
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // 裁剪框尺寸、位置
    var cropRect by remember { mutableStateOf(Rect.Zero) }
    // 拖拽模式
    var dragMode by remember { mutableStateOf(DragMode.None) }
    // 边角触摸半径
    val cornerTouchRadius = 60f
    // 角标长度
    val cornerHandleLength = 52f
    // 角标粗细
    val cornerHandleThickness = 10f

    LaunchedEffect(cropBounds, aspectRatio) {
        // 图片加载或比例切换时，裁剪框重置为图片区域内可容纳的最大矩形。
        if (!cropBounds.isEmpty) {
            cropRect = createInitialCropRect(cropBounds, aspectRatio)
            onCropRectChanged(cropRect)
        }
    }

    Canvas(modifier = modifier
        .fillMaxSize()
        .onSizeChanged{
            canvasSize = it
        }
        .graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .pointerInput(canvasSize, cropBounds, aspectRatio) {
            detectDragGestures(
                onDragStart = { offset ->
                    dragMode = detectDragMode(
                        touch = offset,
                        rect = cropRect,
                        cornerRadius = cornerTouchRadius
                    )
                },
                onDragEnd = {
                    dragMode = DragMode.None
                },
                onDragCancel = {
                    dragMode = DragMode.None
                },
                onDrag = { change, dragAmount ->
                    // 裁剪框手势自己消费拖动，避免和外层可能存在的图片预览手势互相干扰。
                    change.consume()
                    if (cropBounds.isEmpty) {
                        return@detectDragGestures
                    }
                    val safeMinSize = minSize.coerceAtMost(min(cropBounds.width, cropBounds.height))
                    val newCropRect = when (dragMode) {
                        DragMode.Move -> {
                            cropRect.moveInsideBounds(dragAmount, cropBounds)
                        }
                        DragMode.TopLeft,
                        DragMode.TopRight,
                        DragMode.BottomLeft,
                        DragMode.BottomRight -> {
                            cropRect.resizeFromCorner(
                                mode = dragMode,
                                dragAmount = dragAmount,
                                bounds = cropBounds,
                                minSize = safeMinSize,
                                aspectRatio = aspectRatio
                            )
                        }
                        DragMode.None -> cropRect
                     }
                    if (newCropRect != cropRect) {
                        // 用户真实拖动后通知页面，便于外层点亮裁剪按钮。
                        cropRect = newCropRect
                        onCropRectChanged(newCropRect)
                        onCropRectUserChanged(newCropRect)
                    }
                }
            )
        }
    ) {
        if (cropBounds.isEmpty || cropRect.isEmpty) {
            return@Canvas
        }

        // 1. 半透明遮罩
        drawRect(
            color = Color.Black.copy(alpha = 0.55f),
            size = size
        )

        // 2. 挖空裁剪区域
        drawRect(
            color = Color.Transparent,
            topLeft = Offset(cropRect.left, cropRect.top),
            size = Size(cropRect.width, cropRect.height),
            blendMode = BlendMode.Clear
        )

        // 3. 边框
        drawRect(
            color = Color.White,
            topLeft = Offset(cropRect.left, cropRect.top),
            size = Size(cropRect.width, cropRect.height),
            style = Stroke(width = 4f)
        )

        // 4. 九宫格辅助线
        drawRuleOfThirds(cropRect)

        // 5. 四角控制点
        drawCornerHandles(
            rect = cropRect,
            length = cornerHandleLength,
            thickness = cornerHandleThickness
        )
    }
}

/**
 * 判断当前拖的是哪里
 */
private fun detectDragMode(
    touch: Offset,
    rect: Rect,
    cornerRadius: Float
): DragMode {
    val topLeft = Offset(rect.left, rect.top)
    val topRight = Offset(rect.right, rect.top)
    val bottomLeft = Offset(rect.left, rect.bottom)
    val bottomRight = Offset(rect.right, rect.bottom)

    return when {
        touch.distanceTo(topLeft) <= cornerRadius -> DragMode.TopLeft
        touch.distanceTo(topRight) <= cornerRadius -> DragMode.TopRight
        touch.distanceTo(bottomLeft) <= cornerRadius -> DragMode.BottomLeft
        touch.distanceTo(bottomRight) <= cornerRadius -> DragMode.BottomRight

        rect.contains(touch) -> DragMode.Move

        else -> DragMode.None
    }
}

private fun Offset.distanceTo(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

/**
 * 四角缩放核心逻辑
 */
private fun Rect.resizeFromCorner(
    mode: DragMode,
    dragAmount: Offset,
    bounds: Rect,
    minSize: Float,
    aspectRatio: Float?
): Rect {
    return if (aspectRatio == null) {
        resizeFree(
            mode = mode,
            dragAmount = dragAmount,
            bounds = bounds,
            minSize = minSize
        )
    } else {
        resizeWithAspectRatio(
            mode = mode,
            dragAmount = dragAmount,
            bounds = bounds,
            minSize = minSize,
            aspectRatio = aspectRatio
        )
    }
}

private fun createInitialCropRect(bounds: Rect, aspectRatio: Float?): Rect {
    // 初始裁剪框缩小到 bounds 的 90%，留出拖拽移动的空间。
    val initialScale = 0.9f

    if (aspectRatio == null || aspectRatio <= 0f) {
        val cropWidth = bounds.width * initialScale
        val cropHeight = bounds.height * initialScale
        val left = bounds.left + (bounds.width - cropWidth) / 2f
        val top = bounds.top + (bounds.height - cropHeight) / 2f
        return Rect(
            left = left,
            top = top,
            right = left + cropWidth,
            bottom = top + cropHeight
        )
    }

    val boundsRatio = bounds.width / bounds.height
    val maxWidth: Float
    val maxHeight: Float
    if (boundsRatio > aspectRatio) {
        maxHeight = bounds.height * initialScale
        maxWidth = maxHeight * aspectRatio
    } else {
        maxWidth = bounds.width * initialScale
        maxHeight = maxWidth / aspectRatio
    }

    // 按目标比例在图片内居中放置，避免裁剪框覆盖到图片外的空白区域。
    val left = bounds.left + (bounds.width - maxWidth) / 2f
    val top = bounds.top + (bounds.height - maxHeight) / 2f
    return Rect(
        left = left,
        top = top,
        right = left + maxWidth,
        bottom = top + maxHeight
    )
}

/**
 * 绘制九宫格
 */
private fun DrawScope.drawRuleOfThirds(rect: Rect) {
    val oneThirdWidth = rect.width / 3f
    val oneThirdHeight = rect.height / 3f

    for (i in 1..2) {
        val x = rect.left + oneThirdWidth * i
        drawLine(
            color = Color.White.copy(alpha = 0.6f),
            start = Offset(x, rect.top),
            end = Offset(x, rect.bottom),
            strokeWidth = 2f
        )

        val y = rect.top + oneThirdHeight * i
        drawLine(
            color = Color.White.copy(alpha = 0.6f),
            start = Offset(rect.left, y),
            end = Offset(rect.right, y),
            strokeWidth = 2f
        )
    }
}

/**
 * 绘制四角 L 形控制柄。
 *
 * 每个角由一横一竖两个矩形拼成，矩形粗细一半落在裁剪框内、一半落在裁剪框外，
 * 形成常见图片裁剪框里那种“内侧被挖掉”的直角控制点。
 */
private fun DrawScope.drawCornerHandles(
    rect: Rect,
    length: Float,
    thickness: Float
) {
    val halfThickness = thickness / 2f

    drawCornerHandle(
        corner = Offset(rect.left, rect.top),
        horizontalDirection = 1f,
        verticalDirection = 1f,
        length = length,
        thickness = thickness,
        halfThickness = halfThickness
    )
    drawCornerHandle(
        corner = Offset(rect.right, rect.top),
        horizontalDirection = -1f,
        verticalDirection = 1f,
        length = length,
        thickness = thickness,
        halfThickness = halfThickness
    )
    drawCornerHandle(
        corner = Offset(rect.left, rect.bottom),
        horizontalDirection = 1f,
        verticalDirection = -1f,
        length = length,
        thickness = thickness,
        halfThickness = halfThickness
    )
    drawCornerHandle(
        corner = Offset(rect.right, rect.bottom),
        horizontalDirection = -1f,
        verticalDirection = -1f,
        length = length,
        thickness = thickness,
        halfThickness = halfThickness
    )
}

/**
 * 绘制单个角的横向和纵向矩形边。
 */
private fun DrawScope.drawCornerHandle(
    corner: Offset,
    horizontalDirection: Float,
    verticalDirection: Float,
    length: Float,
    thickness: Float,
    halfThickness: Float
) {
    val horizontalLeft = if (horizontalDirection > 0f) {
        corner.x - halfThickness
    } else {
        corner.x - length + halfThickness
    }
    val verticalTop = if (verticalDirection > 0f) {
        corner.y - halfThickness
    } else {
        corner.y - length + halfThickness
    }
    val verticalLeft = corner.x - halfThickness
    val horizontalTop = corner.y - halfThickness

    drawRect(
        color = Color.White,
        topLeft = Offset(horizontalLeft, horizontalTop),
        size = Size(length, thickness)
    )
    drawRect(
        color = Color.White,
        topLeft = Offset(verticalLeft, verticalTop),
        size = Size(thickness, length)
    )
}
