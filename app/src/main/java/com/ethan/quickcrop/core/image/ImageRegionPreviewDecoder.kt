package com.ethan.quickcrop.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.compose.ui.geometry.Rect
import kotlin.math.max

/**
 * 原图区域分块预览解码器。
 *
 * 编辑页主预览使用采样图保证流畅；用户放大查看时，再按当前可见区域从原图解码局部高清块。
 */
object ImageRegionPreviewDecoder {
    private const val TAG = "ImageRegionPreviewDecoder"
    private const val DEFAULT_MAX_TILE_BYTES = 32 * 1024 * 1024
    private const val ARGB_8888_BYTES_PER_PIXEL = 4

    fun decodeTile(
        context: Context,
        request: ImageRegionTileRequest,
        maxTileBytes: Int = DEFAULT_MAX_TILE_BYTES
    ): ImageRegionTile? {
        return runCatching {
            val info = ImageExifUtils.readImageInfo(context, request.sourceUri) ?: return null
            val orientedRect = request.normalizedRect.toOrientedSourceRect(info)
            if (orientedRect.width() <= 1F || orientedRect.height() <= 1F) {
                Log.w(TAG, "分块预览区域过小，跳过解码: $orientedRect")
                return null
            }

            val encodedRect = ImageExifUtils.mapOrientedRectToEncodedRect(orientedRect, info)
            if (encodedRect.width() <= 0 || encodedRect.height() <= 0) {
                Log.w(TAG, "分块预览编码区域无效，跳过解码: $encodedRect")
                return null
            }

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = calculateSampleSize(
                    width = encodedRect.width(),
                    height = encodedRect.height(),
                    maxBitmapBytes = maxTileBytes
                )
            }

            val tileBitmap = context.contentResolver.openInputStream(request.sourceUri)?.use { inputStream ->
                @Suppress("DEPRECATION")
                val decoder = BitmapRegionDecoder.newInstance(inputStream, false) ?: return null
                try {
                    decoder.decodeRegion(encodedRect, options)
                } finally {
                    // BitmapRegionDecoder 需要显式释放，避免高倍预览频繁分块时占用 native 内存。
                    decoder.recycle()
                }
            } ?: return null

            val correctedBitmap = ImageExifUtils.correctBitmapOrientation(tileBitmap, info.orientation)
            Log.d(
                TAG,
                "加载原图分块预览: normalized=${request.normalizedRect}, encoded=$encodedRect, sample=${options.inSampleSize}"
            )
            ImageRegionTile(
                bitmap = correctedBitmap,
                normalizedRect = request.normalizedRect
            )
        }.onFailure { throwable ->
            Log.w(TAG, "原图分块预览解码失败: ${request.sourceUri}", throwable)
        }.getOrNull()
    }

    private fun Rect.toOrientedSourceRect(info: ImageExifInfo): RectF {
        return RectF(
            left.coerceIn(0F, 1F) * info.orientedWidth,
            top.coerceIn(0F, 1F) * info.orientedHeight,
            right.coerceIn(0F, 1F) * info.orientedWidth,
            bottom.coerceIn(0F, 1F) * info.orientedHeight
        )
    }

    private fun calculateSampleSize(width: Int, height: Int, maxBitmapBytes: Int): Int {
        var sampleSize = 1
        while (true) {
            val sampledWidth = max(1, width / sampleSize)
            val sampledHeight = max(1, height / sampleSize)
            val sampledBytes = sampledWidth.toLong() * sampledHeight.toLong() * ARGB_8888_BYTES_PER_PIXEL
            if (sampledBytes <= maxBitmapBytes) {
                return sampleSize
            }
            // 2 的幂更适配 BitmapRegionDecoder 的采样行为。
            sampleSize *= 2
        }
    }
}

data class ImageRegionTileRequest(
    val sourceUri: Uri,
    val normalizedRect: Rect
)

data class ImageRegionTile(
    val bitmap: Bitmap,
    val normalizedRect: Rect
)
