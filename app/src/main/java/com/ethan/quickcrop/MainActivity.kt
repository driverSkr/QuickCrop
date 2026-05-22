package com.ethan.quickcrop

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.ethan.base.BaseActivity
import com.ethan.quickcrop.ui.media.MediaPickActivity
import com.ethan.quickcrop.ui.theme.QuickCropTheme

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuickCropTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Button(onClick = {
                        MediaPickActivity.launch(this@MainActivity)
                    }) {
                        Text(text = "Open MediaPickActivity")
                    }
                }
            }
        }
    }
}
