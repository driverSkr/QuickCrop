package com.ethan.quickcrop.ui.media

import android.net.Uri

enum class MediaPickType {
    IMAGE,
    VIDEO,
    ALL;

    companion object {
        fun fromValue(value: String?): MediaPickType {
            return values().firstOrNull { it.name == value } ?: IMAGE
        }
    }
}

internal data class MediaPhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val width: Int,
    val height: Int,
    val bucketId: String,
    val bucketName: String,
    val sortDate: Long,
    val durationMs: Long = 0L,
    val isVideo: Boolean = false
)

internal data class MediaAlbum(
    val id: String,
    val name: String,
    val count: Int,
    val coverUri: Uri,
    val coverIsVideo: Boolean,
    val latestSortDate: Long
)
