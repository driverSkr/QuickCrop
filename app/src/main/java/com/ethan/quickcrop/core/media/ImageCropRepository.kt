package com.ethan.quickcrop.core.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.ethan.quickcrop.core.model.CropAspectRatio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageCropRepository {
    private const val TAG = "ImageCropRepo"

    suspend fun loadBitmap(
        context: Context,
        uri: Uri
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "读取图片失败", throwable)
            null
        }
    }

    suspend fun exportCropBitmap(
        context: Context,
        sourceBitmap: Bitmap,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
        outputAspectRatio: CropAspectRatio
    ): Uri = withContext(Dispatchers.IO) {
        val fileName = buildString {
            append("quickcrop_crop_")
            append(SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()))
            append(".jpg")
        }

        val fitScale = min(
            viewportWidthPx.toFloat() / sourceBitmap.width.toFloat(),
            viewportHeightPx.toFloat() / sourceBitmap.height.toFloat()
        )
        val totalScale = fitScale * zoom.coerceAtLeast(1f)
        val displayedWidth = sourceBitmap.width * totalScale
        val displayedHeight = sourceBitmap.height * totalScale
        val left = (viewportWidthPx - displayedWidth) / 2f + offsetX
        val top = (viewportHeightPx - displayedHeight) / 2f + offsetY

        val sourceLeft = max(0f, (-left) / totalScale)
        val sourceTop = max(0f, (-top) / totalScale)
        val sourceRight = min(
            sourceBitmap.width.toFloat(),
            (viewportWidthPx - left) / totalScale
        )
        val sourceBottom = min(
            sourceBitmap.height.toFloat(),
            (viewportHeightPx - top) / totalScale
        )

        val startX = floor(sourceLeft).roundToInt().coerceIn(0, sourceBitmap.width - 1)
        val startY = floor(sourceTop).roundToInt().coerceIn(0, sourceBitmap.height - 1)
        val endX = ceil(sourceRight).roundToInt().coerceIn(startX + 1, sourceBitmap.width)
        val endY = ceil(sourceBottom).roundToInt().coerceIn(startY + 1, sourceBitmap.height)
        val cropWidth = max(1, endX - startX)
        val cropHeight = max(1, endY - startY)

        val cropped = Bitmap.createBitmap(sourceBitmap, startX, startY, cropWidth, cropHeight)
        val targetWidth = max(1, viewportWidthPx)
        val targetHeight = max(1, viewportHeightPx)
        val scaled = if (cropped.width == targetWidth && cropped.height == targetHeight) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
        }

        val outputUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageToGallery(context, fileName, scaled)
        } else {
            saveImageToLegacyStorage(context, fileName, scaled)
        }

        Log.i(
            TAG,
            "图片导出完成：$outputUri，ratio=${outputAspectRatio.label}"
        )
        outputUri
    }
}

private fun saveImageToGallery(
    context: Context,
    fileName: String,
    bitmap: Bitmap
): Uri {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_PICTURES + File.separator + "QuickCrop"
        )
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val uri = resolver.insert(collection, contentValues)
        ?: throw IllegalStateException("无法创建图片相册条目")

    try {
        resolver.openOutputStream(uri)?.use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                throw IllegalStateException("图片压缩失败")
            }
        } ?: throw IllegalStateException("无法打开图片输出流")

        val pendingValues = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        resolver.update(uri, pendingValues, null, null)
        return uri
    } catch (throwable: Throwable) {
        resolver.delete(uri, null, null)
        throw throwable
    }
}

private fun saveImageToLegacyStorage(
    context: Context,
    fileName: String,
    bitmap: Bitmap
): Uri {
    val outputDirectory = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        "QuickCrop"
    )
    if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
        throw IllegalStateException("无法创建图片输出目录：${outputDirectory.absolutePath}")
    }

    val outputFile = File(outputDirectory, fileName)
    FileOutputStream(outputFile).use { output ->
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
            throw IllegalStateException("图片压缩失败")
        }
    }

    android.media.MediaScannerConnection.scanFile(
        context,
        arrayOf(outputFile.absolutePath),
        arrayOf("image/jpeg"),
        null
    )
    return Uri.fromFile(outputFile)
}
