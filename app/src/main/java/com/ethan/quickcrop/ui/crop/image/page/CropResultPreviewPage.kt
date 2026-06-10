package com.ethan.quickcrop.ui.crop.image.page

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethan.quickcrop.MainActivity
import com.ethan.quickcrop.R
import com.ethan.quickcrop.core.image.ImagePreviewDecoder
import com.ethan.quickcrop.extension.finishActivity
import com.ethan.quickcrop.ui.media.MediaPickActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "CropResultPreviewPage"
private const val OUTPUT_QUALITY = 95

@Composable
fun CropResultPreviewPage(imageUri: Uri?) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var editorStep by remember { mutableStateOf(ImageEditorStep.Rotate) }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(ImageFilterOption.original()) }
    var adjustments by remember { mutableStateOf(ImageAdjustments()) }
    var isExporting by remember { mutableStateOf(false) }
    var exportedUri by remember { mutableStateOf<Uri?>(null) }

    // 从裁剪缓存文件读取结果图，预览页按屏幕安全尺寸采样，避免超大结果图绘制崩溃。
    val bitmap by produceState<Bitmap?>(initialValue = null, imageUri) {
        value = imageUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    ImagePreviewDecoder.decode(context = context.applicationContext, uri = uri)
                }.onFailure { throwable ->
                    Log.e(TAG, "读取裁剪结果失败: $uri", throwable)
                }.getOrNull()
            }
        }
    }

    fun exportImage() {
        val source = imageUri
        if (source == null) {
            Toast.makeText(context, "图片来源为空，无法导出", Toast.LENGTH_SHORT).show()
            return
        }
        isExporting = true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                exportEditedImage(
                    context = context.applicationContext,
                    sourceUri = source,
                    rotationDegrees = rotationDegrees,
                    flipHorizontal = flipHorizontal,
                    flipVertical = flipVertical,
                    filterOption = selectedFilter,
                    adjustments = adjustments
                )
            }
            isExporting = false
            result.onSuccess { uri ->
                Log.d(TAG, "图片导出成功: $uri")
                exportedUri = uri
                editorStep = ImageEditorStep.Success
            }.onFailure { throwable ->
                Log.e(TAG, "图片导出失败", throwable)
                Toast.makeText(context, "导出失败，请稍后重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun goBack() {
        when (editorStep) {
            ImageEditorStep.Rotate -> context.finishActivity()
            ImageEditorStep.Filter -> editorStep = ImageEditorStep.Rotate
            ImageEditorStep.Adjust -> editorStep = ImageEditorStep.Filter
            ImageEditorStep.Success -> context.finishActivity()
        }
    }

    BackHandler(true) {
        goBack()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0C0C0F)).statusBarsPadding()) {
        ImageEditorTopBar(
            title = editorStep.title,
            actionText = when {
                editorStep == ImageEditorStep.Adjust && isExporting -> "导出中"
                editorStep == ImageEditorStep.Adjust -> "导出"
                editorStep == ImageEditorStep.Success -> ""
                else -> "下一步"
            },
            actionProminent = editorStep == ImageEditorStep.Adjust,
            actionEnabled = editorStep != ImageEditorStep.Success && !isExporting,
            onBack = { goBack() },
            onAction = {
                when (editorStep) {
                    ImageEditorStep.Rotate -> editorStep = ImageEditorStep.Filter
                    ImageEditorStep.Filter -> editorStep = ImageEditorStep.Adjust
                    ImageEditorStep.Adjust -> exportImage()
                    ImageEditorStep.Success -> Unit
                }
            }
        )

        StepIndicator(activeIndex = editorStep.activeStepIndex)

        Box(modifier = Modifier.weight(1f)) {
            when (editorStep) {
                ImageEditorStep.Rotate -> RotateEditorContent(
                    bitmap = bitmap,
                    rotationDegrees = rotationDegrees,
                    flipHorizontal = flipHorizontal,
                    flipVertical = flipVertical,
                    selectedFilter = selectedFilter,
                    adjustments = adjustments,
                    onRotateLeft = {
                        rotationDegrees = (rotationDegrees + 270) % 360
                        Log.d(TAG, "左旋图片，当前角度: $rotationDegrees")
                    },
                    onRotateRight = {
                        rotationDegrees = (rotationDegrees + 90) % 360
                        Log.d(TAG, "右旋图片，当前角度: $rotationDegrees")
                    },
                    onRotateHalf = {
                        rotationDegrees = (rotationDegrees + 180) % 360
                        Log.d(TAG, "旋转 180 度，当前角度: $rotationDegrees")
                    },
                    onFlipHorizontal = {
                        flipHorizontal = !flipHorizontal
                        Log.d(TAG, "水平翻转: $flipHorizontal")
                    },
                    onFlipVertical = {
                        flipVertical = !flipVertical
                        Log.d(TAG, "垂直翻转: $flipVertical")
                    },
                    onNext = { editorStep = ImageEditorStep.Filter }
                )
                ImageEditorStep.Filter -> FilterEditorContent(
                    bitmap = bitmap,
                    rotationDegrees = rotationDegrees,
                    flipHorizontal = flipHorizontal,
                    flipVertical = flipVertical,
                    selectedFilter = selectedFilter,
                    adjustments = adjustments,
                    onFilterClick = { filter ->
                        selectedFilter = filter
                        Log.d(TAG, "切换滤镜: ${filter.name}")
                    },
                    onNext = { editorStep = ImageEditorStep.Adjust }
                )
                ImageEditorStep.Adjust -> AdjustEditorContent(
                    bitmap = bitmap,
                    rotationDegrees = rotationDegrees,
                    flipHorizontal = flipHorizontal,
                    flipVertical = flipVertical,
                    selectedFilter = selectedFilter,
                    adjustments = adjustments,
                    onAdjustmentsChanged = { nextAdjustments ->
                        adjustments = nextAdjustments
                        Log.d(TAG, "基础调整变更: $nextAdjustments")
                    },
                    onExport = { exportImage() }
                )
                ImageEditorStep.Success -> ExportSuccessPanel(
                    exportedUri = exportedUri,
                    fallbackBitmap = bitmap,
                    rotationDegrees = rotationDegrees,
                    flipHorizontal = flipHorizontal,
                    flipVertical = flipVertical,
                    selectedFilter = selectedFilter,
                    adjustments = adjustments,
                    onBackHome = {
                        context.startActivity(
                            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                        context.finishActivity()
                    },
                    onContinueEdit = {
                        MediaPickActivity.launch(context)
                        context.finishActivity()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ImageEditorTopBar(
    title: String,
    actionText: String,
    actionProminent: Boolean,
    actionEnabled: Boolean,
    onBack: () -> Unit,
    onAction: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp)) {
        Image(
            painter = painterResource(R.drawable.svg_icon_back),
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterStart).clickable { onBack() }
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
        if (actionText.isNotEmpty()) {
            Text(
                text = actionText,
                color = if (actionEnabled) Color.White else Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        when {
                            !actionEnabled -> Color.White.copy(alpha = 0.12f)
                            actionProminent -> Color(0xFF7C3AED)
                            else -> Color.White.copy(alpha = 0.2f)
                        }
                    )
                    .clickable(enabled = actionEnabled) { onAction() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun StepIndicator(activeIndex: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(6) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = if (index == activeIndex) 16.dp else 6.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (index == activeIndex) Color(0xFF7C3AED) else Color(0xFF3F3F46))
            )
        }
    }
}

@Composable
private fun RotateEditorContent(
    bitmap: Bitmap?,
    rotationDegrees: Int,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    selectedFilter: ImageFilterOption,
    adjustments: ImageAdjustments,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onRotateHalf: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        PreviewPanel(
            bitmap = bitmap,
            rotationDegrees = rotationDegrees,
            flipHorizontal = flipHorizontal,
            flipVertical = flipVertical,
            selectedFilter = selectedFilter,
            adjustments = adjustments,
            modifier = Modifier.weight(1f)
        )
        if (rotationDegrees != 0) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "已旋转 ${rotationDegrees}°",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF7C3AED))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            RoundToolButton(iconRes = R.drawable.fa_rotate_left, label = "左旋 90°", onClick = onRotateLeft)
            RoundToolButton(iconRes = R.drawable.fa_rotate_right, label = "右旋 90°", onClick = onRotateRight)
            RoundToolButton(iconRes = R.drawable.fa_refresh, label = "旋转 180°", onClick = onRotateHalf)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            RoundToolButton(iconRes = R.drawable.fa_arrows_left_right, label = "水平翻转", selected = flipHorizontal, onClick = onFlipHorizontal)
            RoundToolButton(iconRes = R.drawable.fa_arrows_up_down, label = "垂直翻转", selected = flipVertical, onClick = onFlipVertical)
        }
        Spacer(modifier = Modifier.height(14.dp))
        PrimaryBottomButton(text = "下一步：滤镜 →", onClick = onNext)
    }
}

