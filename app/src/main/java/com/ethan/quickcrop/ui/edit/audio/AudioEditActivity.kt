package com.ethan.quickcrop.ui.edit.audio

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.ethan.base.BaseActivity
import com.ethan.quickcrop.ui.edit.audio.page.AudioEditPage
import com.ethan.quickcrop.ui.theme.QuickCropTheme

class AudioEditActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuickCropTheme {
                AudioEditPage(
                    onExportCompleted = { outputUri ->
                        finishWithResult(
                            resultCode = RESULT_OK,
                            data = Intent().apply {
                                data = outputUri
                                putExtra(EXTRA_AUDIO_URI, outputUri.toString())
                            }
                        )
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_AUDIO_URI = "extra_audio_uri"
    }
}
