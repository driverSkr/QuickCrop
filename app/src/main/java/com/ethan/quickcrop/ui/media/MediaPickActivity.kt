package com.ethan.quickcrop.ui.media

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ethan.base.BaseActivity
import com.ethan.quickcrop.ui.edit.video.CropVideoActivity
import com.ethan.quickcrop.ui.edit.image.ImageEditActivity
import com.ethan.quickcrop.ui.media.page.MediaPickPage
import com.ethan.quickcrop.ui.theme.QuickCropTheme
import java.io.File

/**
 * QuickCrop 自定义相册入口，按上游传入的媒体类型展示图片、视频或混合媒体。
 */
class MediaPickActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pickType = MediaPickType.fromValue(getStringExtra(EXTRA_PICK_TYPE))
        setContent {
            QuickCropTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0C0C0F)
                ) {
                    MediaPickPage(
                        pickType = pickType,
                        onClose = { finish() },
                        onImageImportReady = { importPath -> openCropImagePage(importPath) },
                        onVideoPickReady = { videoUri -> openCropVideoPage(videoUri) }
                    )
                }
            }
        }
    }

    private fun openCropImagePage(importPath: String) {
        // 传递导入缓存文件给裁剪页，保留原相册代码“先校验/转码/落缓存，再进入后续流程”的能力。
        val imageUri = Uri.fromFile(File(importPath))
        val started = navigateTo<ImageEditActivity> {
            putExtra(ImageEditActivity.EXTRA_IMAGE_URI, imageUri.toString())
        }
        if (started) {
            finish()
        }
    }

    private fun openCropVideoPage(videoUri: Uri) {
        // 视频模块当前只实现 UI 入口，后续接入剪辑链路时可在这里传递视频 Uri 或缓存路径。
        val started = navigateTo<CropVideoActivity> {
            putExtra(CropVideoActivity.EXTRA_VIDEO_URI, videoUri.toString())
        }
        if (started) {
            finish()
        }
    }

    companion object {
        private const val EXTRA_PICK_TYPE = "extra_pick_type"

        fun launch(context: Context, pickType: MediaPickType = MediaPickType.IMAGE) {
            BaseActivity.navigateTo(
                context = context,
                targetActivity = MediaPickActivity::class.java
            ) {
                putExtra(EXTRA_PICK_TYPE, pickType.name)
            }
        }
    }
}