@Composable
private fun FilterEditorContent(
    bitmap: Bitmap?,
    rotationDegrees: Int,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    selectedFilter: ImageFilterOption,
    adjustments: ImageAdjustments,
    onFilterClick: (ImageFilterOption) -> Unit,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(bottom = 24.dp)) {
        PreviewPanel(
            bitmap = bitmap,
            rotationDegrees = rotationDegrees,
            flipHorizontal = flipHorizontal,
            flipVertical = flipVertical,
            selectedFilter = selectedFilter,
            adjustments = adjustments,
            modifier = Modifier.fillMaxWidth().height(192.dp).padding(horizontal = 16.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            val filters = ImageFilterOption.defaults()
            items(filters.size) { index ->
                FilterCard(
                    bitmap = bitmap,
                    option = filters[index],
                    selected = filters[index].name == selectedFilter.name,
                    onClick = { onFilterClick(filters[index]) }
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PrimaryBottomButton(text = "下一步：调整 →", onClick = onNext)
        }
    }
}

@Composable
private fun AdjustEditorContent(
    bitmap: Bitmap?,
    rotationDegrees: Int,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    selectedFilter: ImageFilterOption,
    adjustments: ImageAdjustments,
    onAdjustmentsChanged: (ImageAdjustments) -> Unit,
    onExport: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        PreviewPanel(
            bitmap = bitmap,
            rotationDegrees = rotationDegrees,
            flipHorizontal = flipHorizontal,
            flipVertical = flipVertical,
            selectedFilter = selectedFilter,
            adjustments = adjustments,
            modifier = Modifier.fillMaxWidth().height(208.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        AdjustmentSlider(R.drawable.fa_sun, "亮度", adjustments.brightness) {
            onAdjustmentsChanged(adjustments.copy(brightness = it))
        }
        AdjustmentSlider(R.drawable.fa_adjust, "对比度", adjustments.contrast) {
            onAdjustmentsChanged(adjustments.copy(contrast = it))
        }
        AdjustmentSlider(R.drawable.fa_palette, "饱和度", adjustments.saturation) {
            onAdjustmentsChanged(adjustments.copy(saturation = it))
        }
        AdjustmentSlider(R.drawable.fa_temperature_half, "色温", adjustments.temperature) {
            onAdjustmentsChanged(adjustments.copy(temperature = it))
        }
        AdjustmentSlider(R.drawable.fa_bolt, "清晰度", adjustments.clarity) {
            onAdjustmentsChanged(adjustments.copy(clarity = it))
        }
        Spacer(modifier = Modifier.height(10.dp))
        PrimaryBottomButton(text = "导出图片", onClick = onExport)
    }
}

@Composable
private fun PreviewPanel(
    bitmap: Bitmap?,
    rotationDegrees: Int,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    selectedFilter: ImageFilterOption,
    adjustments: ImageAdjustments,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .graphicsLayer {
                        rotationZ = rotationDegrees.toFloat()
                        scaleX = if (flipHorizontal) -1f else 1f
                        scaleY = if (flipVertical) -1f else 1f
                    },
                contentScale = ContentScale.Fit,
                colorFilter = buildComposeColorMatrix(selectedFilter, adjustments)
                    ?.let { ColorFilter.colorMatrix(it) }
            )
        } else {
            Text(text = "图片加载中...", color = Color.White)
        }
    }
}

