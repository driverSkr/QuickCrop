package com.ethan.quickcrop.ui.crop.image.page

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethan.quickcrop.R
import com.ethan.quickcrop.core.image.ImageCropProcessor
import com.ethan.quickcrop.core.image.ImageCropRequest
import com.ethan.quickcrop.core.image.ImagePreviewDecoder
import com.ethan.quickcrop.extension.finishActivity
import com.ethan.quickcrop.ui.crop.image.CropResultPreviewActivity
import com.ethan.quickcrop.ui.crop.image.view.ResizableCropBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "CropImagePage"

@Composable
fun CropImagePage(sourceUri: Uri?) {
    val context = LocalContext.current
    // 裁剪页只解码屏幕预览图；真正导出时会重新读取 sourceUri，避免预览图影响输出质量。
    val bitmap by produceState<Bitmap?>(initialValue = null, sourceUri) {
        value = sourceUri?.let { uri ->
            withContext(Dispatchers.IO) {
                ImagePreviewDecoder.decode(context = context.applicationContext, uri = uri)
            }
        }
    }
    val aspectRatioList = listOf("原始", "1:1", "16:9", "9:16", "4:3", "自由")
    var selectedAspectRatio by remember { mutableStateOf(aspectRatioList[0]) }
    // 防止重复点击裁剪按钮触发多次导出任务。
    var isCropping by remember { mutableStateOf(false) }
    // 当前裁剪框在 Compose 画布坐标系中的位置，导出时会映射回原图像素坐标。
    var currentCropRect by remember { mutableStateOf(Rect.Zero) }
    var imageContainerSize by remember { mutableStateOf(IntSize.Zero) }
    val imageBounds = remember(bitmap, imageContainerSize) {
        // Image 使用 ContentScale.Fit 显示，真实图片区域通常小于容器；裁剪框必须限制在这块区域内。
        calculateFitImageBounds(bitmap = bitmap, containerSize = imageContainerSize)
    }
    val cropAspectRatio = remember(selectedAspectRatio, bitmap) {
        // 把底部比例文案转换成裁剪框需要的宽高比，null 表示自由比例。
        selectedAspectRatio.toCropAspectRatio(bitmap)
    }
    val coroutineScope = rememberCoroutineScope()
    fun handleCropClick() {
        if (sourceUri == null) {
            Toast.makeText(context, "图片来源为空，无法裁剪", Toast.LENGTH_SHORT).show()
            return
        }
        val request = ImageCropRequest(
            sourceUri = sourceUri,
            baseImageBounds = imageBounds,
            // 当前裁剪页不支持图片缩放/平移，因此导出映射使用原始显示区域。
            imageScale = 1f,
            imageOffset = Offset.Zero,
            cropRect = currentCropRect
        )
        isCropping = true
        coroutineScope.launch {
            Log.d(TAG, "开始裁剪，裁剪框: $currentCropRect")
            val result = withContext(Dispatchers.IO) {
                // 裁剪导出包含原图解码、Canvas 渲染和文件写入，必须放到 IO 线程。
                ImageCropProcessor.crop(context.applicationContext, request)
            }
            isCropping = false
            result.onSuccess { outputUri ->
                // 裁剪成功后只进入结果预览页，并关闭当前裁剪页，避免返回到已完成的编辑状态。
                context.startActivity(
                    Intent(context, CropResultPreviewActivity::class.java).apply {
                        putExtra(CropResultPreviewActivity.EXTRA_IMAGE_URI, outputUri.toString())
                    }
                )
                context.finishActivity()
            }.onFailure { throwable ->
                Log.e(TAG, "裁剪失败", throwable)
                Toast.makeText(context, "裁剪失败，请稍后重试", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val canCrop = !isCropping && sourceUri != null && !currentCropRect.isEmpty

    BackHandler(true){}
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp)) {
            Image(painter = painterResource(R.drawable.svg_icon_back), contentDescription = null, modifier = Modifier.align(Alignment.CenterStart).clickable{
                context.finishActivity()
            })

            Text("裁剪图片", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            CropActionButton(
                enabled = canCrop,
                modifier = Modifier.align(Alignment.CenterEnd),
                onClick = { handleCropClick() }
            )
        }
        StepIndicator(activeIndex = 1)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged {
                    imageContainerSize = it
                },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(text = "图片加载中...")
            }

            ResizableCropBox(
                modifier = Modifier.fillMaxSize(),
                cropBounds = imageBounds,
                aspectRatio = cropAspectRatio,
                onCropRectChanged = { cropRect ->
                    // 记录当前裁剪框，后续导出图片时需要用它换算原图坐标。
                    currentCropRect = cropRect
                },
                onCropRectUserChanged = { cropRect ->
                    Log.d(TAG, "用户调整裁剪框: $cropRect")
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 底部操作区叠加系统导航栏安全距离，避免按钮被手势条或三键导航遮挡。
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp)
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(aspectRatioList.size) { index ->
                    AspectRatioItem(aspectRatio = aspectRatioList[index], isSelected = aspectRatioList[index] == selectedAspectRatio, onClick = {
                        val nextAspectRatio = aspectRatioList[index]
                        if (nextAspectRatio != selectedAspectRatio) {
                            // 比例切换会重置裁剪框，也属于用户主动裁剪设置变更。
                            Log.d(TAG, "用户切换裁剪比例: $selectedAspectRatio -> $nextAspectRatio")
                            selectedAspectRatio = nextAspectRatio
                        }
                    })
                }
            }
            CropApplyButton(enabled = canCrop, onClick = { handleCropClick() })
        }
    }
}

