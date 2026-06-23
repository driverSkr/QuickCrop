package com.ethan.quickcrop.ui.edit.image.page

import EditImageBottomToolbar
import EditImageTool
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import com.ethan.quickcrop.custom.rememberArcValueScaleState
import com.ethan.quickcrop.extension.finishActivity
import com.ethan.quickcrop.ui.edit.image.ImageEditResultActivity
import com.ethan.quickcrop.ui.edit.image.view.AdjustEditorContent
import com.ethan.quickcrop.ui.edit.image.view.CropAspectOrientation
import com.ethan.quickcrop.ui.edit.image.view.CropRotatePanel
import com.ethan.quickcrop.ui.edit.image.view.DEFAULT_ADJUSTMENT_LIMIT
import com.ethan.quickcrop.ui.edit.image.view.DiscardEditConfirmDialog
import com.ethan.quickcrop.ui.edit.image.view.EditImageAdjustmentType
import com.ethan.quickcrop.ui.edit.image.view.EditImageAdjustments
import com.ethan.quickcrop.ui.edit.image.view.FilterEditorContent
import com.ethan.quickcrop.ui.edit.image.view.ImageFilterOption
import com.ethan.quickcrop.ui.edit.image.view.ResizableCropBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TAG = "EditImagePage"
private const val RIGHT_ANGLE_ROTATION_STEP_DEGREES = 90
private const val FULL_ROTATION_DEGREES = 360
private const val MIN_PREVIEW_ZOOM = 0.5F
private const val MAX_PREVIEW_ZOOM = 5F
private const val ORIGINAL_TILE_ZOOM_THRESHOLD = 1.6F
private const val CROP_CORNER_TOUCH_RADIUS = 96F
private const val CROP_EDGE_RESIZE_TOUCH_INSET = 72F

/**
 * 图片编辑页入口，负责预览解码、裁剪框交互、滤镜调节、保存导出和离开确认。
 */
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
    // 当前裁剪比例和方向只影响裁剪框约束，不直接修改原图数据。
    var selectedAspectRatio by remember { mutableStateOf(aspectRatioList[0]) }
    var selectedCropOrientation by remember { mutableStateOf(CropAspectOrientation.Portrait) }
    // 预览缩放/平移用于查看图片细节，保存时会映射到导出请求中保证所见即所得。
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
        // 放大到阈值后再请求原图局部分块，降低大图整体解码带来的内存压力。
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
    // 统一计算是否存在编辑操作，控制保存按钮、返回确认和重置按钮展示。
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

    // 统一处理顶部返回和系统返回，按是否存在未保存操作决定直接退出或弹窗确认。
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

    // 一键恢复所有编辑状态，包含裁剪、旋转、镜像、滤镜、调节和查看缩放。
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

    // 将当前屏幕编辑状态组装成保存请求，并交给图片保存处理器写入系统相册。
    fun handleSaveClick() {
        // 保存前再次校验源图和裁剪区域，避免异步解码未完成时触发导出。
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
        // 预览层的缩放和平移需要叠加到保存请求，否则导出结果会和屏幕预览不一致。
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
                                // 以双指中心为锚点缩放，缩放后中心位置尽量保持在手指下方。
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

/**
 * 原图高清分块预览层，放大查看时覆盖在低清预览图上提升局部清晰度。
 */
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

/**
 * 根据当前缩放、平移和图片层位置计算需要解码的原图可见区域。
 */
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

    // 把屏幕可见范围反算到未缩放的预览坐标系，再和图片层求交集。
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

    // 可见区域过小时跳过分块解码，避免频繁请求没有实际展示价值的小瓦片。
    if (normalizedRect.width <= 0.001F || normalizedRect.height <= 0.001F) {
        return null
    }
    return ImageRegionTileRequest(
        sourceUri = sourceUri,
        normalizedRect = normalizedRect
    )
}

/**
 * 计算预览图允许的最小缩放，确保图片始终覆盖裁剪框区域。
 */
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

