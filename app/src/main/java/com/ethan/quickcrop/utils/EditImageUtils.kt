package com.ethan.quickcrop.utils

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

object EditImageUtils {
    fun calculateFitImageBounds(bitmap: Bitmap?, containerSize: IntSize): Rect {
        if (bitmap == null || containerSize.width <= 0 || containerSize.height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) {
            return Rect.Zero
        }

        val containerWidth = containerSize.width.toFloat()
        val containerHeight = containerSize.height.toFloat()
        val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val containerRatio = containerWidth / containerHeight

        val displayWidth: Float
        val displayHeight: Float
        if (containerRatio > imageRatio) {
            // 容器更宽时，图片高度贴满容器，高度决定显示尺寸。
            displayHeight = containerHeight
            displayWidth = displayHeight * imageRatio
        } else {
            // 容器更窄时，图片宽度贴满容器，宽度决定显示尺寸。
            displayWidth = containerWidth
            displayHeight = displayWidth / imageRatio
        }

        // 图片居中显示，因此需要计算上下或左右留白，裁剪坐标也以这个区域为准。
        val left = (containerWidth - displayWidth) / 2f
        val top = (containerHeight - displayHeight) / 2f
        return Rect(
            left = left,
            top = top,
            right = left + displayWidth,
            bottom = top + displayHeight
        )
    }
}