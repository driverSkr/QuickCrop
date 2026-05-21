package com.ethan.quickcrop.core.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import com.ethan.quickcrop.core.model.ThumbnailFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoPreviewRepository {
    private const val TAG = "VideoPreviewRepo"

    suspend fun loadPreviewFrames(
        context: Context,
        uri: Uri,
        frameCount: Int = 8
    ): PreviewMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            val videoWidth = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0
            val videoHeight = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0
            val videoRotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0
            val displayAspectRatio = calculateDisplayAspectRatio(
                width = videoWidth,
                height = videoHeight,
                rotationDegrees = videoRotation
            )

            val safeFrameCount = frameCount.coerceAtLeast(4)
            val frames = if (durationMs > 0L) {
                (0 until safeFrameCount).map { index ->
                    val timeMs = if (safeFrameCount == 1) {
                        0L
                    } else {
                        durationMs * index / (safeFrameCount - 1)
                    }
                    val bitmap = retriever.getFrameAtTime(
                        timeMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    ThumbnailFrame(
                        timeMs = timeMs,
                        bitmap = checkNotNull(bitmap) {
                            "无法从视频中提取缩略图：$uri"
                        }.asImageBitmap()
                    )
                }
            } else {
                emptyList()
            }

            PreviewMetadata(
                durationMs = durationMs,
                frames = frames,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                videoRotationDegrees = videoRotation,
                displayAspectRatio = displayAspectRatio
            )
        } catch (throwable: Throwable) {
            Log.e(TAG, "读取视频预览失败", throwable)
            PreviewMetadata(
                durationMs = 0L,
                frames = emptyList(),
                errorMessage = throwable.message
            )
        } finally {
            try {
                retriever.release()
            } catch (releaseError: Throwable) {
                Log.w(TAG, "释放 MediaMetadataRetriever 失败", releaseError)
            }
        }
    }

    data class PreviewMetadata(
        val durationMs: Long,
        val frames: List<ThumbnailFrame>,
        val videoWidth: Int = 0,
        val videoHeight: Int = 0,
        val videoRotationDegrees: Int = 0,
        val displayAspectRatio: Float = 16f / 9f,
        val errorMessage: String? = null
    )
}

private fun calculateDisplayAspectRatio(
    width: Int,
    height: Int,
    rotationDegrees: Int
): Float {
    if (width <= 0 || height <= 0) {
        return 16f / 9f
    }

    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    val displayWidth = if (normalizedRotation == 90 || normalizedRotation == 270) height else width
    val displayHeight = if (normalizedRotation == 90 || normalizedRotation == 270) width else height
    return displayWidth.toFloat() / displayHeight.toFloat()
}
