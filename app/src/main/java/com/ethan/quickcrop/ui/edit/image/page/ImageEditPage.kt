package com.ethan.quickcrop.ui.edit.image.page

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethan.base.BaseActivity
import com.ethan.quickcrop.R
import com.ethan.quickcrop.core.image.ImageEditSaveProcessor
import com.ethan.quickcrop.core.image.ImageEditSaveRequest
import com.ethan.quickcrop.core.image.ImagePreviewDecoder
import com.ethan.quickcrop.core.image.ImageRegionPreviewDecoder
import com.ethan.quickcrop.core.image.ImageRegionTile
import com.ethan.quickcrop.core.image.ImageRegionTileRequest
import com.ethan.quickcrop.custom.ArcValueScale
import com.ethan.quickcrop.custom.ArcValueScaleState
import com.ethan.quickcrop.custom.NumericValueIndicator
import com.ethan.quickcrop.custom.rememberArcValueScaleState
import com.ethan.quickcrop.extension.finishActivity
import com.ethan.quickcrop.ui.edit.image.view.ResizableCropBox
import com.ethan.quickcrop.ui.edit.image.ImageEditResultActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TAG = "EditImagePage"
private const val DEFAULT_ROTATE_ANGLE_LIMIT = 45F
private const val DEFAULT_ADJUSTMENT_LIMIT = 50F
private const val RIGHT_ANGLE_ROTATION_STEP_DEGREES = 90
private const val FULL_ROTATION_DEGREES = 360
private const val MIN_PREVIEW_ZOOM = 0.5F
private const val MAX_PREVIEW_ZOOM = 5F
private const val ORIGINAL_TILE_ZOOM_THRESHOLD = 1.6F
private const val CROP_CORNER_TOUCH_RADIUS = 96F
private const val CROP_EDGE_RESIZE_TOUCH_INSET = 72F

