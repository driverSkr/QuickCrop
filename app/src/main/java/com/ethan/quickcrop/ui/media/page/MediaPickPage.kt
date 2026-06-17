package com.ethan.quickcrop.ui.media.page

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ethan.quickcrop.R
import com.ethan.quickcrop.ui.media.MediaAlbum
import com.ethan.quickcrop.ui.media.MediaPickType
import com.ethan.quickcrop.ui.media.MediaPhoto
import com.ethan.quickcrop.ui.media.view.AlbumList
import com.ethan.quickcrop.ui.media.view.EmptyPhotoState
import com.ethan.quickcrop.ui.media.view.LoadingState
import com.ethan.quickcrop.ui.media.view.MediaPickerTopBar
import com.ethan.quickcrop.ui.media.view.MediaPreviewPage
import com.ethan.quickcrop.ui.media.view.PermissionDeniedState
import com.ethan.quickcrop.ui.media.view.PhotoGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

private const val TAG = "MediaPickPage"
private const val RECENT_ALBUM_ID = "recent"
private const val UNKNOWN_ALBUM_ID = "unknown"
private const val MAX_IMAGE_WIDTH = 8064
private const val MAX_IMAGE_HEIGHT = 6048
private const val MAX_IMAGE_LONG_EDGE = 8064

// 相册权限的页面态，用于驱动无权限、加载中和相册内容三类主展示。
private enum class PhotoPermissionState {
    Checking,
    Granted,
    Denied
}

// 导入校验通过后的结果，path 指向已经准备好的本地缓存文件。
private data class ImportSuccess(val path: String)

// 导入校验失败信息，统一携带提示文案，避免校验分支直接散落 Toast。
private data class ImportFailure(val messageRes: Int)

// 导入链路的统一返回值，保证调用方只处理成功跳转或失败提示。
private sealed class ImportResult {
    data class Success(val value: ImportSuccess) : ImportResult()
    data class Failure(val value: ImportFailure) : ImportResult()
}

private data class ImportImageFormat(
    val supported: Boolean,
    val outputExtension: String,
    val needsJpegConversion: Boolean
)

