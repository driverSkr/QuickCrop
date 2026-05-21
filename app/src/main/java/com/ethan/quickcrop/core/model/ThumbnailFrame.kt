package com.ethan.quickcrop.core.model

import androidx.compose.ui.graphics.ImageBitmap

data class ThumbnailFrame(
    val timeMs: Long,
    val bitmap: ImageBitmap
)

