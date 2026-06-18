package com.ethan.quickcrop.ui.edit.audio

import android.os.Bundle
import androidx.activity.compose.setContent
import com.ethan.base.BaseActivity
import com.ethan.quickcrop.ui.theme.QuickCropTheme

class AudioEditActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuickCropTheme {

            }
        }
    }
}
