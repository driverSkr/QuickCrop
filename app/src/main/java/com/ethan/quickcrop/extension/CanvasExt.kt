package com.ethan.quickcrop.extension

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.ethan.quickcrop.ui.edit.image.model.DragMode
import kotlin.math.abs

fun Rect.moveInsideCanvas(
    dragAmount: Offset,
    canvasSize: Size
): Rect {
    val newLeft = left + dragAmount.x
    val newTop = top + dragAmount.y

    val maxLeft = canvasSize.width - width
    val maxTop = canvasSize.height - height

    val fixedLeft = newLeft.coerceIn(0f, maxLeft.coerceAtLeast(0f))
    val fixedTop = newTop.coerceIn(0f, maxTop.coerceAtLeast(0f))

    return Rect(
        left = fixedLeft,
        top = fixedTop,
        right = fixedLeft + width,
        bottom = fixedTop + height
    )
}

fun Rect.moveInsideBounds(
    dragAmount: Offset,
    bounds: Rect
): Rect {
    val newLeft = left + dragAmount.x
    val newTop = top + dragAmount.y

    val maxLeft = bounds.right - width
    val maxTop = bounds.bottom - height

    // 只锁住已经贴满的方向，另一个方向仍然允许移动，避免固定比例裁剪框被误认为完全不能拖动。
    val fixedLeft = if (width >= bounds.width) {
        bounds.left + (bounds.width - width) / 2F
    } else {
        newLeft.coerceIn(bounds.left, maxLeft)
    }
    val fixedTop = if (height >= bounds.height) {
        bounds.top + (bounds.height - height) / 2F
    } else {
        newTop.coerceIn(bounds.top, maxTop)
    }

    return Rect(
        left = fixedLeft,
        top = fixedTop,
        right = fixedLeft + width,
        bottom = fixedTop + height
    )
}

/**
 * 固定比例缩放
 */
fun Rect.resizeWithAspectRatio(
    mode: DragMode,
    dragAmount: Offset,
    bounds: Rect,
    minSize: Float,
    aspectRatio: Float
): Rect {
    val fixedX: Float
    val fixedY: Float

    var movingX: Float
    var movingY: Float

    when (mode) {
        DragMode.TopLeft -> {
            fixedX = right
            fixedY = bottom
            movingX = left + dragAmount.x
            movingY = top + dragAmount.y
        }

        DragMode.TopRight -> {
            fixedX = left
            fixedY = bottom
            movingX = right + dragAmount.x
            movingY = top + dragAmount.y
        }

        DragMode.BottomLeft -> {
            fixedX = right
            fixedY = top
            movingX = left + dragAmount.x
            movingY = bottom + dragAmount.y
        }

        DragMode.BottomRight -> {
            fixedX = left
            fixedY = top
            movingX = right + dragAmount.x
            movingY = bottom + dragAmount.y
        }

        else -> return this
    }

    val widthFromHorizontalDrag = abs(movingX - fixedX)
    val widthFromVerticalDrag = abs(movingY - fixedY) * aspectRatio
    // 固定比例缩放同时响应横向和纵向拖动；初始框贴住图片时，从上/下边缘向内拖也应能缩放。
    var newWidth = if (abs(dragAmount.x) >= abs(dragAmount.y) * aspectRatio) {
        widthFromHorizontalDrag
    } else {
        widthFromVerticalDrag
    }
    var newHeight = newWidth / aspectRatio

    if (newWidth < minSize) {
        newWidth = minSize
        newHeight = newWidth / aspectRatio
    }

    if (newHeight < minSize) {
        newHeight = minSize
        newWidth = newHeight * aspectRatio
    }

    val signX = if (movingX >= fixedX) 1f else -1f
    val signY = if (movingY >= fixedY) 1f else -1f

    var newLeft: Float
    var newTop: Float
    var newRight: Float
    var newBottom: Float

    if (signX > 0) {
        newLeft = fixedX
        newRight = fixedX + newWidth
    } else {
        newLeft = fixedX - newWidth
        newRight = fixedX
    }

    if (signY > 0) {
        newTop = fixedY
        newBottom = fixedY + newHeight
    } else {
        newTop = fixedY - newHeight
        newBottom = fixedY
    }

    // 超出图片边界时，按边界重新缩小，避免裁剪到图片外的空白区域。
    if (newLeft < bounds.left) {
        newLeft = bounds.left
        newWidth = newRight - newLeft
        newHeight = newWidth / aspectRatio

        if (signY > 0) {
            newBottom = newTop + newHeight
        } else {
            newTop = newBottom - newHeight
        }
    }

    if (newRight > bounds.right) {
        newRight = bounds.right
        newWidth = newRight - newLeft
        newHeight = newWidth / aspectRatio

        if (signY > 0) {
            newBottom = newTop + newHeight
        } else {
            newTop = newBottom - newHeight
        }
    }

    if (newTop < bounds.top) {
        newTop = bounds.top
        newHeight = newBottom - newTop
        newWidth = newHeight * aspectRatio

        if (signX > 0) {
            newRight = newLeft + newWidth
        } else {
            newLeft = newRight - newWidth
        }
    }

    if (newBottom > bounds.bottom) {
        newBottom = bounds.bottom
        newHeight = newBottom - newTop
        newWidth = newHeight * aspectRatio

        if (signX > 0) {
            newRight = newLeft + newWidth
        } else {
            newLeft = newRight - newWidth
        }
    }

    return Rect(
        left = newLeft.coerceIn(bounds.left, bounds.right),
        top = newTop.coerceIn(bounds.top, bounds.bottom),
        right = newRight.coerceIn(bounds.left, bounds.right),
        bottom = newBottom.coerceIn(bounds.top, bounds.bottom)
    )
}

/**
 * 自由比例缩放
 */
fun Rect.resizeFree(
    mode: DragMode,
    dragAmount: Offset,
    bounds: Rect,
    minSize: Float
): Rect {
    var newLeft = left
    var newTop = top
    var newRight = right
    var newBottom = bottom

    when (mode) {
        DragMode.TopLeft -> {
            newLeft += dragAmount.x
            newTop += dragAmount.y
        }

        DragMode.TopRight -> {
            newRight += dragAmount.x
            newTop += dragAmount.y
        }

        DragMode.BottomLeft -> {
            newLeft += dragAmount.x
            newBottom += dragAmount.y
        }

        DragMode.BottomRight -> {
            newRight += dragAmount.x
            newBottom += dragAmount.y
        }

        else -> Unit
    }

    // 边界限制在图片实际显示区域内，避免自由比例拖到空白位置。
    newLeft = newLeft.coerceIn(bounds.left, (right - minSize).coerceAtLeast(bounds.left))
    newTop = newTop.coerceIn(bounds.top, (bottom - minSize).coerceAtLeast(bounds.top))
    newRight = newRight.coerceIn((left + minSize).coerceAtMost(bounds.right), bounds.right)
    newBottom = newBottom.coerceIn((top + minSize).coerceAtMost(bounds.bottom), bounds.bottom)

    return Rect(newLeft, newTop, newRight, newBottom)
}