private fun calculateFitImageBounds(bitmap: Bitmap?, containerSize: IntSize): Rect {
    if (bitmap == null || containerSize.width <= 0 || containerSize.height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) {
        return Rect.Zero
    }

    val containerWidth = containerSize.width.toFloat()
    val containerHeight = containerSize.height.toFloat()
    val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
    val containerRatio = containerWidth / containerHeight

    val displayWidth: Float
    val displayHeight: Float
    if (containerRatio > imageRatio) {
        // 容器更宽时，图片高度贴满容器，高度决定显示尺寸。
        displayHeight = containerHeight
        displayWidth = displayHeight * imageRatio
    } else {
        // 容器更窄时，图片宽度贴满容器，宽度决定显示尺寸。
        displayWidth = containerWidth
        displayHeight = displayWidth / imageRatio
    }

    // 图片居中显示，因此需要计算上下或左右留白，裁剪坐标也以这个区域为准。
    val left = (containerWidth - displayWidth) / 2f
    val top = (containerHeight - displayHeight) / 2f
    return Rect(
        left = left,
        top = top,
        right = left + displayWidth,
        bottom = top + displayHeight
    )
}

private fun String.toCropAspectRatio(bitmap: Bitmap?): Float? {
    return when (this) {
        "原始" -> bitmap?.let { image ->
            if (image.height > 0) {
                image.width.toFloat() / image.height.toFloat()
            } else {
                null
            }
        }
        "自由" -> null
        else -> {
            val width = substringBefore(":").toFloatOrNull()
            val height = substringAfter(":").toFloatOrNull()
            if (width != null && height != null && height > 0f) {
                width / height
            } else {
                null
            }
        }
    }
}

@Composable
private fun StepIndicator(activeIndex: Int) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(6) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = if (index == activeIndex) 16.dp else 6.dp, height = 6.dp)
                    .background(
                        color = if (index == activeIndex) Color(0xFF7C3AED) else Color(0xFF3F3F46),
                        shape = RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

@Composable
private fun AspectRatioItem(aspectRatio: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier
        .clickable{ onClick() }
        .background(color = if (isSelected) Color(0xFF374151) else Color(0xFF1F2937), shape = RoundedCornerShape(16.dp))
        .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text = aspectRatio, fontSize = 12.sp, lineHeight = 12.sp, color = if (isSelected) Color.White else Color(0xFF9CA3AF), modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun CropApplyButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .height(48.dp)
            .background(color = if (enabled) Color.White else Color.White.copy(alpha = 0.28f), shape = RoundedCornerShape(24.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = "应用裁剪", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (enabled) Color.Black else Color.White.copy(alpha = 0.5f))
    }
}

@Composable
fun CropActionButton(enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val backgroundColor = if (enabled) {
        Color.White.copy(alpha = 0.2f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }
    val textColor = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .background(color = backgroundColor, shape = RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "裁剪", fontSize = 14.sp, lineHeight = 14.sp, color = textColor)
    }
}