@Composable
fun MediaPickPage(
    pickType: MediaPickType = MediaPickType.IMAGE,
    onClose: () -> Unit,
    onImageImportReady: (String) -> Unit,
    onVideoPickReady: (Uri) -> Unit = {},
    permissionRequestedBeforeOpen: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionState by remember { mutableStateOf(PhotoPermissionState.Checking) }
    var photos by remember { mutableStateOf<List<MediaPhoto>>(emptyList()) }
    var albums by remember { mutableStateOf<List<MediaAlbum>>(emptyList()) }
    var selectedAlbumId by rememberSaveable { mutableStateOf(RECENT_ALBUM_ID) }
    val gridFirstVisiblePosition = rememberSaveable { mutableIntStateOf(0) }
    var showAlbumList by rememberSaveable { mutableStateOf(false) }
    var previewStartIndex by remember { mutableStateOf<Int?>(null) }
    var isLoadingPhotos by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    fun refreshMedia() {
        scope.launch {
            isLoadingPhotos = true
            val media = withContext(Dispatchers.IO) { loadMediaItems(context, pickType) }
            Log.d(TAG, "刷新媒体列表完成: pickType=$pickType, count=${media.size}")
            photos = media
            albums = buildMediaAlbums(context, media)
            if (selectedAlbumId != RECENT_ALBUM_ID && albums.none { it.id == selectedAlbumId }) {
                selectedAlbumId = RECENT_ALBUM_ID
            }
            isLoadingPhotos = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantMap ->
        val granted = grantMap.values.any { it } || hasMediaPermission(context, pickType)
        if (granted) {
            permissionState = PhotoPermissionState.Granted
            refreshMedia()
        } else {
            permissionState = PhotoPermissionState.Denied
            isLoadingPhotos = false
        }
    }

    fun requestPermissionIfNeeded() {
        if (hasMediaPermission(context, pickType)) {
            permissionState = PhotoPermissionState.Granted
            refreshMedia()
        } else {
            permissionState = PhotoPermissionState.Checking
            isLoadingPhotos = true
            permissionLauncher.launch(requiredMediaPermissions(pickType))
        }
    }

    fun openAppSettings() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        }.onFailure { throwable ->
            Log.e(TAG, "打开照片权限设置失败", throwable)
            Toast.makeText(context, R.string.media_picker_open_settings_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun importPhoto(photo: MediaPhoto) {
        if (isImporting) return
        scope.launch {
            isImporting = true
            val result = withContext(Dispatchers.IO) {
                validateAndPrepareImport(context, photo)
            }
            isImporting = false
            when (result) {
                is ImportResult.Success -> {
                    // 校验和本地缓存准备成功后，再交给后续裁剪流程处理。
                    onImageImportReady(result.value.path)
                }
                is ImportResult.Failure -> {
                    Toast.makeText(context, result.value.messageRes, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(pickType) {
        if (permissionRequestedBeforeOpen) {
            if (hasMediaPermission(context, pickType)) {
                permissionState = PhotoPermissionState.Granted
                refreshMedia()
            } else {
                permissionState = PhotoPermissionState.Denied
                isLoadingPhotos = false
            }
        } else {
            requestPermissionIfNeeded()
        }
    }

    DisposableEffect(lifecycleOwner, permissionState, pickType) {
        val observer = LifecycleEventObserver { _, event ->
            // 从系统设置返回时重新检查权限，用户打开照片权限后立即刷新相册内容。
            if (event == Lifecycle.Event.ON_RESUME &&
                permissionState == PhotoPermissionState.Denied &&
                hasMediaPermission(context, pickType)
            ) {
                permissionState = PhotoPermissionState.Granted
                refreshMedia()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val currentAlbum = albums.firstOrNull { it.id == selectedAlbumId }
    val displayPhotos = remember(photos, selectedAlbumId) {
        if (selectedAlbumId == RECENT_ALBUM_ID) {
            photos
        } else {
            photos.filter { it.bucketId == selectedAlbumId }
        }
    }
    fun handleMediaClick(photo: MediaPhoto) {
        if (photo.isVideo) {
            onVideoPickReady(photo.uri)
            return
        }
        importPhoto(photo)
    }

    fun handleMediaPreviewClick(photo: MediaPhoto) {
        // 预览页支持图片和视频，索引直接基于当前相册展示列表计算。
        previewStartIndex = displayPhotos.indexOfFirst { it.id == photo.id && it.isVideo == photo.isVideo }.coerceAtLeast(0)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0C0C0F))) {
        Column(modifier = Modifier.fillMaxSize()) {
            MediaPickerTopBar(
                title = currentAlbum?.name ?: stringResource(R.string.media_picker_recent),
                expanded = showAlbumList,
                showAlbumEntrance = permissionState == PhotoPermissionState.Granted && photos.isNotEmpty(),
                onClose = onClose,
                onTitleClick = { showAlbumList = !showAlbumList }
            )

            when {
                permissionState == PhotoPermissionState.Denied -> {
                    PermissionDeniedState(
                        pickType = pickType,
                        onOpenSettings = { openAppSettings() },
                        modifier = Modifier.weight(1f)
                    )
                }
                isLoadingPhotos || permissionState == PhotoPermissionState.Checking -> {
                    LoadingState(modifier = Modifier.weight(1f))
                }
                photos.isEmpty() -> {
                    EmptyPhotoState(
                        pickType = pickType,
                        modifier = Modifier.weight(1f)
                    )
                }
                showAlbumList -> {
                    AlbumList(
                        albums = albums,
                        selectedAlbumId = selectedAlbumId,
                        onAlbumClick = { album ->
                            // 切换相册后收起相册列表，网格区域展示所选相册图片。
                            selectedAlbumId = album.id
                            showAlbumList = false
                        },
                        modifier = Modifier.padding(top = 8.dp).weight(1f)
                    )
                }
                else -> {
                    PhotoGrid(
                        photos = displayPhotos,
                        firstVisiblePositionProvider = { gridFirstVisiblePosition.intValue },
                        onFirstVisiblePositionChange = { gridFirstVisiblePosition.intValue = it },
                        onPhotoClick = { photo -> handleMediaClick(photo) },
                        onPreviewClick = { photo -> handleMediaPreviewClick(photo) },
                        modifier = Modifier.padding(top = 8.dp).weight(1f)
                    )
                }
            }
        }

        // 预览页以浮层方式覆盖相册内容，关闭后底层网格和滚动位置保持不变。
        previewStartIndex?.let { initialIndex ->
            MediaPreviewPage(
                photos = displayPhotos,
                initialIndex = initialIndex,
                onClose = { previewStartIndex = null },
                onConfirm = { photo -> handleMediaClick(photo) }
            )
        }

        // 导入校验、缓存拷贝或 HEIC 转换期间展示全屏遮罩，防止重复点击。
        if (isImporting) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

// 根据入口类型返回媒体读取权限，保留 ALL 模式以支持图片和视频混合展示。
internal fun requiredMediaPermissions(pickType: MediaPickType): Array<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val mediaPermissions = when (pickType) {
        MediaPickType.IMAGE -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        MediaPickType.VIDEO -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        MediaPickType.ALL -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    }

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        mediaPermissions + Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    } else {
        mediaPermissions
    }
}

// 兼容旧调用点：默认请求图片权限。
internal fun requiredMediaPhotoPermissions(): Array<String> {
    return requiredMediaPermissions(MediaPickType.IMAGE)
}

// 判断是否已经拥有当前入口需要的媒体权限或 Android 14 的有限访问权限。
internal fun hasMediaPermission(context: Context, pickType: MediaPickType): Boolean {
    val imagePermissionGranted = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        }
        else -> {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    val videoPermissionGranted = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        }
        else -> {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    val limitedPhotoGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    return when (pickType) {
        MediaPickType.IMAGE -> imagePermissionGranted || limitedPhotoGranted
        // 视频入口不能直接复用“部分照片访问”状态，否则用户只授权过图片时会跳过视频权限请求，导致视频相册为空。
        MediaPickType.VIDEO -> videoPermissionGranted
        MediaPickType.ALL -> imagePermissionGranted || videoPermissionGranted || limitedPhotoGranted
    }
}

// 兼容旧调用点：默认按图片入口判断权限。
internal fun hasMediaPhotoPermission(context: Context): Boolean {
    return hasMediaPermission(context, MediaPickType.IMAGE)
}

// 从 MediaStore 按入口类型读取媒体，并按添加时间倒序生成网格数据源。
private fun loadMediaItems(context: Context, pickType: MediaPickType): List<MediaPhoto> {
    return when (pickType) {
        MediaPickType.IMAGE -> loadImageItems(context)
        MediaPickType.VIDEO -> loadVideoItems(context)
        MediaPickType.ALL -> loadImageItems(context) + loadVideoItems(context)
    }.sortedByDescending { it.sortDate }
}

// 兼容旧调用点：默认读取图片和视频混合列表。
private fun loadMediaPhotos(context: Context): List<MediaPhoto> {
    return loadMediaItems(context, MediaPickType.ALL)
}

private fun loadImageItems(context: Context): List<MediaPhoto> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.DATE_ADDED
    )
    val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "${MediaStore.Images.Media.IS_PENDING}=0"
    } else {
        null
    }
    val sortOrder = mediaSortOrder(
        dateTakenColumn = MediaStore.Images.Media.DATE_TAKEN,
        dateModifiedColumn = MediaStore.Images.Media.DATE_MODIFIED,
        dateAddedColumn = MediaStore.Images.Media.DATE_ADDED
    )

    return runCatching {
        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val bucketIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val dateTakenIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            buildList {
                while (cursor.moveToNext()) {
                    // 将每一行 MediaStore 结果转换成页面统一使用的 MediaPhoto 模型。
                    val id = cursor.getLong(idIndex)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val bucketId = cursor.getString(bucketIdIndex).orEmpty().ifBlank { UNKNOWN_ALBUM_ID }
                    val bucketName = cursor.getString(bucketNameIndex).orEmpty()
                        .ifBlank { context.getString(R.string.media_picker_unknown_album) }
                    val sortDate = resolveMediaSortDate(
                        dateTakenMs = cursor.getLong(dateTakenIndex),
                        dateModifiedSeconds = cursor.getLong(dateModifiedIndex),
                        dateAddedSeconds = cursor.getLong(dateAddedIndex)
                    )
                    add(
                        MediaPhoto(
                            id = id,
                            uri = uri,
                            displayName = cursor.getString(nameIndex).orEmpty(),
                            mimeType = cursor.getString(mimeIndex),
                            width = cursor.getInt(widthIndex),
                            height = cursor.getInt(heightIndex),
                            bucketId = bucketId,
                            bucketName = bucketName,
                            sortDate = sortDate,
                            durationMs = 0L,
                            isVideo = false
                        )
                    )
                }
            }
        }.orEmpty()
    }.onFailure { throwable ->
        Log.e(TAG, "读取相册图片失败", throwable)
    }.getOrDefault(emptyList())
}

private fun loadVideoItems(context: Context): List<MediaPhoto> {
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.MIME_TYPE,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.HEIGHT,
        MediaStore.Video.Media.BUCKET_ID,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.DATE_TAKEN,
        MediaStore.Video.Media.DATE_MODIFIED,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.DURATION
    )
    val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "${MediaStore.Video.Media.IS_PENDING}=0"
    } else {
        null
    }
    val sortOrder = mediaSortOrder(
        dateTakenColumn = MediaStore.Video.Media.DATE_TAKEN,
        dateModifiedColumn = MediaStore.Video.Media.DATE_MODIFIED,
        dateAddedColumn = MediaStore.Video.Media.DATE_ADDED
    )

    return runCatching {
        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val bucketIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val dateTakenIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            val dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val bucketId = cursor.getString(bucketIdIndex).orEmpty().ifBlank { UNKNOWN_ALBUM_ID }
                    val bucketName = cursor.getString(bucketNameIndex).orEmpty()
                        .ifBlank { context.getString(R.string.media_picker_unknown_album) }
                    val sortDate = resolveMediaSortDate(
                        dateTakenMs = cursor.getLong(dateTakenIndex),
                        dateModifiedSeconds = cursor.getLong(dateModifiedIndex),
                        dateAddedSeconds = cursor.getLong(dateAddedIndex)
                    )
                    add(
                        MediaPhoto(
                            id = id,
                            uri = uri,
                            displayName = cursor.getString(nameIndex).orEmpty(),
                            mimeType = cursor.getString(mimeIndex),
                            width = cursor.getInt(widthIndex),
                            height = cursor.getInt(heightIndex),
                            bucketId = bucketId,
                            bucketName = bucketName,
                            sortDate = sortDate,
                            durationMs = cursor.getLong(durationIndex),
                            isVideo = true
                        )
                    )
                }
            }
        }.orEmpty()
    }.onFailure { throwable ->
        Log.e(TAG, "读取相册视频失败", throwable)
    }.getOrDefault(emptyList())
}

