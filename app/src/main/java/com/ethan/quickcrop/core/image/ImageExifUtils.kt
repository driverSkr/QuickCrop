package com.ethan.quickcrop.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 图片 EXIF 工具。
 *
 * 编辑页所有预览和导出都通过这里处理方向，避免“预览已摆正、保存仍歪斜”的问题。
 */
object ImageExifUtils {
    private const val TAG = "ImageExifUtils"

    fun readImageInfo(context: Context, uri: Uri): ImageExifInfo? {
        val bounds = readBitmapBounds(context, uri) ?: return null
        val orientation = readOrientation(context, uri)
        val swapped = isOrientationSwapped(orientation)
        return ImageExifInfo(
            encodedWidth = bounds.width,
            encodedHeight = bounds.height,
            orientedWidth = if (swapped) bounds.height else bounds.width,
            orientedHeight = if (swapped) bounds.width else bounds.height,
            orientation = orientation
        )
    }

    fun readOrientation(context: Context, uri: Uri): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ExifInterface(inputStream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.onFailure { throwable ->
            Log.w(TAG, "读取 EXIF 方向失败，使用默认方向: $uri", throwable)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    fun decodeBitmapWithCorrectedOrientation(context: Context, uri: Uri): Bitmap {
        val orientation = readOrientation(context, uri)
        val sourceBitmap = requireNotNull(context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }) {
            "原图解码失败: $uri"
        }
        return correctBitmapOrientation(sourceBitmap, orientation)
    }

    fun correctBitmapOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = createOrientationMatrix(
            orientation = orientation,
            width = bitmap.width,
            height = bitmap.height
        )
        if (matrix.isIdentity) {
            return bitmap
        }

        return runCatching {
            // EXIF 方向只作用在像素进入编辑链路的入口处，后续 UI 坐标都按摆正后的图片计算。
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.onSuccess { corrected ->
            if (corrected !== bitmap) {
                bitmap.recycle()
            }
        }.onFailure { throwable ->
            Log.e(TAG, "按 EXIF 方向矫正图片失败，回退原图方向: orientation=$orientation", throwable)
        }.getOrDefault(bitmap)
    }

    fun copyExifMetadata(
        context: Context,
        sourceUri: Uri,
        outputUri: Uri,
        outputWidth: Int,
        outputHeight: Int
    ) {
        runCatching {
            val sourceExif = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                ExifInterface(inputStream)
            } ?: return

            context.contentResolver.openFileDescriptor(outputUri, "rw")?.use { parcelFileDescriptor ->
                val outputExif = ExifInterface(parcelFileDescriptor.fileDescriptor)
                PRESERVED_EXIF_TAGS.forEach { tag ->
                    sourceExif.getAttribute(tag)?.let { value ->
                        outputExif.setAttribute(tag, value)
                    }
                }

                // 输出图片已经把方向渲染进像素里，因此 EXIF 方向必须重置为正常方向。
                outputExif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString()
                )
                outputExif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, outputWidth.toString())
                outputExif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, outputHeight.toString())
                outputExif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, outputWidth.toString())
                outputExif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, outputHeight.toString())
                outputExif.saveAttributes()
            } ?: Log.w(TAG, "复制 EXIF 失败，无法打开输出文件: $outputUri")
        }.onFailure { throwable ->
            // EXIF 保留失败不能阻断用户保存图片，记录日志即可。
            Log.w(TAG, "复制 EXIF 元数据失败: source=$sourceUri, output=$outputUri", throwable)
        }
    }

    fun mapOrientedRectToEncodedRect(
        orientedRect: android.graphics.RectF,
        info: ImageExifInfo
    ): android.graphics.Rect {
        val encodedBounds = android.graphics.RectF(
            0F,
            0F,
            info.encodedWidth.toFloat(),
            info.encodedHeight.toFloat()
        )
        val points = floatArrayOf(
            orientedRect.left, orientedRect.top,
            orientedRect.right, orientedRect.top,
            orientedRect.right, orientedRect.bottom,
            orientedRect.left, orientedRect.bottom
        )
        for (index in points.indices step 2) {
            val mapped = mapOrientedPointToEncoded(
                x = points[index],
                y = points[index + 1],
                info = info
            )
            points[index] = mapped.first
            points[index + 1] = mapped.second
        }

        val left = points.filterIndexed { index, _ -> index % 2 == 0 }.minOrNull() ?: 0F
        val right = points.filterIndexed { index, _ -> index % 2 == 0 }.maxOrNull() ?: info.encodedWidth.toFloat()
        val top = points.filterIndexed { index, _ -> index % 2 == 1 }.minOrNull() ?: 0F
        val bottom = points.filterIndexed { index, _ -> index % 2 == 1 }.maxOrNull() ?: info.encodedHeight.toFloat()

        return android.graphics.Rect(
            floor(left).toInt().coerceIn(0, info.encodedWidth - 1),
            floor(top).toInt().coerceIn(0, info.encodedHeight - 1),
            ceil(right).toInt().coerceIn(1, info.encodedWidth),
            ceil(bottom).toInt().coerceIn(1, info.encodedHeight)
        ).also { rect ->
            if (!encodedBounds.contains(android.graphics.RectF(rect))) {
                Log.d(TAG, "分块区域已限制在原图范围内: rect=$rect, bounds=$encodedBounds")
            }
        }
    }

    private fun readBitmapBounds(context: Context, uri: Uri): BitmapBounds? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        } ?: return null
        return if (options.outWidth > 0 && options.outHeight > 0) {
            BitmapBounds(width = options.outWidth, height = options.outHeight)
        } else {
            null
        }
    }

    private fun createOrientationMatrix(orientation: Int, width: Int, height: Int): Matrix {
        return Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1F, 1F, width / 2F, height / 2F)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180F, width / 2F, height / 2F)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1F, -1F, width / 2F, height / 2F)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(90F)
                    postScale(-1F, 1F)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90F)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(270F)
                    postScale(-1F, 1F)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270F)
                else -> Unit
            }
        }
    }

    private fun isOrientationSwapped(orientation: Int): Boolean {
        return orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270
    }

    private fun mapOrientedPointToEncoded(x: Float, y: Float, info: ImageExifInfo): Pair<Float, Float> {
        val encodedWidth = info.encodedWidth.toFloat()
        val encodedHeight = info.encodedHeight.toFloat()
        return when (info.orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> encodedWidth - x to y
            ExifInterface.ORIENTATION_ROTATE_180 -> encodedWidth - x to encodedHeight - y
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> x to encodedHeight - y
            ExifInterface.ORIENTATION_TRANSPOSE -> y to x
            ExifInterface.ORIENTATION_ROTATE_90 -> y to encodedHeight - x
            ExifInterface.ORIENTATION_TRANSVERSE -> encodedWidth - y to encodedHeight - x
            ExifInterface.ORIENTATION_ROTATE_270 -> encodedWidth - y to x
            else -> x to y
        }
    }

    private val PRESERVED_EXIF_TAGS = arrayOf(
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_EXPOSURE_PROGRAM,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_LIGHT_SOURCE,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_WHITE_BALANCE
    )
}

data class ImageExifInfo(
    val encodedWidth: Int,
    val encodedHeight: Int,
    val orientedWidth: Int,
    val orientedHeight: Int,
    val orientation: Int
)

private data class BitmapBounds(
    val width: Int,
    val height: Int
)
