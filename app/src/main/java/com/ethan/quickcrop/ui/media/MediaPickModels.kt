package com.ethan.quickcrop.ui.media

import android.net.Uri

internal data class MediaPhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val width: Int,
    val height: Int,
    val bucketId: String,
    val bucketName: String,
    val dateAdded: Long,
    val durationMs: Long = 0L,
    val isVideo: Boolean = false
)

internal data class MediaAlbum(
    val id: String,
    val name: String,
    val count: Int,
    val coverUri: Uri,
    val latestDateAdded: Long
)