@Composable
private fun RoundToolButton(
    iconRes: Int,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.width(86.dp).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (selected) Color(0xFF7C3AED) else Color(0xFF1F2937)),
            contentAlignment = Alignment.Center
        ) {
            FaIcon(iconRes = iconRes, tint = Color(0xFFD1D5DB), modifier = Modifier.size(20.dp))
        }
        Text(text = label, color = Color(0xFF9CA3AF), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun FilterCard(bitmap: Bitmap?, option: ImageFilterOption, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1F2937))
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) Color(0xFF7C3AED) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = option.composeMatrix?.let { ColorFilter.colorMatrix(it) }
                )
            } else {
                Text(text = option.shortName, color = Color.White, fontSize = 16.sp)
            }
        }
        Text(
            text = option.name,
            color = if (selected) Color(0xFFC084FC) else Color(0xFF9CA3AF),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun AdjustmentSlider(iconRes: Int, title: String, value: Int, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FaIcon(iconRes = iconRes, tint = adjustmentIconColor(iconRes), modifier = Modifier.size(15.dp))
                Text(text = title, color = Color(0xFFE5E7EB), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
            }
            Text(text = if (value > 0) "+$value" else value.toString(), color = Color(0xFF9CA3AF), fontSize = 12.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = -50f..50f,
            modifier = Modifier.height(32.dp)
        )
    }
}