// 基于媒体列表构建相册入口，第一项固定为最近项目，其余按相册最新媒体时间倒序。
private fun mediaSortOrder(dateTakenColumn: String, dateModifiedColumn: String, dateAddedColumn: String): String {
    return "$dateTakenColumn DESC, $dateModifiedColumn DESC, $dateAddedColumn DESC"
}

private fun resolveMediaSortDate(
    dateTakenMs: Long,
    dateModifiedSeconds: Long,
    dateAddedSeconds: Long
): Long {
    return when {
        dateTakenMs > 0L -> dateTakenMs
        dateModifiedSeconds > 0L -> dateModifiedSeconds * 1000L
        else -> dateAddedSeconds * 1000L
    }
}

private fun buildMediaAlbums(context: Context, photos: List<MediaPhoto>): List<MediaAlbum> {
    if (photos.isEmpty()) return emptyList()
    val recentAlbum = MediaAlbum(
        id = RECENT_ALBUM_ID,
        name = context.getString(R.string.media_picker_recent),
        count = photos.size,
        coverUri = photos.first().uri,
        coverIsVideo = photos.first().isVideo,
        latestSortDate = photos.first().sortDate
    )
    val bucketAlbums = photos
        .groupBy { it.bucketId }
        .mapNotNull { (bucketId, bucketPhotos) ->
            val cover = bucketPhotos.maxByOrNull { it.sortDate } ?: return@mapNotNull null
            MediaAlbum(
                id = bucketId,
                name = cover.bucketName,
                count = bucketPhotos.size,
                coverUri = cover.uri,
                coverIsVideo = cover.isVideo,
                latestSortDate = cover.sortDate
            )
        }
        .sortedByDescending { it.latestSortDate }
    return listOf(recentAlbum) + bucketAlbums
}

