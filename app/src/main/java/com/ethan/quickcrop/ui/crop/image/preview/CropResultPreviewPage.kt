package com.ethan.quickcrop.ui.crop.image.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethan.quickcrop.R
import com.ethan.quickcrop.extension.finishActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "CropResultPreviewPage"

@Composable
fun CropResultPreviewPage(imageUri: Uri?) {
    val context = LocalContext.current
    // 结果预览页的缩放和平移只影响查看效果，不会再参与裁剪导出。
    var imageScale by remember { mutableStateOf(1f) }
    var imageOffset by remember { mutableStateOf(Offset.Zero) }
    // 从裁剪缓存文件读取结果图，预览页不重新处理原图。
    val bitmap by produceState<Bitmap?>(initialValue = null, imageUri) {
        value = imageUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                }.onFailure { throwable ->
                    Log.e(TAG, "读取裁剪结果失败: $uri", throwable)
                }.getOrNull()
            }
        }
    }

    BackHandler(true) {
        context.finishActivity()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
            Image(
                painter = painterResource(R.drawable.svg_icon_back),
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterStart).clickable {
                    context.finishActivity()
                }
            )
            Text(
                text = "预览结果",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.W700,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // 双指缩放限制在 0.5x - 5x；单指拖动通过 pan 累加偏移。
                        val nextScale = (imageScale * zoom).coerceIn(0.5f, 5f)
                        // 预览页只做结果查看，拖动不裁剪坐标，因此直接跟随手势偏移。
                        imageScale = nextScale
                        imageOffset += pan
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // 使用 graphicsLayer 只改变屏幕绘制变换，不重新生成 Bitmap，预览性能更稳。
                            scaleX = imageScale
                            scaleY = imageScale
                            translationX = imageOffset.x
                            translationY = imageOffset.y
                        },
                    contentScale = ContentScale.Fit
                )
            } else {
                // 预览页只负责展示结果；读取失败时给出轻量提示，避免黑屏无反馈。
                Text(text = "图片加载中...", color = Color.White)
            }
        }
    }
}
