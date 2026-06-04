package com.ethan.quickcrop.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

object MediaLibraryRepository {
    private const val TAG = "MediaLibraryRepo"
    private const val DEFAULT_LIMIT = 80

    suspend fun loadMediaItems(
        context: Context,
        limit: Int = DEFAULT_LIMIT
    ): List<GalleryMediaItem> = withContext(Dispatchers.IO) {
        val images = loadImages(context, limit)
        val videos = loadVideos(context, limit)

        (images + videos)
            .sortedByDescending { it.dateAddedMs }
            .take(limit)
    }

    suspend fun loadThumbnail(
        context: Context,
        item: GalleryMediaItem,
        sizePx: Int
    ): Bitmap? = loadThumbnail(
        context = context,
        uri = item.uri,
        isVideo = item.isVideo,
        sizePx = sizePx
    )

    suspend fun loadThumbnail(
        context: Context,
        uri: Uri,
        isVideo: Boolean,
        sizePx: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val safeSize = max(sizePx, 1)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(
                    uri,
                    android.util.Size(safeSize, safeSize),
                    null
                )
            } else {
                loadLegacyThumbnail(context.contentResolver, uri, isVideo, safeSize)
            }
        } catch (throwable: Throwable) {
            Log.w(TAG, "加载缩略图失败：$uri", throwable)
            if (isVideo) {
                // 系统缩略图接口偶发失败时，再尝试直接读取视频关键帧。
                loadLegacyThumbnail(context.contentResolver, uri, isVideo, safeSize)
            } else {
                null
            }
        }
    }

    private fun loadImages(
        context: Context,
        limit: Int
    ): List<GalleryMediaItem> = queryMedia(
        context = context,
        collection = imagesCollection(),
        isVideo = false,
        limit = limit,
        projection = imageProjection()
    )

    private fun loadVideos(
        context: Context,
        limit: Int
    ): List<GalleryMediaItem> = queryMedia(
        context = context,
        collection = videosCollection(),
        isVideo = true,
        limit = limit,
        projection = videoProjection()
    )

    private fun queryMedia(
        context: Context,
        collection: Uri,
        isVideo: Boolean,
        limit: Int,
        projection: Array<String>
    ): List<GalleryMediaItem> {
        val items = mutableListOf<GalleryMediaItem>()
        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext() && items.size < limit) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val uri = if (isVideo) {
                        Uri.withAppendedPath(videosCollection(), id.toString())
                    } else {
                        Uri.withAppendedPath(imagesCollection(), id.toString())
                    }
                    val displayName = cursor.getStringOrNull(MediaStore.MediaColumns.DISPLAY_NAME)
                        ?: if (isVideo) "未命名视频" else "未命名图片"
                    val mimeType = cursor.getStringOrNull(MediaStore.MediaColumns.MIME_TYPE)
                        ?: if (isVideo) "video/*" else "image/*"
                    val dateAddedSeconds = cursor.getLongOrDefault(MediaStore.MediaColumns.DATE_ADDED)
                    val sizeBytes = cursor.getLongOrDefault(MediaStore.MediaColumns.SIZE)
                    val width = cursor.getIntOrDefault(MediaStore.MediaColumns.WIDTH)
                    val height = cursor.getIntOrDefault(MediaStore.MediaColumns.HEIGHT)
                    val durationMs = if (isVideo) {
                        cursor.getLongOrDefault(MediaStore.Video.VideoColumns.DURATION)
                    } else {
                        0L
                    }

                    items += GalleryMediaItem(
                        uri = uri,
                        displayName = displayName,
                        mimeType = mimeType,
                        dateAddedMs = dateAddedSeconds * 1000L,
                        sizeBytes = sizeBytes,
                        width = width,
                        height = height,
                        durationMs = durationMs,
                        isVideo = isVideo
                    )
                }
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "读取媒体列表失败", throwable)
        }
        return items
    }

    private fun loadLegacyThumbnail(
        resolver: android.content.ContentResolver,
        uri: Uri,
        isVideo: Boolean,
        sizePx: Int
    ): Bitmap? {
        return if (isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    retriever.setDataSource(descriptor.fileDescriptor)
                    val source = retriever.getFrameAtTime(
                        0L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    ) ?: return null
                    scaleBitmapToSize(source, sizePx)
                }
            } catch (throwable: Throwable) {
                Log.w(TAG, "旧版本视频缩略图读取失败：$uri", throwable)
                null
            } finally {
                try {
                    retriever.release()
                } catch (releaseError: Throwable) {
                    Log.w(TAG, "释放 retriever 失败", releaseError)
                }
            }
        } else {
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }
    }

    private fun scaleBitmapToSize(bitmap: Bitmap, sizePx: Int): Bitmap {
        val target = sizePx.coerceAtLeast(1)
        val ratio = max(bitmap.width, bitmap.height).toFloat() / target.toFloat()
        if (ratio <= 1f) {
            return bitmap
        }

        val width = (bitmap.width / ratio).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height / ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun imageProjection(): Array<String> = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.WIDTH,
        MediaStore.MediaColumns.HEIGHT
    )

    private fun videoProjection(): Array<String> = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.WIDTH,
        MediaStore.MediaColumns.HEIGHT,
        MediaStore.Video.VideoColumns.DURATION
    )

    private fun imagesCollection(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun videosCollection(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun android.database.Cursor.getStringOrNull(columnName: String): String? {
        val columnIndex = getColumnIndex(columnName)
        return if (columnIndex >= 0 && !isNull(columnIndex)) {
            getString(columnIndex)
        } else {
            null
        }
    }

    private fun android.database.Cursor.getLongOrDefault(columnName: String): Long {
        val columnIndex = getColumnIndex(columnName)
        return if (columnIndex >= 0 && !isNull(columnIndex)) {
            getLong(columnIndex)
        } else {
            0L
        }
    }

    private fun android.database.Cursor.getIntOrDefault(columnName: String): Int {
        val columnIndex = getColumnIndex(columnName)
        return if (columnIndex >= 0 && !isNull(columnIndex)) {
            getInt(columnIndex)
        } else {
            0
        }
    }
}

data class GalleryMediaItem(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val dateAddedMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val isVideo: Boolean
)
