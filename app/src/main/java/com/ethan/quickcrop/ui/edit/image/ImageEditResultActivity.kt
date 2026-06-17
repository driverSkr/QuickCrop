package com.ethan.quickcrop.ui.edit.image

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import com.ethan.base.BaseActivity
import com.ethan.quickcrop.ui.edit.image.page.ImageEditResultPage
import com.ethan.quickcrop.ui.theme.QuickCropTheme

class ImageEditResultActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageUri = getStringExtra(EXTRA_IMAGE_URI)?.let(Uri::parse)
        setContent {
            QuickCropTheme {
                ImageEditResultPage(sourceUri = imageUri)
            }
        }
    }

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
    }
}