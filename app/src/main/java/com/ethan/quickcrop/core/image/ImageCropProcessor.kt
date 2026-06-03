package com.ethan.quickcrop.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import java.io.File
import kotlin.math.roundToInt

/**
 * 图片裁剪导出处理器。
 *
 * UI 层只负责维护裁剪框和图片变换状态，这里重新读取原图并用 Canvas + Matrix
 * 渲染最终结果，便于后续继续叠加旋转、镜像、滤镜、贴纸等编辑操作。
 */
object ImageCropProcessor {
    private const val TAG = "ImageCropProcessor"
    private const val OUTPUT_QUALITY = 95

    /**
     * 执行一次图片裁剪导出。
     *
     * 这里不复用裁剪页上用于预览的 Bitmap，而是根据 sourceUri 重新解码原图：
     * 1. 保证导出的清晰度来自原始像素，不受 UI 预览尺寸影响。
     * 2. 后续可以在同一条导出链路里追加旋转、镜像、滤镜等编辑操作。
     * 3. 所有耗时工作由调用方放到 IO 线程，避免阻塞 Compose 主线程。
     */
    fun crop(context: Context, request: ImageCropRequest): Result<Uri> {
        return runCatching {
            require(!request.cropRect.isEmpty) { "裁剪框为空，无法导出图片" }
            require(!request.baseImageBounds.isEmpty) { "图片显示区域为空，无法换算裁剪坐标" }

            // 从原图 Uri 重新解码，裁剪结果不依赖页面预览图的缩放质量。
            val sourceBitmap = decodeSourceBitmap(context, request.sourceUri)
            try {
                // 把 Compose 画布上的裁剪框坐标换算为原图像素坐标。
                val sourceRect = request.toSourceCropRect(
                    sourceWidth = sourceBitmap.width,
                    sourceHeight = sourceBitmap.height
                )
                Log.d(TAG, "原图裁剪区域: $sourceRect")

                val outputBitmap = renderCropBitmap(sourceBitmap, sourceRect)
                try {
                    saveToCache(context, outputBitmap)
                } finally {
                    outputBitmap.recycle()
                }
            } finally {
                sourceBitmap.recycle()
            }
        }.onFailure { throwable ->
            Log.e(TAG, "图片裁剪导出失败", throwable)
        }
    }

    private fun decodeSourceBitmap(context: Context, uri: Uri): Bitmap {
        // 目前先整图解码，便于保持导出管线直观；大图优化时可替换为区域解码或采样解码。
        return requireNotNull(context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }) {
            "原图解码失败: $uri"
        }
    }

    private fun renderCropBitmap(sourceBitmap: Bitmap, sourceRect: SourceCropRect): Bitmap {
        // 输出尺寸直接使用原图像素中的裁剪区域尺寸，保证裁剪结果保持原始细节。
        val outputWidth = sourceRect.width.coerceAtLeast(1)
        val outputHeight = sourceRect.height.coerceAtLeast(1)
        val outputBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        // FILTER_BITMAP_FLAG 让缩放/变换时的采样更平滑，当前纯裁剪场景也保留它，便于后续扩展。
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        val matrix = Matrix().apply {
            // 把原图裁剪区域移动到输出画布左上角；后续旋转/镜像也可以继续叠加在这个矩阵上。
            setTranslate(-sourceRect.left.toFloat(), -sourceRect.top.toFloat())
        }

        canvas.drawBitmap(sourceBitmap, matrix, paint)
        return outputBitmap
    }

    private fun saveToCache(context: Context, bitmap: Bitmap): Uri {
        // 裁剪结果先落到应用缓存，预览页通过 file Uri 读取；后续保存到相册时再走 MediaStore。
        val outputDir = File(context.cacheDir, "crop_result").apply {
            if (!exists() && !mkdirs()) {
                Log.w(TAG, "创建裁剪结果目录失败: $absolutePath")
            }
        }
        val outputFile = File(outputDir, "crop_${System.currentTimeMillis()}.jpg")
        outputFile.outputStream().use { outputStream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, OUTPUT_QUALITY, outputStream)) {
                "裁剪结果压缩失败"
            }
        }
        Log.d(TAG, "裁剪结果已保存: ${outputFile.absolutePath}")
        return Uri.fromFile(outputFile)
    }
}

/**
 * 一次裁剪导出需要的全部参数。
 *
 * baseImageBounds 是原图在裁剪页中的显示区域，cropRect 是裁剪框在同一坐标系中的位置。
 * imageScale/imageOffset 预留给“裁剪页支持图片缩放/平移”的复杂场景；当前裁剪页保持 1f/Zero。
 */
data class ImageCropRequest(
    val sourceUri: Uri,
    val baseImageBounds: Rect,
    val imageScale: Float,
    val imageOffset: Offset,
    val cropRect: Rect
)

/**
 * 原图像素坐标系里的裁剪矩形。
 *
 * left/top/width/height 最终会直接传给 Canvas/Matrix 渲染输出。
 */
private data class SourceCropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

private fun ImageCropRequest.toSourceCropRect(sourceWidth: Int, sourceHeight: Int): SourceCropRect {
    // 先根据图片变换参数得到“当前真实显示区域”，当前裁剪页未缩放时等于 baseImageBounds。
    val transformedBounds = baseImageBounds.transform(imageScale, imageOffset)
    require(!transformedBounds.isEmpty) { "变换后的图片显示区域为空" }

    // 将屏幕裁剪框换算成原图归一化坐标，再映射到原图像素坐标。
    // 例如 normalizedLeft=0.25 表示裁剪框左边界落在图片显示宽度的 25% 位置。
    val normalizedLeft = ((cropRect.left - transformedBounds.left) / transformedBounds.width).coerceIn(0f, 1f)
    val normalizedTop = ((cropRect.top - transformedBounds.top) / transformedBounds.height).coerceIn(0f, 1f)
    val normalizedRight = ((cropRect.right - transformedBounds.left) / transformedBounds.width).coerceIn(0f, 1f)
    val normalizedBottom = ((cropRect.bottom - transformedBounds.top) / transformedBounds.height).coerceIn(0f, 1f)

    // coerceIn 保证浮点误差或边界拖动不会生成越界像素坐标。
    val left = (normalizedLeft * sourceWidth).roundToInt().coerceIn(0, sourceWidth - 1)
    val top = (normalizedTop * sourceHeight).roundToInt().coerceIn(0, sourceHeight - 1)
    val right = (normalizedRight * sourceWidth).roundToInt().coerceIn(left + 1, sourceWidth)
    val bottom = (normalizedBottom * sourceHeight).roundToInt().coerceIn(top + 1, sourceHeight)

    return SourceCropRect(
        left = left,
        top = top,
        width = right - left,
        height = bottom - top
    )
}

/**
 * 计算图片显示区域经过缩放和平移后的矩形。
 *
 * 这个方法目前主要服务导出管线的扩展性：当裁剪页未来支持图片缩放、旋转或位移时，
 * 裁剪坐标映射仍然可以沿用同一套入口。
 */
fun Rect.transform(scale: Float, offset: Offset): Rect {
    val center = center
    val targetWidth = width * scale
    val targetHeight = height * scale
    val left = center.x - targetWidth / 2f + offset.x
    val top = center.y - targetHeight / 2f + offset.y
    return Rect(
        left = left,
        top = top,
        right = left + targetWidth,
        bottom = top + targetHeight
    )
}
