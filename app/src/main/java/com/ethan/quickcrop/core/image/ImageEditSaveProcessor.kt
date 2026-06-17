package com.ethan.quickcrop.core.image

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt

/**
 * 图片编辑页保存处理器。
 *
 * 这里重新读取原图并按编辑页当前显示变换渲染，避免直接使用预览 Bitmap 导致导出清晰度下降。
 */
object ImageEditSaveProcessor {
    private const val TAG = "ImageEditSaveProcessor"
    private const val OUTPUT_QUALITY = 95
    private const val RIGHT_ANGLE_ROTATION_STEP_DEGREES = 90
    private const val FULL_ROTATION_DEGREES = 360

    fun saveToGallery(context: Context, request: ImageEditSaveRequest): Result<Uri> {
        return runCatching {
            require(!request.cropRect.isEmpty) { "裁剪框为空，无法保存图片" }
            require(!request.visualImageBounds.isEmpty) { "图片显示区域为空，无法保存图片" }
            require(!request.imageLayerBounds.isEmpty) { "图片绘制区域为空，无法保存图片" }

            val sourceBitmap = decodeSourceBitmap(context, request.sourceUri)
            try {
                val outputBitmap = renderEditedCropBitmap(sourceBitmap, request)
                try {
                    saveBitmapToGallery(context, outputBitmap)
                } finally {
                    outputBitmap.recycle()
                }
            } finally {
                sourceBitmap.recycle()
            }
        }.onFailure { throwable ->
            Log.e(TAG, "保存图片编辑结果失败", throwable)
        }
    }

    private fun decodeSourceBitmap(context: Context, uri: Uri): Bitmap {
        return requireNotNull(context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }) {
            "原图解码失败: $uri"
        }
    }

    private fun renderEditedCropBitmap(sourceBitmap: Bitmap, request: ImageEditSaveRequest): Bitmap {
        val outputSize = request.calculateOutputSize(
            sourceWidth = sourceBitmap.width,
            sourceHeight = sourceBitmap.height
        )
        val outputBitmap = Bitmap.createBitmap(
            outputSize.width,
            outputSize.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(outputBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            request.filterColorMatrix?.let { matrix ->
                // 保存时应用编辑页当前滤镜，保证相册结果和主预览一致。
                colorFilter = ColorMatrixColorFilter(matrix)
            }
        }
        val layerCenterX = request.imageLayerBounds.left + request.imageLayerBounds.width / 2F
        val layerCenterY = request.imageLayerBounds.top + request.imageLayerBounds.height / 2F
        val outputScaleX = outputSize.width / request.cropRect.width
        val outputScaleY = outputSize.height / request.cropRect.height
        val matrix = Matrix().apply {
            // 先把原图像素铺满编辑页中的图片绘制层。
            setScale(
                request.imageLayerBounds.width / sourceBitmap.width,
                request.imageLayerBounds.height / sourceBitmap.height
            )
            postTranslate(request.imageLayerBounds.left, request.imageLayerBounds.top)
            // 再叠加编辑页里的镜像、自动覆盖缩放和旋转，保持导出结果与预览一致。
            postScale(
                if (request.mirrorHorizontal) -request.coverScale else request.coverScale,
                request.coverScale,
                layerCenterX,
                layerCenterY
            )
            postRotate(request.rotationDegrees, layerCenterX, layerCenterY)
            // 最后把裁剪框区域映射到输出 Bitmap。
            postTranslate(-request.cropRect.left, -request.cropRect.top)
            postScale(outputScaleX, outputScaleY)
        }

        canvas.drawBitmap(sourceBitmap, matrix, paint)
        return outputBitmap
    }

    private fun ImageEditSaveRequest.calculateOutputSize(sourceWidth: Int, sourceHeight: Int): OutputSize {
        val isSizeSwapped = isRightAngleRotationSwapped(rightAngleRotationDegrees)
        val visualSourceWidth = if (isSizeSwapped) sourceHeight.toFloat() else sourceWidth.toFloat()
        val visualSourceHeight = if (isSizeSwapped) sourceWidth.toFloat() else sourceHeight.toFloat()
        val outputWidth = (cropRect.width / visualImageBounds.width * visualSourceWidth)
            .roundToInt()
            .coerceAtLeast(1)
        val outputHeight = (cropRect.height / visualImageBounds.height * visualSourceHeight)
            .roundToInt()
            .coerceAtLeast(1)
        return OutputSize(width = outputWidth, height = outputHeight)
    }

    private fun isRightAngleRotationSwapped(rotationDegrees: Int): Boolean {
        val normalizedDegrees = ((rotationDegrees % FULL_ROTATION_DEGREES) + FULL_ROTATION_DEGREES) % FULL_ROTATION_DEGREES
        return normalizedDegrees == RIGHT_ANGLE_ROTATION_STEP_DEGREES ||
            normalizedDegrees == FULL_ROTATION_DEGREES - RIGHT_ANGLE_ROTATION_STEP_DEGREES
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
            Log.d(TAG, "图片编辑结果已保存到相册: $outputUri")
            return outputUri
        } finally {
            if (!completed) {
                // 写入失败时清理半成品，避免相册里出现损坏条目。
                runCatching { resolver.delete(outputUri, null, null) }
                    .onFailure { throwable -> Log.w(TAG, "清理保存失败文件失败: $outputUri", throwable) }
            }
        }
    }
}

data class ImageEditSaveRequest(
    val sourceUri: Uri,
    val cropRect: Rect,
    val visualImageBounds: Rect,
    val imageLayerBounds: Rect,
    val rightAngleRotationDegrees: Int,
    val rotationDegrees: Float,
    val coverScale: Float,
    val mirrorHorizontal: Boolean,
    val filterColorMatrix: ColorMatrix?
)

private data class OutputSize(
    val width: Int,
    val height: Int
)