@Composable
private fun FaIcon(iconRes: Int, tint: Color, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint)
    )
}

private fun adjustmentIconColor(iconRes: Int): Color {
    return when (iconRes) {
        R.drawable.fa_sun -> Color(0xFFFACC15)
        R.drawable.fa_palette -> Color(0xFFF472B6)
        R.drawable.fa_temperature_half -> Color(0xFFFB923C)
        R.drawable.fa_bolt -> Color(0xFF60A5FA)
        else -> Color(0xFFD1D5DB)
    }
}

@Composable
private fun PrimaryBottomButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ExportSuccessPanel(
    exportedUri: Uri?,
    fallbackBitmap: Bitmap?,
    rotationDegrees: Int,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    selectedFilter: ImageFilterOption,
    adjustments: ImageAdjustments,
    onBackHome: () -> Unit,
    onContinueEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 成功页优先展示真正写入相册的结果图；读取失败时回退到当前编辑预览，避免成功页空白。
    val exportedBitmap by produceState<Bitmap?>(initialValue = null, exportedUri) {
        value = exportedUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    ImagePreviewDecoder.decode(context = context.applicationContext, uri = uri)
                }.onFailure { throwable ->
                    Log.e(TAG, "读取导出结果失败: $uri", throwable)
                }.getOrNull()
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF18181B)),
            contentAlignment = Alignment.Center
        ) {
            if (exportedBitmap != null) {
                Image(
                    bitmap = exportedBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                PreviewPanel(
                    bitmap = fallbackBitmap,
                    rotationDegrees = rotationDegrees,
                    flipHorizontal = flipHorizontal,
                    flipVertical = flipVertical,
                    selectedFilter = selectedFilter,
                    adjustments = adjustments,
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(22.dp))
        FaIcon(iconRes = R.drawable.fa_check, tint = Color.White, modifier = Modifier.size(34.dp))
        Text(text = "导出成功！", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Text(text = "图片已保存到相册", color = Color(0xFF9CA3AF), fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 26.dp))
        PrimaryBottomButton(text = "返回首页", onClick = onBackHome)
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1F2937))
                .clickable { onContinueEdit() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "继续编辑", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private enum class ImageEditorStep(val title: String, val activeStepIndex: Int) {
    Rotate("旋转 & 翻转", 2),
    Filter("滤镜", 3),
    Adjust("基础调整", 4),
    Success("导出成功", 5)
}

private data class ImageFilterOption(
    val name: String,
    val shortName: String,
    val composeMatrix: ColorMatrix?,
    val androidMatrix: android.graphics.ColorMatrix?
) {
    companion object {
        fun original() = ImageFilterOption("原图", "原", null, null)

        fun defaults(): List<ImageFilterOption> {
            return listOf(
                original(),
                saturationFilter("黑白", "黑", 0f),
                colorScaleFilter("复古", "旧", red = 1.12f, green = 0.95f, blue = 0.72f),
                colorScaleFilter("鲜亮", "亮", red = 1.12f, green = 1.12f, blue = 1.04f),
                colorScaleFilter("暖调", "暖", red = 1.16f, green = 1.04f, blue = 0.88f),
                colorScaleFilter("冷调", "冷", red = 0.9f, green = 1.02f, blue = 1.18f),
                colorScaleFilter("高对比", "高", red = 1.18f, green = 1.18f, blue = 1.18f)
            )
        }

        private fun saturationFilter(name: String, shortName: String, saturation: Float): ImageFilterOption {
            val compose = ColorMatrix().apply { setToSaturation(saturation) }
            val android = android.graphics.ColorMatrix().apply { setSaturation(saturation) }
            return ImageFilterOption(name, shortName, compose, android)
        }

        private fun colorScaleFilter(name: String, shortName: String, red: Float, green: Float, blue: Float): ImageFilterOption {
            val values = floatArrayOf(
                red, 0f, 0f, 0f, 0f,
                0f, green, 0f, 0f, 0f,
                0f, 0f, blue, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            return ImageFilterOption(
                name = name,
                shortName = shortName,
                composeMatrix = ColorMatrix(values),
                androidMatrix = android.graphics.ColorMatrix(values)
            )
        }
    }
}

private data class ImageAdjustments(
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val temperature: Int = 0,
    val clarity: Int = 0
)

private fun exportEditedImage(
    context: Context,
    sourceUri: Uri,
    rotationDegrees: Int,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    filterOption: ImageFilterOption,
    adjustments: ImageAdjustments
): Result<Uri> {
    return runCatching {
        val sourceBitmap = decodeBitmap(context, sourceUri)
        try {
            val outputBitmap = renderEditedBitmap(
                sourceBitmap = sourceBitmap,
                rotationDegrees = rotationDegrees,
                flipHorizontal = flipHorizontal,
                flipVertical = flipVertical,
                filterOption = filterOption,
                adjustments = adjustments
            )
            try {
                saveBitmapToGallery(context, outputBitmap)
            } finally {
                if (outputBitmap !== sourceBitmap) {
                    outputBitmap.recycle()
                }
            }
        } finally {
            sourceBitmap.recycle()
        }
    }.onFailure { throwable ->
        Log.e(TAG, "保存编辑结果失败", throwable)
    }
}

private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
    return requireNotNull(context.contentResolver.openInputStream(uri)?.use { inputStream ->
        BitmapFactory.decodeStream(inputStream)
    }) {
        "导出图片解码失败: $uri"
    }
}

private fun renderEditedBitmap(
    sourceBitmap: Bitmap,
    rotationDegrees: Int,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    filterOption: ImageFilterOption,
    adjustments: ImageAdjustments
): Bitmap {
    val matrix = Matrix().apply {
        postScale(if (flipHorizontal) -1f else 1f, if (flipVertical) -1f else 1f, sourceBitmap.width / 2f, sourceBitmap.height / 2f)
        postRotate(rotationDegrees.toFloat(), sourceBitmap.width / 2f, sourceBitmap.height / 2f)
    }
    val transformedBitmap = if (rotationDegrees == 0 && !flipHorizontal && !flipVertical) {
        sourceBitmap
    } else {
        // 旋转和翻转都在导出阶段重新渲染，保证最终文件与预览一致。
        Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, matrix, true)
    }

    val colorMatrix = buildAndroidColorMatrix(filterOption, adjustments)
    if (colorMatrix == null) {
        return transformedBitmap
    }

    val filteredBitmap = Bitmap.createBitmap(transformedBitmap.width, transformedBitmap.height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(colorMatrix)
    }
    Canvas(filteredBitmap).drawBitmap(transformedBitmap, 0f, 0f, paint)
    if (transformedBitmap !== sourceBitmap) {
        transformedBitmap.recycle()
    }
    return filteredBitmap
}

private fun buildComposeColorMatrix(filterOption: ImageFilterOption, adjustments: ImageAdjustments): ColorMatrix? {
    val hasAdjustments = adjustments != ImageAdjustments()
    if (filterOption.composeMatrix == null && !hasAdjustments) {
        return null
    }

    val matrix = filterOption.composeMatrix?.let { ColorMatrix(it.values.copyOf()) } ?: ColorMatrix()
    if (adjustments.saturation != 0) {
        matrix *= ColorMatrix().apply { setToSaturation(1f + adjustments.saturation / 100f) }
    }
    if (adjustments.brightness != 0 || adjustments.contrast != 0 || adjustments.temperature != 0) {
        matrix *= buildComposeToneMatrix(adjustments)
    }
    return matrix
}

private fun buildComposeToneMatrix(adjustments: ImageAdjustments): ColorMatrix {
    val contrastScale = 1f + adjustments.contrast / 100f
    val brightnessOffset = adjustments.brightness * 2.55f
    val contrastOffset = 255f * (1f - contrastScale) / 2f
    val temperatureScale = adjustments.temperature / 100f
    val redScale = 1f + temperatureScale * 0.18f
    val blueScale = 1f - temperatureScale * 0.18f
    val offset = brightnessOffset + contrastOffset
    return ColorMatrix(
        floatArrayOf(
            contrastScale * redScale, 0f, 0f, 0f, offset,
            0f, contrastScale, 0f, 0f, offset,
            0f, 0f, contrastScale * blueScale, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

private fun buildAndroidColorMatrix(
    filterOption: ImageFilterOption,
    adjustments: ImageAdjustments
): android.graphics.ColorMatrix? {
    val hasAdjustments = adjustments != ImageAdjustments()
    if (filterOption.androidMatrix == null && !hasAdjustments) {
        return null
    }

    val matrix = android.graphics.ColorMatrix()
    filterOption.androidMatrix?.let { matrix.postConcat(it) }
    if (adjustments.saturation != 0) {
        matrix.postConcat(android.graphics.ColorMatrix().apply {
            setSaturation(1f + adjustments.saturation / 100f)
        })
    }
    if (adjustments.brightness != 0 || adjustments.contrast != 0 || adjustments.temperature != 0) {
        matrix.postConcat(buildAndroidToneMatrix(adjustments))
    }
    return matrix
}

private fun buildAndroidToneMatrix(adjustments: ImageAdjustments): android.graphics.ColorMatrix {
    val contrastScale = 1f + adjustments.contrast / 100f
    val brightnessOffset = adjustments.brightness * 2.55f
    val contrastOffset = 255f * (1f - contrastScale) / 2f
    val temperatureScale = adjustments.temperature / 100f
    val redScale = 1f + temperatureScale * 0.18f
    val blueScale = 1f - temperatureScale * 0.18f
    val offset = brightnessOffset + contrastOffset
    return android.graphics.ColorMatrix(
        floatArrayOf(
            contrastScale * redScale, 0f, 0f, 0f, offset,
            0f, contrastScale, 0f, 0f, offset,
            0f, 0f, contrastScale * blueScale, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri {
    val resolver = context.contentResolver
    val displayName = "QuickCrop_${System.currentTimeMillis()}.jpg"
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.WIDTH, bitmap.width)
        put(MediaStore.Images.Media.HEIGHT, bitmap.height)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/QuickCrop")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val outputUri = requireNotNull(resolver.insert(collection, values)) {
        "创建相册文件失败"
    }
    var completed = false
    try {
        resolver.openOutputStream(outputUri)?.use { outputStream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, OUTPUT_QUALITY, outputStream)) {
                "图片压缩写入失败"
            }
        } ?: error("打开相册输出流失败")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(outputUri, values, null, null)
        }
        completed = true
        return outputUri
    } finally {
        if (!completed) {
            // 写入失败时清理半成品，避免相册中出现损坏条目。
            runCatching { resolver.delete(outputUri, null, null) }
                .onFailure { throwable -> Log.w(TAG, "清理导出失败文件失败: $outputUri", throwable) }
        }
    }
}