@Composable
fun ImageEditPage(sourceUri: Uri?) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    // 裁剪页只解码屏幕预览图；真正导出时会重新读取 sourceUri，避免预览图影响输出质量。
    val bitmap by produceState<Bitmap?>(initialValue = null, sourceUri) {
        value = sourceUri?.let { uri ->
            withContext(Dispatchers.IO) {
                ImagePreviewDecoder.decode(context = context.applicationContext, uri = uri)
            }
        }
    }
    val aspectRatioList = listOf("自由", "原始", "1:1", "16:9", "9:16", "4:3")
    var selectedAspectRatio by remember { mutableStateOf(aspectRatioList[0]) }
    var selectedCropOrientation by remember { mutableStateOf(CropAspectOrientation.Portrait) }
    var previewZoom by remember { mutableStateOf(1F) }
    var previewPan by remember { mutableStateOf(Offset.Zero) }
    // 底部工具栏负责模式选择，裁剪/滤镜/调节各自保留状态，切换时不重置用户操作。
    var selectedTool by remember { mutableStateOf(EditImageTool.Crop) }
    // 裁剪模式下的图片微调旋转角度，默认范围由通用弧形刻度盘控制。
    val rotateScaleState = rememberArcValueScaleState()
    val rotateAngle = rotateScaleState.currentValue
    // 图片镜像和 90 度旋转作为离散变换，和刻度尺的微调旋转叠加展示。
    var isImageMirrored by remember { mutableStateOf(false) }
    var rightAngleRotationDegrees by remember { mutableStateOf(0) }
    // 当前选中的滤镜会同时作用于页面预览和最终保存结果。
    var selectedFilter by remember { mutableStateOf(ImageFilterOption.original()) }
    // 调节模块复用基础调整的取值范围和矩阵算法，但状态只保存在编辑页内部。
    var selectedAdjustmentType by remember { mutableStateOf(EditImageAdjustmentType.Brightness) }
    var imageAdjustments by remember { mutableStateOf(EditImageAdjustments()) }
    val adjustmentScaleState = rememberArcValueScaleState(
        scaleMin = -DEFAULT_ADJUSTMENT_LIMIT.toInt(),
        scaleMaxLength = (DEFAULT_ADJUSTMENT_LIMIT * 2).toInt()
    )
    // 防止用户连续点击保存造成重复写入相册。
    var isSaving by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    // 当前裁剪框在 Compose 画布坐标系中的位置，导出时会映射回原图像素坐标。
    var currentCropRect by remember { mutableStateOf(Rect.Zero) }
    // 只有用户主动拖动裁剪框后才认为裁剪区域发生编辑，初始化贴图不点亮保存按钮。
    var hasCropUserChanged by remember { mutableStateOf(false) }
    var cropResetSignal by remember { mutableStateOf(0) }
    var imageContainerSize by remember { mutableStateOf(IntSize.Zero) }
    val previewLayout = remember(bitmap, imageContainerSize, rightAngleRotationDegrees) {
        // 预览区域根据 90 度旋转后的视觉宽高比重新计算，避免高度被压缩时图片变形或裁剪框错位。
        calculateAdaptiveImagePreviewLayout(
            bitmap = bitmap,
            containerSize = imageContainerSize,
            rightAngleRotationDegrees = rightAngleRotationDegrees
        )
    }
    val imageBounds = previewLayout.cropBounds
    val imageLayerBounds = previewLayout.imageLayerBounds
    val isCropToolSelected = selectedTool == EditImageTool.Crop
    val imageCoverScale = remember(imageBounds, imageLayerBounds, rightAngleRotationDegrees, rotateAngle) {
        // 任意角度旋转时自动放大图片，保证裁剪框内不会露出透明边角。
        calculateRotationCoverScale(
            cropBounds = imageBounds,
            imageLayerBounds = imageLayerBounds,
            rotationDegrees = rightAngleRotationDegrees.toFloat() + rotateAngle
        )
    }
    val cropAspectRatio = remember(selectedAspectRatio, selectedCropOrientation, bitmap, rightAngleRotationDegrees) {
        // 把底部比例文案转换成裁剪框需要的宽高比，null 表示自由比例。
        selectedAspectRatio.toCropAspectRatio(bitmap, rightAngleRotationDegrees, selectedCropOrientation)
    }
    val previewColorMatrix = remember(selectedFilter, imageAdjustments) {
        buildComposeColorMatrix(selectedFilter, imageAdjustments)
    }
    val regionTileRequest = remember(sourceUri, previewZoom, previewPan, imageContainerSize, imageLayerBounds) {
        buildRegionTileRequest(
            sourceUri = sourceUri,
            zoom = previewZoom,
            pan = previewPan,
            containerSize = imageContainerSize,
            imageLayerBounds = imageLayerBounds
        )
    }
    val regionTile by produceState<ImageRegionTile?>(initialValue = null, regionTileRequest) {
        value = regionTileRequest?.let { request ->
            withContext(Dispatchers.IO) {
                ImageRegionPreviewDecoder.decodeTile(
                    context = context.applicationContext,
                    request = request
                )
            }
        }
    }
    val hasPreviewTransformChanged = abs(previewZoom - 1F) > 0.01F ||
        abs(previewPan.x) > 0.5F ||
        abs(previewPan.y) > 0.5F
    val hasEditOperation = hasCropUserChanged ||
        hasPreviewTransformChanged ||
        abs(rotateAngle) > 0.01F ||
        isImageMirrored ||
        rightAngleRotationDegrees != 0 ||
        selectedAspectRatio != aspectRatioList[0] ||
        selectedCropOrientation != CropAspectOrientation.Portrait ||
        !selectedFilter.isOriginal ||
        imageAdjustments != EditImageAdjustments()
    val canSave = hasEditOperation &&
        !isSaving &&
        sourceUri != null &&
        !currentCropRect.isEmpty &&
        !imageBounds.isEmpty &&
        !imageLayerBounds.isEmpty

    LaunchedEffect(selectedTool) {
        if (selectedTool == EditImageTool.Adjust) {
            // 进入调节模式时同步刻度盘位置，保留当前调节项之前的值。
            adjustmentScaleState.syncCurrentValue(imageAdjustments.valueOf(selectedAdjustmentType).toFloat())
        }
    }

    LaunchedEffect(sourceUri) {
        // 切换图片来源时重置查看缩放，避免上一张图的平移缩放影响新图初始展示。
        previewZoom = 1F
        previewPan = Offset.Zero
    }

    LaunchedEffect(imageContainerSize, imageBounds, currentCropRect, previewZoom) {
        // 缩放/拖动查看时，图片的可见边界必须始终覆盖裁剪框，避免保存时露出空白。
        val minimumZoom = calculateMinimumPreviewZoom(
            imageBounds = imageBounds,
            cropRect = currentCropRect
        )
        val safeZoom = previewZoom.coerceIn(minimumZoom, MAX_PREVIEW_ZOOM)
        if (safeZoom != previewZoom) {
            previewZoom = safeZoom
        }
        previewPan = previewPan.coercePreviewPan(
            zoom = safeZoom,
            imageBounds = imageBounds,
            cropRect = currentCropRect
        )
    }

    fun requestLeavePage() {
        if (isSaving) {
            Toast.makeText(context, "图片正在保存，请稍后", Toast.LENGTH_SHORT).show()
            return
        }
        if (hasEditOperation) {
            // 有未保存操作时，顶部返回和系统侧滑返回都先给用户二次确认。
            showDiscardConfirmDialog = true
            Log.d(TAG, "检测到未保存编辑操作，展示离开确认弹窗")
        } else {
            context.finishActivity()
        }
    }

    BackHandler(enabled = true) {
        if (showDiscardConfirmDialog) {
            showDiscardConfirmDialog = false
            Log.d(TAG, "用户通过返回键关闭离开确认弹窗")
        } else {
            requestLeavePage()
        }
    }

    fun handleResetClick() {
        if (!hasEditOperation) {
            Log.d(TAG, "没有编辑操作，忽略重置")
            return
        }

        // 重置所有编辑状态，并通过 resetSignal 通知裁剪框恢复初始包裹图片的位置。
        rotateScaleState.reset()
        adjustmentScaleState.reset()
        isImageMirrored = false
        rightAngleRotationDegrees = 0
        selectedAspectRatio = aspectRatioList[0]
        selectedCropOrientation = CropAspectOrientation.Portrait
        selectedFilter = ImageFilterOption.original()
        selectedAdjustmentType = EditImageAdjustmentType.Brightness
        imageAdjustments = EditImageAdjustments()
        previewZoom = 1F
        previewPan = Offset.Zero
        hasCropUserChanged = false
        cropResetSignal += 1
        Log.d(TAG, "已重置所有图片编辑操作")
    }

    fun handleSaveClick() {
        val safeSourceUri = sourceUri
        if (safeSourceUri == null) {
            Toast.makeText(context, "图片来源为空，无法保存", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentCropRect.isEmpty || imageBounds.isEmpty || imageLayerBounds.isEmpty) {
            Log.w(TAG, "保存失败，图片或裁剪区域尚未准备完成: cropRect=$currentCropRect, imageBounds=$imageBounds, imageLayerBounds=$imageLayerBounds")
            Toast.makeText(context, "图片尚未准备完成，请稍后重试", Toast.LENGTH_SHORT).show()
            return
        }
        val minimumZoom = calculateMinimumPreviewZoom(
            imageBounds = imageBounds,
            cropRect = currentCropRect
        )
        val safePreviewZoom = previewZoom.coerceIn(minimumZoom, MAX_PREVIEW_ZOOM)
        val safePreviewPan = previewPan.coercePreviewPan(
            zoom = safePreviewZoom,
            imageBounds = imageBounds,
            cropRect = currentCropRect
        )
        val transformedImageBounds = imageBounds.transformByPreviewGesture(
            zoom = safePreviewZoom,
            pan = safePreviewPan
        )
        val transformedImageLayerBounds = imageLayerBounds.transformByPreviewGesture(
            zoom = safePreviewZoom,
            pan = safePreviewPan
        )
        val request = ImageEditSaveRequest(
            sourceUri = safeSourceUri,
            cropRect = currentCropRect,
            // 保存时使用已经叠加查看缩放/平移后的图片边界，保证导出和屏幕预览一致。
            visualImageBounds = transformedImageBounds,
            imageLayerBounds = transformedImageLayerBounds,
            rightAngleRotationDegrees = rightAngleRotationDegrees,
            rotationDegrees = rightAngleRotationDegrees.toFloat() + rotateAngle,
            coverScale = imageCoverScale,
            mirrorHorizontal = isImageMirrored,
            filterColorMatrix = buildAndroidColorMatrix(selectedFilter, imageAdjustments)
        )
        isSaving = true
        coroutineScope.launch {
            Log.d(TAG, "开始保存图片编辑结果: $request")
            val result = withContext(Dispatchers.IO) {
                ImageEditSaveProcessor.saveToGallery(context.applicationContext, request)
            }
            isSaving = false
            result.onSuccess { outputUri ->
                Log.d(TAG, "图片编辑结果保存成功，跳转预览页: $outputUri")
                BaseActivity.navigateTo(
                    context = context,
                    targetActivity = ImageEditResultActivity::class.java
                ) {
                    putExtra(ImageEditResultActivity.EXTRA_IMAGE_URI, outputUri.toString())
                }
            }.onFailure { throwable ->
                Log.e(TAG, "图片编辑结果保存失败", throwable)
                Toast.makeText(context, "保存失败，请稍后重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painter = painterResource(R.drawable.svg_icon_back), contentDescription = null, modifier = Modifier.clickable{
                requestLeavePage()
            })
            if (hasEditOperation) {
                Spacer(modifier = Modifier.width(12.dp))
                Image(
                    painter = painterResource(R.drawable.svg_reset),
                    contentDescription = "重置编辑操作",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            enabled = !isSaving,
                            role = Role.Button,
                            onClick = { handleResetClick() }
                        )
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            EditImageSaveButton(
                enabled = canSave,
                onClick = { handleSaveClick() }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .weight(1f)
                // 图片旋转后可能超出预览区域，这里裁掉溢出，避免遮挡底部控制区。
                .clipToBounds()
                .onSizeChanged {
                    imageContainerSize = it
                }
                .pointerInput(bitmap, imageContainerSize, imageBounds, currentCropRect, isCropToolSelected) {
                    if (bitmap == null || imageContainerSize.width <= 0 || imageContainerSize.height <= 0) {
                        return@pointerInput
                    }
                    awaitEachGesture {
                        var handledMultiTouch = false
                        var handledPreviewGesture = false
                        while (true) {
                            // 在 Initial 阶段优先处理图片层手势；裁剪框边角缩放区域会被保留给裁剪框自己处理。
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount == 0) {
                                if (handledPreviewGesture) {
                                    Log.d(TAG, "结束图片预览拖动/缩放: zoom=$previewZoom, pan=$previewPan")
                                }
                                break
                            }

                            if (pressedCount >= 2) {
                                handledMultiTouch = true
                                handledPreviewGesture = true
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val pan = event.calculatePan()
                                val zoomChange = event.calculateZoom()
                                val oldZoom = previewZoom
                                val minimumZoom = calculateMinimumPreviewZoom(
                                    imageBounds = imageBounds,
                                    cropRect = currentCropRect
                                )
                                val newZoom = (oldZoom * zoomChange).coerceIn(minimumZoom, MAX_PREVIEW_ZOOM)
                                val contentPoint = Offset(
                                    x = (centroid.x - previewPan.x) / oldZoom,
                                    y = (centroid.y - previewPan.y) / oldZoom
                                )
                                val nextPan = Offset(
                                    x = centroid.x - contentPoint.x * newZoom + pan.x,
                                    y = centroid.y - contentPoint.y * newZoom + pan.y
                                )
                                previewZoom = newZoom
                                previewPan = nextPan.coercePreviewPan(
                                    zoom = newZoom,
                                    imageBounds = imageBounds,
                                    cropRect = currentCropRect
                                )
                                // 双指缩放始终属于图片查看层，避免裁剪框跟随手势被误操作。
                                event.changes.forEach { it.consume() }
                            } else if (handledMultiTouch) {
                                // 双指结束后剩下一根手指时，消费掉本轮尾巴，避免突然触发裁剪框拖动。
                                event.changes.forEach { it.consume() }
                            } else {
                                val activeChange = event.changes.firstOrNull { it.pressed } ?: continue
                                val shouldHandlePreviewPan = shouldHandleSingleFingerPreviewPan(
                                    touch = activeChange.position,
                                    cropRect = currentCropRect,
                                    cropBounds = imageBounds,
                                    reserveCropBoxResizeGesture = isCropToolSelected
                                )
                                if (shouldHandlePreviewPan && event.changes.none { it.isConsumed }) {
                                    val pan = event.calculatePan()
                                    if (pan != Offset.Zero) {
                                        val minimumZoom = calculateMinimumPreviewZoom(
                                            imageBounds = imageBounds,
                                            cropRect = currentCropRect
                                        )
                                        val safeZoom = previewZoom.coerceIn(minimumZoom, MAX_PREVIEW_ZOOM)
                                        if (safeZoom != previewZoom) {
                                            previewZoom = safeZoom
                                        }
                                        previewPan = (previewPan + pan).coercePreviewPan(
                                            zoom = safeZoom,
                                            imageBounds = imageBounds,
                                            cropRect = currentCropRect
                                        )
                                        handledPreviewGesture = true
                                        // 单指拖动用于移动图片预览，裁剪框边角区域仍保留给缩放裁剪框。
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        // 查看缩放只作用在图片预览层，裁剪框保持独立覆盖，避免边框和触控范围被放大。
                        transformOrigin = TransformOrigin(0F, 0F)
                        scaleX = previewZoom
                        scaleY = previewZoom
                        translationX = previewPan.x
                        translationY = previewPan.y
                    }
            ) {
                if (bitmap != null && !imageLayerBounds.isEmpty) {
                    val imageLayerWidth = with(density) { imageLayerBounds.width.toDp() }
                    val imageLayerHeight = with(density) { imageLayerBounds.height.toDp() }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(
                                    x = imageLayerBounds.left.roundToInt(),
                                    y = imageLayerBounds.top.roundToInt()
                                )
                            }
                            .size(width = imageLayerWidth, height = imageLayerHeight)
                            .graphicsLayer {
                                // 预览图合并镜像、90 度旋转和刻度尺微调，裁剪框保持在屏幕坐标系中便于继续拖拽。
                                scaleX = if (isImageMirrored) -imageCoverScale else imageCoverScale
                                scaleY = imageCoverScale
                                rotationZ = rightAngleRotationDegrees.toFloat() + rotateAngle
                            }
                    ) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            colorFilter = previewColorMatrix?.let { ColorFilter.colorMatrix(it) }
                        )
                        RegionPreviewTileImage(
                            tile = regionTile,
                            colorFilter = previewColorMatrix?.let { ColorFilter.colorMatrix(it) }
                        )
                    }
                } else {
                    Text(text = "图片加载中...")
                }
            }

            ResizableCropBox(
                modifier = Modifier.fillMaxSize(),
                cropBounds = imageBounds,
                aspectRatio = cropAspectRatio,
                // 编辑页初始裁剪框需要正好包裹图片，比例切换时再按目标比例取图片内最大区域。
                initialCropScale = 1f,
                // 切到滤镜/调节时只隐藏裁剪框，不移除组件，避免丢失用户之前调整过的 cropRect。
                visible = isCropToolSelected,
                enabled = isCropToolSelected,
                resetSignal = cropResetSignal,
                onCropRectChanged = { cropRect ->
                    // 记录当前裁剪框，后续导出图片时需要用它换算原图坐标。
                    currentCropRect = cropRect
                },
                onCropRectUserChanged = { cropRect ->
                    hasCropUserChanged = true
                    Log.d(TAG, "用户调整裁剪框: $cropRect")
                }
            )
        }

        AnimatedContent(
            targetState = selectedTool,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            when (it) {
                EditImageTool.Crop -> {
                    CropRotatePanel(
                        rotateAngle = rotateAngle,
                        rotateScaleState = rotateScaleState,
                        aspectRatioList = aspectRatioList,
                        selectedAspectRatio = selectedAspectRatio,
                        selectedCropOrientation = selectedCropOrientation,
                        enabled = bitmap != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        onMirrorClick = {
                            if (bitmap == null) {
                                Log.w(TAG, "图片未加载完成，忽略镜像操作")
                            } else {
                                isImageMirrored = !isImageMirrored
                                Log.d(TAG, "切换图片镜像: isImageMirrored=$isImageMirrored")
                            }
                        },
                        onRotateRightAngleClick = {
                            if (bitmap == null) {
                                Log.w(TAG, "图片未加载完成，忽略 90 度旋转操作")
                            } else {
                                rightAngleRotationDegrees = (rightAngleRotationDegrees + RIGHT_ANGLE_ROTATION_STEP_DEGREES) % FULL_ROTATION_DEGREES
                                Log.d(TAG, "图片旋转 90 度: rightAngleRotationDegrees=$rightAngleRotationDegrees")
                            }
                        },
                        onCropOrientationClick = { orientation ->
                            selectedCropOrientation = orientation
                            Log.d(TAG, "切换裁剪方向: ${orientation.label}")
                        },
                        onAspectRatioClick = { aspectRatio ->
                            selectedAspectRatio = aspectRatio
                            Log.d(TAG, "切换裁剪比例模式: $aspectRatio")
                        }
                    )
                }

                EditImageTool.Filter -> {
                    FilterEditorContent(
                        bitmap = bitmap,
                        selectedFilter = selectedFilter,
                        onFilterClick = { filter ->
                            selectedFilter = filter
                            Log.d(TAG, "切换滤镜: ${filter.name}")
                        }
                    )
                }

                EditImageTool.Adjust -> {
                    AdjustEditorContent(
                        selectedType = selectedAdjustmentType,
                        adjustments = imageAdjustments,
                        scaleState = adjustmentScaleState,
                        enabled = bitmap != null,
                        onTypeClick = { type ->
                            selectedAdjustmentType = type
                            adjustmentScaleState.syncCurrentValue(imageAdjustments.valueOf(type).toFloat())
                            Log.d(TAG, "切换调节项: ${type.label}")
                        },
                        onValueChanged = { type, value ->
                            val safeValue = value.coerceIn(
                                -DEFAULT_ADJUSTMENT_LIMIT.toInt(),
                                DEFAULT_ADJUSTMENT_LIMIT.toInt()
                            )
                            imageAdjustments = imageAdjustments.withValue(type, safeValue)
                            Log.d(TAG, "编辑页基础调整变更: type=${type.label}, value=$safeValue, adjustments=$imageAdjustments")
                        }
                    )
                }
            }
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

    if (showDiscardConfirmDialog) {
        DiscardEditConfirmDialog(
            onConfirmDiscard = {
                showDiscardConfirmDialog = false
                Log.d(TAG, "用户确认不保存修改并离开编辑页")
                context.finishActivity()
            },
            onContinueEdit = {
                showDiscardConfirmDialog = false
                Log.d(TAG, "用户取消离开，继续编辑")
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

private enum class CropAspectOrientation(
    val label: String
) {
    Portrait(label = "纵向"),
    Landscape(label = "横向")
}

private enum class EditImageAdjustmentType(
    val label: String,
    val iconRes: Int
) {
    Brightness(label = "亮度", iconRes = R.drawable.fa_sun),
    Contrast(label = "对比度", iconRes = R.drawable.fa_adjust),
    Saturation(label = "饱和度", iconRes = R.drawable.fa_palette),
    Temperature(label = "色温", iconRes = R.drawable.fa_temperature_half),
    Clarity(label = "清晰度", iconRes = R.drawable.fa_bolt)
}

private data class EditImageAdjustments(
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val temperature: Int = 0,
    val clarity: Int = 0
) {
    fun valueOf(type: EditImageAdjustmentType): Int {
        return when (type) {
            EditImageAdjustmentType.Brightness -> brightness
            EditImageAdjustmentType.Contrast -> contrast
            EditImageAdjustmentType.Saturation -> saturation
            EditImageAdjustmentType.Temperature -> temperature
            EditImageAdjustmentType.Clarity -> clarity
        }
    }

    fun withValue(type: EditImageAdjustmentType, value: Int): EditImageAdjustments {
        return when (type) {
            EditImageAdjustmentType.Brightness -> copy(brightness = value)
            EditImageAdjustmentType.Contrast -> copy(contrast = value)
            EditImageAdjustmentType.Saturation -> copy(saturation = value)
            EditImageAdjustmentType.Temperature -> copy(temperature = value)
            EditImageAdjustmentType.Clarity -> copy(clarity = value)
        }
    }
}

private fun formatAdjustmentValue(value: Int): String {
    return if (value > 0) "+$value" else value.toString()
}

@Composable
private fun RegionPreviewTileImage(
    tile: ImageRegionTile?,
    colorFilter: ColorFilter?
) {
    if (tile == null || tile.bitmap.isRecycled) {
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val rect = tile.normalizedRect
        Image(
            bitmap = tile.bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .offset(x = maxWidth * rect.left, y = maxHeight * rect.top)
                .size(
                    width = maxWidth * rect.width,
                    height = maxHeight * rect.height
                ),
            contentScale = ContentScale.FillBounds,
            // 高清分块同样应用滤镜/调节矩阵，避免放大查看时色彩和采样预览不一致。
            colorFilter = colorFilter
        )
    }
}

@Composable
private fun DiscardEditConfirmDialog(
    onConfirmDiscard: () -> Unit,
    onContinueEdit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onContinueEdit,
        containerColor = Color(0xFF18181B),
        title = {
            Text(
                text = "不保存修改？",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "当前编辑内容尚未保存，确认返回将丢失这些修改。",
                color = Color(0xFFD1D5DB),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmDiscard) {
                Text(text = "确认", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onContinueEdit) {
                Text(text = "继续编辑", color = Color(0xFF9CA3AF), fontWeight = FontWeight.Bold)
            }
        }
    )
}

private fun buildRegionTileRequest(
    sourceUri: Uri?,
    zoom: Float,
    pan: Offset,
    containerSize: IntSize,
    imageLayerBounds: Rect
): ImageRegionTileRequest? {
    if (
        sourceUri == null ||
        zoom < ORIGINAL_TILE_ZOOM_THRESHOLD ||
        containerSize.width <= 0 ||
        containerSize.height <= 0 ||
        imageLayerBounds.isEmpty
    ) {
        return null
    }

    val visibleViewport = Rect(
        left = -pan.x / zoom,
        top = -pan.y / zoom,
        right = (containerSize.width - pan.x) / zoom,
        bottom = (containerSize.height - pan.y) / zoom
    )
    val visibleImageRect = visibleViewport.intersectOrNull(imageLayerBounds) ?: return null
    val normalizedRect = Rect(
        left = ((visibleImageRect.left - imageLayerBounds.left) / imageLayerBounds.width).coerceIn(0F, 1F),
        top = ((visibleImageRect.top - imageLayerBounds.top) / imageLayerBounds.height).coerceIn(0F, 1F),
        right = ((visibleImageRect.right - imageLayerBounds.left) / imageLayerBounds.width).coerceIn(0F, 1F),
        bottom = ((visibleImageRect.bottom - imageLayerBounds.top) / imageLayerBounds.height).coerceIn(0F, 1F)
    ).quantizeForTile()

    if (normalizedRect.width <= 0.001F || normalizedRect.height <= 0.001F) {
        return null
    }
    return ImageRegionTileRequest(
        sourceUri = sourceUri,
        normalizedRect = normalizedRect
    )
}

private fun calculateMinimumPreviewZoom(
    imageBounds: Rect,
    cropRect: Rect
): Float {
    if (imageBounds.isEmpty || imageBounds.width <= 0F || imageBounds.height <= 0F) {
        return 1F
    }

    val targetCropRect = if (!cropRect.isEmpty) cropRect else imageBounds
    if (targetCropRect.isEmpty || targetCropRect.width <= 0F || targetCropRect.height <= 0F) {
        return 1F
    }

    // 当裁剪框和图片等大时，最小缩放自然是 1；裁剪框变小时才允许缩到 0.5 倍查看。
    val zoomForWidth = targetCropRect.width / imageBounds.width
    val zoomForHeight = targetCropRect.height / imageBounds.height
    return maxOf(MIN_PREVIEW_ZOOM, zoomForWidth, zoomForHeight)
        .coerceIn(MIN_PREVIEW_ZOOM, MAX_PREVIEW_ZOOM)
}

private fun Offset.coercePreviewPan(
    zoom: Float,
    imageBounds: Rect,
    cropRect: Rect
): Offset {
    if (imageBounds.isEmpty || imageBounds.width <= 0F || imageBounds.height <= 0F || zoom <= 0F) {
        return Offset.Zero
    }

    val targetCropRect = if (!cropRect.isEmpty) cropRect else imageBounds
    if (targetCropRect.isEmpty) {
        return Offset.Zero
    }

    val minX = targetCropRect.right - imageBounds.right * zoom
    val maxX = targetCropRect.left - imageBounds.left * zoom
    val minY = targetCropRect.bottom - imageBounds.bottom * zoom
    val maxY = targetCropRect.top - imageBounds.top * zoom

    fun coerceAxis(value: Float, minValue: Float, maxValue: Float): Float {
        return if (minValue <= maxValue) {
            value.coerceIn(minValue, maxValue)
        } else {
            // 理论上最小缩放会避免图片小于裁剪框；这里保底居中，防止异常尺寸导致跳动。
            (minValue + maxValue) / 2F
        }
    }

    return Offset(
        x = coerceAxis(x, minX, maxX),
        y = coerceAxis(y, minY, maxY)
    )
}

private fun Rect.transformByPreviewGesture(
    zoom: Float,
    pan: Offset
): Rect {
    if (isEmpty || zoom <= 0F) {
        return Rect.Zero
    }
    return Rect(
        left = left * zoom + pan.x,
        top = top * zoom + pan.y,
        right = right * zoom + pan.x,
        bottom = bottom * zoom + pan.y
    )
}

private fun shouldHandleSingleFingerPreviewPan(
    touch: Offset,
    cropRect: Rect,
    cropBounds: Rect,
    reserveCropBoxResizeGesture: Boolean
): Boolean {
    if (!reserveCropBoxResizeGesture || cropRect.isEmpty || cropBounds.isEmpty) {
        return true
    }

    // 单指拖动默认移动图片；只有命中裁剪框可缩放区域时，才把手势留给裁剪框组件。
    return !touch.isInCropResizeGestureArea(
        rect = cropRect,
        bounds = cropBounds
    )
}

private fun Offset.isInCropResizeGestureArea(
    rect: Rect,
    bounds: Rect
): Boolean {
    val topLeft = Offset(rect.left, rect.top)
    val topRight = Offset(rect.right, rect.top)
    val bottomLeft = Offset(rect.left, rect.bottom)
    val bottomRight = Offset(rect.right, rect.bottom)
    if (
        distanceTo(topLeft) <= CROP_CORNER_TOUCH_RADIUS ||
        distanceTo(topRight) <= CROP_CORNER_TOUCH_RADIUS ||
        distanceTo(bottomLeft) <= CROP_CORNER_TOUCH_RADIUS ||
        distanceTo(bottomRight) <= CROP_CORNER_TOUCH_RADIUS
    ) {
        return true
    }

    val isMoveLocked = rect.width >= bounds.width || rect.height >= bounds.height
    if (!isMoveLocked || !rect.contains(this)) {
        return false
    }

    val nearLeft = abs(x - rect.left) <= CROP_EDGE_RESIZE_TOUCH_INSET
    val nearRight = abs(x - rect.right) <= CROP_EDGE_RESIZE_TOUCH_INSET
    val nearTop = abs(y - rect.top) <= CROP_EDGE_RESIZE_TOUCH_INSET
    val nearBottom = abs(y - rect.bottom) <= CROP_EDGE_RESIZE_TOUCH_INSET
    return nearLeft || nearRight || nearTop || nearBottom
}

private fun Offset.distanceTo(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

private fun Rect.intersectOrNull(other: Rect): Rect? {
    val left = maxOf(this.left, other.left)
    val top = maxOf(this.top, other.top)
    val right = minOf(this.right, other.right)
    val bottom = minOf(this.bottom, other.bottom)
    if (right <= left || bottom <= top) {
        return null
    }
    return Rect(left = left, top = top, right = right, bottom = bottom)
}

private fun Rect.quantizeForTile(): Rect {
    fun quantize(value: Float): Float = (value * 1000F).roundToInt() / 1000F
    return Rect(
        left = quantize(left),
        top = quantize(top),
        right = quantize(right),
        bottom = quantize(bottom)
    )
}

@Composable
private fun EditImageSaveButton(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor = if (enabled) Color.White else Color(0xFF3A3A3A)
    val textColor = if (enabled) Color.Black else Color(0xFF8E8E8E)

    Text(
        text = "保存",
        color = textColor,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            // 固定水平和垂直内边距，让启用态白底胶囊按钮更稳定。
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun CropRotatePanel(
    rotateAngle: Float,
    rotateScaleState: ArcValueScaleState,
    aspectRatioList: List<String>,
    selectedAspectRatio: String,
    selectedCropOrientation: CropAspectOrientation,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onMirrorClick: () -> Unit,
    onRotateRightAngleClick: () -> Unit,
    onCropOrientationClick: (CropAspectOrientation) -> Unit,
    onAspectRatioClick: (String) -> Unit
) {
    val displayAngle = rotateAngle.roundToInt()
    val progressFraction = (abs(rotateAngle) / DEFAULT_ROTATE_ANGLE_LIMIT).coerceIn(0F, 1F)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.svg_switch),
                contentDescription = "镜像图片",
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onMirrorClick
                    )
            )
            Spacer(modifier = Modifier.width(24.dp))
            NumericValueIndicator(
                value = displayAngle,
                progressFraction = progressFraction,
                isNegative = rotateAngle < 0F,
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
                // 拖动刻度时保持数字和圆环即时反馈，避免动画造成轻微滞后感。
                animateProgress = false
            )
            Spacer(modifier = Modifier.width(24.dp))
            Image(
                painter = painterResource(R.drawable.svg_rotate),
                contentDescription = "旋转 90 度",
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onRotateRightAngleClick
                    )
            )
        }

        ArcValueScale(
            state = rotateScaleState,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            onStartMove = {
                Log.d(TAG, "开始调整图片旋转角度: ${rotateScaleState.currentValue}")
            },
            onEndMove = {
                Log.d(TAG, "结束调整图片旋转角度: ${rotateScaleState.currentValue}")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        CropOrientationSelector(
            selectedOrientation = selectedCropOrientation,
            enabled = enabled,
            onOrientationClick = onCropOrientationClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        CropAspectRatioSelector(
            aspectRatioList = aspectRatioList,
            selectedAspectRatio = selectedAspectRatio,
            enabled = enabled,
            onAspectRatioClick = onAspectRatioClick
        )
    }
}

@Composable
private fun CropOrientationSelector(
    selectedOrientation: CropAspectOrientation,
    enabled: Boolean,
    onOrientationClick: (CropAspectOrientation) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CropAspectOrientation.entries.forEachIndexed { index, orientation ->
            CropOrientationButton(
                orientation = orientation,
                selected = orientation == selectedOrientation,
                enabled = enabled,
                onClick = { onOrientationClick(orientation) }
            )
            if (index != CropAspectOrientation.entries.lastIndex) {
                Spacer(modifier = Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun CropOrientationButton(
    orientation: CropAspectOrientation,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) Color.White else Color(0xFF212121)
    val iconColor = if (selected) Color.Black else Color.White
    val borderColor = if (selected) Color.White else Color(0x33FFFFFF)
    val iconWidth = if (orientation == CropAspectOrientation.Portrait) 13.dp else 26.dp
    val iconHeight = if (orientation == CropAspectOrientation.Portrait) 26.dp else 13.dp

    Box(
        modifier = Modifier
            .width(52.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor, RoundedCornerShape(14.dp))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = iconWidth, height = iconHeight)
                .clip(RoundedCornerShape(2.dp))
                // 方向按钮只用填充矩形表达横竖方向，避免和比例模式文字争抢视觉层级。
                .background(iconColor.copy(alpha = if (enabled) 1F else 0.45F))
        )
    }
}

@Composable
private fun CropAspectRatioSelector(
    aspectRatioList: List<String>,
    selectedAspectRatio: String,
    enabled: Boolean,
    onAspectRatioClick: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(aspectRatioList.size) { index ->
            val aspectRatio = aspectRatioList[index]
            CropAspectRatioButton(
                text = aspectRatio,
                selected = aspectRatio == selectedAspectRatio,
                enabled = enabled,
                onClick = { onAspectRatioClick(aspectRatio) }
            )
        }
    }
}

@Composable
private fun CropAspectRatioButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) Color.White else Color(0xFF212121)
    val textColor = if (selected) Color.Black else Color.White
    val borderColor = if (selected) Color.White else Color(0x33FFFFFF)

    Text(
        text = text,
        color = textColor.copy(alpha = if (enabled) 1F else 0.45F),
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor, RoundedCornerShape(18.dp))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(18.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            // 裁剪比例按钮固定高度和横向内边距，避免模式文案切换时排版跳动。
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun FilterEditorContent(
    bitmap: Bitmap?,
    selectedFilter: ImageFilterOption,
    onFilterClick: (ImageFilterOption) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
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
    }
}

@Composable
private fun AdjustEditorContent(
    selectedType: EditImageAdjustmentType,
    adjustments: EditImageAdjustments,
    scaleState: ArcValueScaleState,
    enabled: Boolean,
    onTypeClick: (EditImageAdjustmentType) -> Unit,
    onValueChanged: (EditImageAdjustmentType, Int) -> Unit
) {
    val currentValue = scaleState.currentValue
        .roundToInt()
        .coerceIn(-DEFAULT_ADJUSTMENT_LIMIT.toInt(), DEFAULT_ADJUSTMENT_LIMIT.toInt())
    val progressFraction = (abs(scaleState.currentValue) / DEFAULT_ADJUSTMENT_LIMIT).coerceIn(0F, 1F)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NumericValueIndicator(
            value = currentValue,
            progressFraction = progressFraction,
            isNegative = currentValue < 0,
            modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
            // 调节拖动时需要数字和圆环跟手，不等待过渡动画。
            animateProgress = false
        )

        ArcValueScale(
            state = scaleState,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            onValueChanged = { nextValue ->
                onValueChanged(selectedType, nextValue.roundToInt())
            },
            onStartMove = {
                Log.d(TAG, "开始调整基础参数: type=${selectedType.label}, value=${scaleState.currentValue}")
            },
            onEndMove = {
                Log.d(TAG, "结束调整基础参数: type=${selectedType.label}, value=${scaleState.currentValue}")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val adjustmentTypes = EditImageAdjustmentType.entries
            items(adjustmentTypes.size) { index ->
                val type = adjustmentTypes[index]
                AdjustmentOptionButton(
                    type = type,
                    value = adjustments.valueOf(type),
                    selected = type == selectedType,
                    onClick = { onTypeClick(type) }
                )
            }
        }
    }
}

@Composable
private fun AdjustmentOptionButton(
    type: EditImageAdjustmentType,
    value: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) Color.White else Color(0xFF212121)
    val contentColor = if (selected) Color.Black else Color.White
    val borderColor = if (selected) Color.White else Color(0x33FFFFFF)

    Column(
        modifier = Modifier
            .width(72.dp)
            .height(66.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(type.iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            // 调节项图标只使用黑白两色，避免每个功能项出现彩色视觉干扰。
            colorFilter = ColorFilter.tint(contentColor)
        )
        Text(
            text = type.label,
            color = contentColor,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(top = 5.dp)
        )
        Text(
            text = formatAdjustmentValue(value),
            color = contentColor,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            maxLines = 1
        )
    }
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
        selected -> Color.White
        isPressed -> Color(0xFF212121)
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
    val iconColor = if (selected) Color.Black else Color.White
    val textColor = if (selected) Color.Black else Color.White

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

private data class AdaptiveImagePreviewLayout(
    val cropBounds: Rect = Rect.Zero,
    val imageLayerBounds: Rect = Rect.Zero
)

private fun calculateAdaptiveImagePreviewLayout(
    bitmap: Bitmap?,
    containerSize: IntSize,
    rightAngleRotationDegrees: Int
): AdaptiveImagePreviewLayout {
    if (bitmap == null || containerSize.width <= 0 || containerSize.height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) {
        return AdaptiveImagePreviewLayout()
    }

    val isSizeSwapped = isRightAngleRotationSwapped(rightAngleRotationDegrees)
    val visualSourceWidth = if (isSizeSwapped) bitmap.height.toFloat() else bitmap.width.toFloat()
    val visualSourceHeight = if (isSizeSwapped) bitmap.width.toFloat() else bitmap.height.toFloat()
    val cropBounds = calculateFitBounds(
        sourceWidth = visualSourceWidth,
        sourceHeight = visualSourceHeight,
        containerSize = containerSize
    )
    if (cropBounds.isEmpty) {
        return AdaptiveImagePreviewLayout()
    }

    val layerWidth = if (isSizeSwapped) cropBounds.height else cropBounds.width
    val layerHeight = if (isSizeSwapped) cropBounds.width else cropBounds.height
    val centerX = cropBounds.left + cropBounds.width / 2F
    val centerY = cropBounds.top + cropBounds.height / 2F

    // 90/270 度旋转时，实际绘制层要使用旋转前尺寸，旋转后的视觉边界才会正好落在 cropBounds 内。
    val imageLayerBounds = Rect(
        left = centerX - layerWidth / 2F,
        top = centerY - layerHeight / 2F,
        right = centerX + layerWidth / 2F,
        bottom = centerY + layerHeight / 2F
    )
    return AdaptiveImagePreviewLayout(
        cropBounds = cropBounds,
        imageLayerBounds = imageLayerBounds
    )
}

private fun calculateFitBounds(
    sourceWidth: Float,
    sourceHeight: Float,
    containerSize: IntSize
): Rect {
    if (sourceWidth <= 0F || sourceHeight <= 0F || containerSize.width <= 0 || containerSize.height <= 0) {
        return Rect.Zero
    }

    val containerWidth = containerSize.width.toFloat()
    val containerHeight = containerSize.height.toFloat()
    val sourceRatio = sourceWidth / sourceHeight
    val containerRatio = containerWidth / containerHeight
    val displayWidth: Float
    val displayHeight: Float
    if (containerRatio > sourceRatio) {
        // 高度受限时优先按高度缩放，确保预览图和裁剪框都保持原始宽高比。
        displayHeight = containerHeight
        displayWidth = displayHeight * sourceRatio
    } else {
        // 宽度受限时按宽度缩放，避免图片横向溢出预览区域。
        displayWidth = containerWidth
        displayHeight = displayWidth / sourceRatio
    }

    val left = (containerWidth - displayWidth) / 2F
    val top = (containerHeight - displayHeight) / 2F
    return Rect(
        left = left,
        top = top,
        right = left + displayWidth,
        bottom = top + displayHeight
    )
}

private fun isRightAngleRotationSwapped(rotationDegrees: Int): Boolean {
    val normalizedDegrees = ((rotationDegrees % FULL_ROTATION_DEGREES) + FULL_ROTATION_DEGREES) % FULL_ROTATION_DEGREES
    return normalizedDegrees == RIGHT_ANGLE_ROTATION_STEP_DEGREES ||
        normalizedDegrees == FULL_ROTATION_DEGREES - RIGHT_ANGLE_ROTATION_STEP_DEGREES
}

private fun calculateRotationCoverScale(
    cropBounds: Rect,
    imageLayerBounds: Rect,
    rotationDegrees: Float
): Float {
    if (cropBounds.isEmpty || imageLayerBounds.isEmpty || imageLayerBounds.width <= 0F || imageLayerBounds.height <= 0F) {
        Log.w(TAG, "计算旋转覆盖缩放失败，尺寸无效: cropBounds=$cropBounds, imageLayerBounds=$imageLayerBounds")
        return 1F
    }

    val radians = Math.toRadians(rotationDegrees.toDouble())
    val cosValue = abs(cos(radians)).toFloat()
    val sinValue = abs(sin(radians)).toFloat()
    val scaleForRotatedWidth = (cropBounds.width * cosValue + cropBounds.height * sinValue) / imageLayerBounds.width
    val scaleForRotatedHeight = (cropBounds.width * sinValue + cropBounds.height * cosValue) / imageLayerBounds.height

    // 同时满足旋转后横向和纵向包住裁剪框，避免裁剪框四角出现空白。
    return maxOf(scaleForRotatedWidth, scaleForRotatedHeight, 1F)
}

private fun String.toCropAspectRatio(
    bitmap: Bitmap?,
    rightAngleRotationDegrees: Int,
    cropOrientation: CropAspectOrientation
): Float? {
    return when (this) {
        "原始" -> bitmap?.let { image ->
            val isSizeSwapped = isRightAngleRotationSwapped(rightAngleRotationDegrees)
            val sourceWidth = if (isSizeSwapped) image.height else image.width
            val sourceHeight = if (isSizeSwapped) image.width else image.height
            if (sourceHeight > 0) {
                sourceWidth.toFloat() / sourceHeight.toFloat()
            } else {
                null
            }
        }
        "自由" -> null
        else -> {
            val width = substringBefore(":").toFloatOrNull()
            val height = substringAfter(":").toFloatOrNull()
            if (width != null && height != null && height > 0f) {
                // 固定比例由方向按钮决定宽高顺序，例如纵向时 16:9 会按 9:16 应用到裁剪框。
                cropOrientation.applyToRatio(width, height)
            } else {
                null
            }
        }
    }
}

private fun CropAspectOrientation.applyToRatio(width: Float, height: Float): Float {
    if (width <= 0F || height <= 0F) {
        Log.w(TAG, "裁剪比例解析失败，宽高无效: width=$width, height=$height")
        return 1F
    }
    if (width == height) {
        return 1F
    }

    val shortSide = minOf(width, height)
    val longSide = maxOf(width, height)
    return when (this) {
        CropAspectOrientation.Portrait -> shortSide / longSide
        CropAspectOrientation.Landscape -> longSide / shortSide
    }
}

private data class ImageFilterOption(
    val name: String,
    val shortName: String,
    val composeMatrix: ColorMatrix?,
    val androidMatrix: android.graphics.ColorMatrix?
) {
    val isOriginal: Boolean
        get() = composeMatrix == null && androidMatrix == null

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

private fun buildComposeColorMatrix(
    filterOption: ImageFilterOption,
    adjustments: EditImageAdjustments
): ColorMatrix? {
    val hasAdjustments = adjustments != EditImageAdjustments()
    if (filterOption.composeMatrix == null && !hasAdjustments) {
        return null
    }

    // 复制基础调整页的矩阵叠加顺序，确保编辑页预览和导出效果一致。
    val matrix = filterOption.composeMatrix?.let { ColorMatrix(it.values.copyOf()) } ?: ColorMatrix()
    if (adjustments.saturation != 0) {
        matrix *= ColorMatrix().apply { setToSaturation(1f + adjustments.saturation / 100f) }
    }
    if (adjustments.brightness != 0 || adjustments.contrast != 0 || adjustments.temperature != 0) {
        matrix *= buildComposeToneMatrix(adjustments)
    }
    return matrix
}

private fun buildComposeToneMatrix(adjustments: EditImageAdjustments): ColorMatrix {
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
    adjustments: EditImageAdjustments
): android.graphics.ColorMatrix? {
    val hasAdjustments = adjustments != EditImageAdjustments()
    if (filterOption.androidMatrix == null && !hasAdjustments) {
        return null
    }

    // 相册保存使用 Android ColorMatrix，保持和 Compose 预览同一套调整逻辑。
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

private fun buildAndroidToneMatrix(adjustments: EditImageAdjustments): android.graphics.ColorMatrix {
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
