package com.ethan.quickcrop.model

// 裁剪框本质就是一个矩形状态
data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
