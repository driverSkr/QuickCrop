package com.ethan.quickcrop

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ethan.quickcrop.feature.image.ImageEditorScreen
import com.ethan.quickcrop.ui.theme.QuickCropTheme

class ImageEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sourceUri = intent.getStringExtra(MainActivity.EXTRA_MEDIA_URI)?.let(Uri::parse)
        setContent {
            QuickCropTheme {
                ImageEditorScreen(
                    sourceUri = sourceUri,
                    onBack = {
                        finish()
                    }
                )
            }
        }
    }
}