// 导入校验总入口：严格按格式、尺寸面积、最长边、本地缓存准备的顺序返回一个结果。
private fun validateAndPrepareImport(context: Context, photo: MediaPhoto): ImportResult {
    val format = resolveImportImageFormat(photo.displayName, photo.mimeType)
    if (!format.supported) {
        return ImportResult.Failure(ImportFailure(R.string.media_picker_format_not_supported))
    }

    val size = resolveImageSize(context, photo)
        ?: return ImportResult.Failure(ImportFailure(R.string.media_picker_format_not_supported))

    if (size.first.toLong() * size.second.toLong() > MAX_IMAGE_WIDTH.toLong() * MAX_IMAGE_HEIGHT.toLong()) {
        return ImportResult.Failure(ImportFailure(R.string.media_picker_size_exceed))
    }

    if (max(size.first, size.second) > MAX_IMAGE_LONG_EDGE) {
        return ImportResult.Failure(ImportFailure(R.string.media_picker_max_edge_exceed))
    }

    val importPath = if (format.needsJpegConversion) {
        convertToJpeg(context, photo)
    } else {
        copyToImportCache(context, photo, format.outputExtension)
    }

    return if (importPath.isNullOrBlank()) {
        ImportResult.Failure(ImportFailure(R.string.media_picker_import_failed))
    } else {
        ImportResult.Success(ImportSuccess(importPath))
    }
}

