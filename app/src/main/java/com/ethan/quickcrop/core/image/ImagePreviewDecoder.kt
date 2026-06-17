package com.ethan.quickcrop.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlin.math.max

/**
 * 图片预览解码工具。
 *
 * Compose/Canvas 对单次绘制 Bitmap 有大小限制，预览场景不应该直接解码原图。
 * 这里按最大边长和内存上限采样，避免超大图触发 trying to draw too large bitmap 崩溃。
 */
object ImagePreviewDecoder {
    private const val TAG = "ImagePreviewDecoder"
    private const val DEFAULT_MAX_LONG_SIDE = 4096
    private const val DEFAULT_MAX_BITMAP_BYTES = 48 * 1024 * 1024
    private const val ARGB_8888_BYTES_PER_PIXEL = 4

    fun decode(
        context: Context,
        uri: Uri,
        maxLongSide: Int = DEFAULT_MAX_LONG_SIDE,
        maxBitmapBytes: Int = DEFAULT_MAX_BITMAP_BYTES
    ): Bitmap? {
        return runCatching {
            val bounds = decodeBounds(context, uri) ?: decodeBoundsWithImageDecoder(context, uri) ?: return null
            if (bounds.width <= 0 || bounds.height <= 0) {
                Log.w(TAG, "图片尺寸无效，跳过预览解码: $uri, ${bounds.width}x${bounds.height}")
                return null
            }

            val sampleSize = calculateSampleSize(
                width = bounds.width,
                height = bounds.height,
                maxLongSide = maxLongSide,
                maxBitmapBytes = maxBitmapBytes
            )
            Log.d(TAG, "预览图采样解码: $uri, 原始=${bounds.width}x${bounds.height}, sampleSize=$sampleSize")

            val previewBitmap = decodeSampledBitmap(context = context, uri = uri, bounds = bounds, sampleSize = sampleSize)
                ?: return null
            // 初始化预览时自动按 EXIF 方向摆正，后续裁剪和保存都基于摆正后的视觉坐标。
            ImageExifUtils.correctBitmapOrientation(
                bitmap = previewBitmap,
                orientation = ImageExifUtils.readOrientation(context, uri)
            )
        }.onFailure { throwable ->
            Log.e(TAG, "预览图解码失败: $uri", throwable)
        }.getOrNull()
    }

    private fun decodeBounds(context: Context, uri: Uri): ImageBounds? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        } ?: return null

        return ImageBounds(width = options.outWidth, height = options.outHeight)
    }

    private fun decodeBoundsWithImageDecoder(context: Context, uri: Uri): ImageBounds? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return null
        }

        return runCatching {
            var imageBounds: ImageBounds? = null
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // ImageDecoder 兼容 HEIC/HEIF 等格式，这里只取尺寸并把目标缩到 1px，降低兜底探测成本。
                imageBounds = ImageBounds(width = info.size.width, height = info.size.height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(1, 1)
            }
            imageBounds
        }.onFailure { throwable ->
            Log.e(TAG, "ImageDecoder 读取预览图尺寸失败: $uri", throwable)
        }.getOrNull()
    }

    private fun decodeSampledBitmap(
        context: Context,
        uri: Uri,
        bounds: ImageBounds,
        sampleSize: Int
    ): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }?.let { bitmap ->
            return bitmap
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val targetWidth = max(1, bounds.width / sampleSize)
                val targetHeight = max(1, bounds.height / sampleSize)
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    // 预览图强制使用软件 Bitmap，避免硬件 Bitmap 在 Compose 缩放/裁剪时出现兼容问题。
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                    decoder.setTargetSize(targetWidth, targetHeight)
                }
            }.onFailure { throwable ->
                Log.e(TAG, "ImageDecoder 解码预览图失败，尝试 BitmapFactory 兜底: $uri", throwable)
            }
        }
        return null
    }

    private fun calculateSampleSize(
        width: Int,
        height: Int,
        maxLongSide: Int,
        maxBitmapBytes: Int
    ): Int {
        var sampleSize = 1
        while (shouldIncreaseSampleSize(width, height, sampleSize, maxLongSide, maxBitmapBytes)) {
            // BitmapFactory 的 inSampleSize 使用 2 的幂最稳定，兼容旧设备的解码实现。
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun shouldIncreaseSampleSize(
        width: Int,
        height: Int,
        sampleSize: Int,
        maxLongSide: Int,
        maxBitmapBytes: Int
    ): Boolean {
        val sampledWidth = max(1, width / sampleSize)
        val sampledHeight = max(1, height / sampleSize)
        val sampledLongSide = max(sampledWidth, sampledHeight)
        val sampledBytes = sampledWidth.toLong() * sampledHeight.toLong() * ARGB_8888_BYTES_PER_PIXEL
        return sampledLongSide > maxLongSide || sampledBytes > maxBitmapBytes
    }

    private data class ImageBounds(
        val width: Int,
        val height: Int
    )
}
