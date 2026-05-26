package com.ethan.quickcrop.ui.crop.image.model

/**
 * 拖拽模式，作用是判断当前手指拖的是整个框，还是某个角
 */
enum class DragMode {
    None,
    Move,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight
}