// 解析导入图片格式，保留原相册代码支持 JPG/PNG/WebP/HEIC/HEIF 的能力。
private fun resolveImportImageFormat(displayName: String, mimeType: String?): ImportImageFormat {
    val lowerMime = mimeType.orEmpty().lowercase()
    val extension = File(displayName).extension.lowercase()
    return when {
        lowerMime == "image/jpeg" || extension in setOf("jpg", "jpeg") -> {
            ImportImageFormat(supported = true, outputExtension = "jpg", needsJpegConversion = false)
        }
        lowerMime == "image/png" || extension == "png" -> {
            ImportImageFormat(supported = true, outputExtension = "png", needsJpegConversion = false)
        }
        lowerMime == "image/webp" || extension == "webp" -> {
            ImportImageFormat(supported = true, outputExtension = "webp", needsJpegConversion = false)
        }
        lowerMime in setOf("image/heic", "image/heif") || extension in setOf("heic", "heif") -> {
            ImportImageFormat(supported = true, outputExtension = "jpg", needsJpegConversion = true)
        }
        else -> ImportImageFormat(supported = false, outputExtension = "jpg", needsJpegConversion = false)
    }
}

// 解析图片尺寸：优先用 MediaStore 的宽高，缺失时再读取图片头或通过 ImageDecoder 获取。
private fun resolveImageSize(context: Context, photo: MediaPhoto): Pair<Int, Int>? {
    if (photo.width > 0 && photo.height > 0) {
        return photo.width to photo.height
    }

    runCatching {
        context.contentResolver.openInputStream(photo.uri)?.use { input ->
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(input, null, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                return options.outWidth to options.outHeight
            }
        }
    }.onFailure { throwable ->
        Log.e(TAG, "读取图片尺寸失败: ${photo.uri}", throwable)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        runCatching {
            var decodedSize: Pair<Int, Int>? = null
            val source = ImageDecoder.createSource(context.contentResolver, photo.uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decodedSize = info.size.width to info.size.height
                decoder.setTargetSize(1, 1)
            }
            return decodedSize
        }.onFailure { throwable ->
            Log.e(TAG, "ImageDecoder 读取图片尺寸失败: ${photo.uri}", throwable)
        }
    }
    return null
}

// 将无需转码的图片复制到应用缓存目录，后续裁剪页只处理本地文件路径。
private fun copyToImportCache(context: Context, photo: MediaPhoto, extension: String): String? {
    return runCatching {
        val outputFile = createImportFile(context, photo.displayName, extension)
        context.contentResolver.openInputStream(photo.uri)?.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null
        if (outputFile.length() > 0L) outputFile.absolutePath else null
    }.onFailure { throwable ->
        Log.e(TAG, "复制相册图片失败: ${photo.uri}", throwable)
    }.getOrNull()
}

// 将 HEIC/HEIF 等需要兼容处理的图片解码后压缩为 JPG，再交给导入流程。
private fun convertToJpeg(context: Context, photo: MediaPhoto): String? {
    return runCatching {
        val outputFile = createImportFile(context, photo.displayName, "jpg")
        val bitmap = decodeBitmapForImport(context, photo.uri) ?: return null
        try {
            outputFile.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
            }
        } finally {
            bitmap.recycle()
        }
        if (outputFile.length() > 0L) outputFile.absolutePath else null
    }.onFailure { throwable ->
        Log.e(TAG, "转换 HEIC/HEIF 图片失败: ${photo.uri}", throwable)
    }.getOrNull()
}

// 解码图片为 Bitmap：Android P 以上使用 ImageDecoder，旧系统使用 BitmapFactory。
private fun decodeBitmapForImport(context: Context, uri: Uri): Bitmap? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }
    }.onFailure { throwable ->
        Log.e(TAG, "解码导入图片失败: $uri", throwable)
    }.getOrNull()
}

// 为导入图片创建缓存文件名，清理非法字符并追加时间戳避免同名覆盖。
private fun createImportFile(context: Context, displayName: String, extension: String): File {
    val safeName = File(displayName).nameWithoutExtension.ifBlank { "media_pick" }
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
    val outputDir = File(context.externalCacheDir ?: context.cacheDir, "export").apply {
        if (!exists() && !mkdirs()) {
            Log.w(TAG, "创建相册导入缓存目录失败: $absolutePath")
        }
    }
    return File(outputDir, "${safeName}_${System.currentTimeMillis()}.$extension")
}
