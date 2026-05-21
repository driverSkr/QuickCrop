package com.ethan.quickcrop

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ethan.quickcrop.feature.album.AlbumScreen
import com.ethan.quickcrop.feature.image.ImageEditorScreen
import com.ethan.quickcrop.feature.video.VideoEditorScreen

private sealed interface AppScreen {
    data object Album : AppScreen

    data class VideoEditor(val uri: Uri) : AppScreen

    data class ImageEditor(val uri: Uri) : AppScreen
}

@Composable
fun QuickCropApp() {
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Album) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        when (val currentScreen = screen) {
            AppScreen.Album -> {
                AlbumScreen(
                    onVideoClick = { uri ->
                        // 进入视频编辑页时保留当前选中素材，避免再次弹系统选择器。
                        screen = AppScreen.VideoEditor(uri)
                    },
                    onImageClick = { uri ->
                        // 图片编辑页先保留空骨架，后续再补具体能力。
                        screen = AppScreen.ImageEditor(uri)
                    }
                )
            }

            is AppScreen.VideoEditor -> {
                VideoEditorScreen(
                    sourceUri = currentScreen.uri,
                    onBack = {
                        screen = AppScreen.Album
                    }
                )
            }

            is AppScreen.ImageEditor -> {
                ImageEditorScreen(
                    sourceUri = currentScreen.uri,
                    onBack = {
                        screen = AppScreen.Album
                    }
                )
            }
        }
    }
}
