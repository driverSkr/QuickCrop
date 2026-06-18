package com.ethan.quickcrop.ui.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import com.ethan.base.BaseActivity
import com.ethan.quickcrop.ui.settings.page.SettingsPage
import com.ethan.quickcrop.ui.theme.QuickCropTheme

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuickCropTheme {
                SettingsPage()
            }
        }
    }
}