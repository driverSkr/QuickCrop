package com.ethan.quickcrop.ui.crop.image.extension

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.ethan.quickcrop.ui.crop.image.model.DragMode

fun Rect.moveInsideCanvas(
    dragAmount: Offset,
    canvasSize: Size
): Rect {
    val newLeft = left + dragAmount.x
    val newTop = top + dragAmount.y

    val maxLeft = canvasSize.width - width
    val maxTop = canvasSize.height - height

    val fixedLeft = newLeft.coerceIn(0f, maxLeft)
    val fixedTop = newTop.coerceIn(0f, maxTop)

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
    canvasSize: Size,
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

    var newWidth = kotlin.math.abs(movingX - fixedX)
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

    // 超出边界时，按边界重新缩小
    if (newLeft < 0f) {
        newLeft = 0f
        newWidth = newRight - newLeft
        newHeight = newWidth / aspectRatio

        if (signY > 0) {
            newBottom = newTop + newHeight
        } else {
            newTop = newBottom - newHeight
        }
    }

    if (newRight > canvasSize.width) {
        newRight = canvasSize.width
        newWidth = newRight - newLeft
        newHeight = newWidth / aspectRatio

        if (signY > 0) {
            newBottom = newTop + newHeight
        } else {
            newTop = newBottom - newHeight
        }
    }

    if (newTop < 0f) {
        newTop = 0f
        newHeight = newBottom - newTop
        newWidth = newHeight * aspectRatio

        if (signX > 0) {
            newRight = newLeft + newWidth
        } else {
            newLeft = newRight - newWidth
        }
    }

    if (newBottom > canvasSize.height) {
        newBottom = canvasSize.height
        newHeight = newBottom - newTop
        newWidth = newHeight * aspectRatio

        if (signX > 0) {
            newRight = newLeft + newWidth
        } else {
            newLeft = newRight - newWidth
        }
    }

    return Rect(
        left = newLeft.coerceIn(0f, canvasSize.width),
        top = newTop.coerceIn(0f, canvasSize.height),
        right = newRight.coerceIn(0f, canvasSize.width),
        bottom = newBottom.coerceIn(0f, canvasSize.height)
    )
}

/**
 * 自由比例缩放
 */
fun Rect.resizeFree(
    mode: DragMode,
    dragAmount: Offset,
    canvasSize: Size,
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

    // 边界限制
    newLeft = newLeft.coerceIn(0f, right - minSize)
    newTop = newTop.coerceIn(0f, bottom - minSize)
    newRight = newRight.coerceIn(left + minSize, canvasSize.width)
    newBottom = newBottom.coerceIn(top + minSize, canvasSize.height)

    return Rect(newLeft, newTop, newRight, newBottom)
}