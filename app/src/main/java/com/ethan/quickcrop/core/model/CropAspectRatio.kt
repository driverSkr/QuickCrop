package com.ethan.quickcrop.core.model

enum class CropAspectRatio(val ratio: Float, val label: String) {
    OneToOne(1f, "1:1"),
    FourToFive(4f / 5f, "4:5"),
    SixteenToNine(16f / 9f, "16:9")
}

