package com.ethan.quickcrop.ui.edit.image.page

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethan.quickcrop.R
import com.ethan.quickcrop.core.image.ImagePreviewDecoder
import com.ethan.quickcrop.extension.finishActivity
import com.ethan.quickcrop.ui.crop.image.view.ResizableCropBox
import com.ethan.quickcrop.utils.EditImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "EditImagePage"

@Composable
fun EditImagePage(sourceUri: Uri?) {
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
    // 底部工具栏当前只负责模式选择，具体滤镜/调节面板会在后续版本接入。
    var selectedTool by remember { mutableStateOf(EditImageTool.Crop) }
    // 当前裁剪框在 Compose 画布坐标系中的位置，导出时会映射回原图像素坐标。
    var currentCropRect by remember { mutableStateOf(Rect.Zero) }
    var imageContainerSize by remember { mutableStateOf(IntSize.Zero) }
    val imageBounds = remember(bitmap, imageContainerSize) {
        // Image 使用 ContentScale.Fit 显示，真实图片区域通常小于容器；裁剪框必须限制在这块区域内。
        EditImageUtils.calculateFitImageBounds(bitmap = bitmap, containerSize = imageContainerSize)
    }
    val cropAspectRatio = remember(selectedAspectRatio, bitmap) {
        // 把底部比例文案转换成裁剪框需要的宽高比，null 表示自由比例。
        selectedAspectRatio.toCropAspectRatio(bitmap)
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp)) {
            Image(painter = painterResource(R.drawable.svg_icon_back), contentDescription = null, modifier = Modifier.align(Alignment.CenterStart).clickable{
                context.finishActivity()
            })
        }
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

        EditImageBottomToolbar(
            selectedTool = selectedTool,
            onToolClick = { nextTool ->
                if (nextTool != selectedTool) {
                    // 记录模式切换，便于后续接入真实编辑功能时排查状态流转。
                    Log.d(TAG, "切换图片编辑工具: ${selectedTool.label} -> ${nextTool.label}")
                    selectedTool = nextTool
                }
            }
        )
    }
}

private enum class EditImageTool(
    val label: String,
    val iconRes: Int
) {
    Crop(label = "裁剪", iconRes = R.drawable.fa_crop),
    Filter(label = "滤镜", iconRes = R.drawable.fa_palette),
    Adjust(label = "调节", iconRes = R.drawable.fa_adjust)
}

@Composable
private fun EditImageBottomToolbar(
    selectedTool: EditImageTool,
    onToolClick: (EditImageTool) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 底部工具栏需要避开系统导航栏，避免按钮被手势条遮挡。
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditImageTool.entries.forEach { tool ->
            EditImageToolButton(
                tool = tool,
                selected = tool == selectedTool,
                modifier = Modifier.weight(1f),
                onClick = { onToolClick(tool) }
            )
        }
    }
}

@Composable
private fun EditImageToolButton(
    tool: EditImageTool,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val targetBackgroundColor = when {
        selected -> Color(0xFF7C3AED)
        isPressed -> Color(0xFF273244)
        else -> Color.Transparent
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        label = "toolBackgroundColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        label = "toolPressedScale"
    )
    val iconColor = if (selected) Color.White else Color(0xFFD1D5DB)
    val textColor = if (selected) Color.White else Color(0xFF9CA3AF)

    Column(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .height(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(tool.iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(iconColor)
        )
        Text(
            text = tool.label,
            color = textColor,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
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
