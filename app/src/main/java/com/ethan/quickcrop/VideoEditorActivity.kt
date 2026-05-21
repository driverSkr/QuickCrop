package com.ethan.quickcrop

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ethan.base.BaseActivity
import com.ethan.quickcrop.feature.video.VideoEditorScreen
import com.ethan.quickcrop.ui.theme.QuickCropTheme

class VideoEditorActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sourceUri = getStringExtra(MainActivity.EXTRA_MEDIA_URI)?.let(Uri::parse)
        setContent {
            QuickCropTheme {
                VideoEditorScreen(
                    sourceUri = sourceUri,
                    onBack = {
                        finish()
                    }
                )
            }
        }
    }
}