/**
 * 将预览平移限制在安全范围内，避免图片被拖出裁剪框露出空白。
 */
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

    // 单轴平移需要单独约束，异常范围时回退到居中值。
    fun coerceAxis(value: Float, minValue: Float, maxValue: Float): Float {
        return if (minValue <= maxValue) {
            value.coerceIn(minValue, maxValue)
        } else {
            // 理论上最小缩放会避免图片小于裁剪框；这里保底居中，防止异常尺寸导致跳动。
            (minValue + maxValue) / 2F
        }
    }

    // 平移范围由“缩放后的图片边界必须覆盖裁剪框”反推得到。
    return Offset(
        x = coerceAxis(x, minX, maxX),
        y = coerceAxis(y, minY, maxY)
    )
}

/**
 * 把矩形应用预览层的缩放和平移，得到屏幕上的视觉边界。
 */
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

/**
 * 判断单指拖动是否应该交给图片预览层处理。
 */
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

/**
 * 判断触点是否落在裁剪框边角或边缘缩放热区内。
 */
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

/**
 * 计算两个触点之间的欧氏距离。
 */
private fun Offset.distanceTo(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

/**
 * 计算两个矩形的交集，没有重叠时返回 null。
 */
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

/**
 * 将分块请求区域量化到 0.001 精度，减少拖动时近似相同请求的重复解码。
 */
private fun Rect.quantizeForTile(): Rect {
    // 只保留千分位，避免极小浮点抖动造成 regionTileRequest 频繁变化。
    fun quantize(value: Float): Float = (value * 1000F).roundToInt() / 1000F
    return Rect(
        left = quantize(left),
        top = quantize(top),
        right = quantize(right),
        bottom = quantize(bottom)
    )
}

/**
 * 顶部保存按钮，根据是否可保存切换启用态和视觉样式。
 */
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

/**
 * 图片预览布局结果，分别保存裁剪框可用边界和实际图片绘制层边界。
 */
private data class AdaptiveImagePreviewLayout(
    val cropBounds: Rect = Rect.Zero,
    val imageLayerBounds: Rect = Rect.Zero
)

/**
 * 根据图片尺寸、容器尺寸和 90 度旋转状态计算预览布局。
 */
private fun calculateAdaptiveImagePreviewLayout(
    bitmap: Bitmap?,
    containerSize: IntSize,
    rightAngleRotationDegrees: Int
): AdaptiveImagePreviewLayout {
    if (bitmap == null || containerSize.width <= 0 || containerSize.height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) {
        return AdaptiveImagePreviewLayout()
    }

    val isSizeSwapped = isRightAngleRotationSwapped(rightAngleRotationDegrees)
    // 90/270 度时视觉宽高互换，裁剪框需要按旋转后的视觉尺寸适配容器。
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

/**
 * 按等比缩放规则计算源内容在容器内完整显示时的矩形边界。
 */
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

/**
 * 判断 90 度离散旋转是否会导致图片视觉宽高互换。
 */
private fun isRightAngleRotationSwapped(rotationDegrees: Int): Boolean {
    val normalizedDegrees = ((rotationDegrees % FULL_ROTATION_DEGREES) + FULL_ROTATION_DEGREES) % FULL_ROTATION_DEGREES
    return normalizedDegrees == RIGHT_ANGLE_ROTATION_STEP_DEGREES ||
        normalizedDegrees == FULL_ROTATION_DEGREES - RIGHT_ANGLE_ROTATION_STEP_DEGREES
}

/**
 * 计算任意角度旋转后仍能覆盖裁剪框所需的最小放大比例。
 */
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

/**
 * 将比例文案转换为裁剪框约束需要的宽高比，返回 null 表示自由比例。
 */
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

/**
 * 根据用户选择的横向/纵向方向调整固定比例的宽高顺序。
 */
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

/**
 * 构建 Compose 预览使用的颜色矩阵，合并滤镜和基础调节效果。
 */
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

/**
 * 构建 Compose 预览使用的亮度、对比度和色温矩阵。
 */
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

/**
 * 构建 Android 导出使用的颜色矩阵，合并滤镜和基础调节效果。
 */
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

/**
 * 构建 Android 导出使用的亮度、对比度和色温矩阵。
 */
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