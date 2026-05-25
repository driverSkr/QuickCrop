package com.ethan.quickcrop.ui.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ethan.base.BaseActivity
import com.ethan.quickcrop.ui.crop.image.CropImageActivity
import com.ethan.quickcrop.ui.media.page.MediaPickPage
import com.ethan.quickcrop.ui.theme.QuickCropTheme
import java.io.File

/**
 * QuickCrop 自定义相册入口，只保留选图和跳转裁剪页这条核心链路。
 */
class MediaPickActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuickCropTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0C0C0F)
                ) {
                    MediaPickPage(
                        onClose = { finish() },
                        onImportReady = { importPath -> openCropImagePage(importPath) }
                    )
                }
            }
        }
    }

    private fun openCropImagePage(importPath: String) {
        // 传递导入缓存文件给裁剪页，保留原相册代码“先校验/转码/落缓存，再进入后续流程”的能力。
        val imageUri = Uri.fromFile(File(importPath))
        val started = startActivitySafely(
            Intent(this, CropImageActivity::class.java).apply {
                putExtra(CropImageActivity.EXTRA_IMAGE_URI, imageUri.toString())
            }
        )
        if (started) {
            finish()
        }
    }

    companion object {
        private const val TAG = "MediaPickActivity"

        fun launch(context: Context) {
            val intent = Intent(context, MediaPickActivity::class.java).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            runCatching {
                context.startActivity(intent)
            }.onFailure { throwable ->
                Log.w(TAG, "启动自定义相册失败", throwable)
            }
        }
    }
}
