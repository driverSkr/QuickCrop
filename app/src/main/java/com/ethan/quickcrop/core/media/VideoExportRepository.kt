package com.ethan.quickcrop.core.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.Transformer
import com.ethan.quickcrop.core.model.TrimRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.annotation.OptIn(UnstableApi::class)
object VideoExportRepository {
    private const val TAG = "VideoExportRepo"

    suspend fun exportTrimmedVideo(
        context: Context,
        sourceUri: Uri,
        trimRange: TrimRange
    ): Uri = withContext(Dispatchers.Main.immediate) {
        val fileName = buildString {
            append("quickcrop_trim_")
            append(SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()))
            append(".mp4")
        }
        val tempFile = File(context.cacheDir, fileName)

        val exportedTempFile = suspendCancellableCoroutine<File> { continuation ->
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: androidx.media3.transformer.Composition, result: androidx.media3.transformer.ExportResult) {
                        Log.i(TAG, "视频临时导出完成：${tempFile.absolutePath}")
                        if (continuation.isActive) {
                            continuation.resume(tempFile)
                        }
                    }

                    override fun onError(
                        composition: androidx.media3.transformer.Composition,
                        result: androidx.media3.transformer.ExportResult,
                        exportException: ExportException
                    ) {
                        Log.e(TAG, "视频导出失败", exportException)
                        if (continuation.isActive) {
                            continuation.resumeWithException(exportException)
                        }
                    }
                })
                .build()

            val clippedMediaItem = MediaItem.Builder()
                .setUri(sourceUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(trimRange.startMs)
                        .setEndPositionMs(trimRange.endMs)
                        .build()
                )
                .build()

            continuation.invokeOnCancellation {
                Log.w(TAG, "视频导出被取消")
                transformer.cancel()
            }

            try {
                transformer.start(clippedMediaItem, tempFile.absolutePath)
            } catch (throwable: Throwable) {
                Log.e(TAG, "启动视频导出失败", throwable)
                if (continuation.isActive) {
                    continuation.resumeWithException(throwable)
                }
            }
        }

        withContext(Dispatchers.IO) {
            val outputUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveVideoToGallery(context, fileName, exportedTempFile)
            } else {
                saveVideoToLegacyStorage(context, fileName, exportedTempFile)
            }
            if (exportedTempFile.exists() && !exportedTempFile.delete()) {
                Log.w(TAG, "删除临时视频文件失败：${exportedTempFile.absolutePath}")
            }
            Log.i(TAG, "视频导出完成并保存到相册：$outputUri")
            outputUri
        }
    }
}

private fun saveVideoToGallery(
    context: Context,
    fileName: String,
    sourceFile: File
): Uri {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_MOVIES + File.separator + "QuickCrop"
        )
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val uri = resolver.insert(collection, contentValues)
        ?: throw IllegalStateException("无法创建视频相册条目")

    try {
        resolver.openOutputStream(uri)?.use { output ->
            FileInputStream(sourceFile).use { input ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("无法打开视频输出流")

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

private fun saveVideoToLegacyStorage(
    context: Context,
    fileName: String,
    sourceFile: File
): Uri {
    val outputDirectory = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
        "QuickCrop"
    )
    if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
        throw IllegalStateException("无法创建视频输出目录：${outputDirectory.absolutePath}")
    }

    val outputFile = File(outputDirectory, fileName)
    FileInputStream(sourceFile).use { input ->
        FileOutputStream(outputFile).use { output ->
            input.copyTo(output)
        }
    }

    android.media.MediaScannerConnection.scanFile(
        context,
        arrayOf(outputFile.absolutePath),
        arrayOf("video/mp4"),
        null
    )
    return Uri.fromFile(outputFile)
}
