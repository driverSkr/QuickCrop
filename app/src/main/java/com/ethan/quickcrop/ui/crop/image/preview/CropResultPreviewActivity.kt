package com.ethan.quickcrop.ui.crop.image.preview

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import com.ethan.base.BaseActivity
import com.ethan.quickcrop.ui.theme.QuickCropTheme

class CropResultPreviewActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageUri = getStringExtra(EXTRA_IMAGE_URI)?.let(Uri::parse)
        setContent {
            QuickCropTheme {
                CropResultPreviewPage(imageUri = imageUri)
            }
        }
    }

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
    }
}
