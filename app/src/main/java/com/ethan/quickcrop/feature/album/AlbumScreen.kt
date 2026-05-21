package com.ethan.quickcrop.feature.album

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ethan.quickcrop.core.media.GalleryMediaItem
import com.ethan.quickcrop.core.media.MediaLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class GalleryFilter(
    val title: String
) {
    Recent("最近"),
    Video("视频"),
    Image("图片")
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AlbumScreen(
    onVideoClick: (Uri) -> Unit,
    onImageClick: (Uri) -> Unit
) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf(GalleryFilter.Recent) }
    var items by remember { mutableStateOf<List<GalleryMediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("正在准备媒体库。") }
    var permissionGranted by remember { mutableStateOf(hasMediaPermission(context)) }
    var permissionRequested by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionGranted = hasMediaPermission(context, result)
        statusMessage = if (permissionGranted) {
            "权限已授予，正在加载本地媒体。"
        } else {
            "没有读取相册权限，暂时无法展示本地媒体。"
        }
    }

    LaunchedEffect(permissionGranted, reloadToken) {
        if (!permissionGranted) {
            return@LaunchedEffect
        }

        isLoading = true
        statusMessage = "正在扫描相册中的图片和视频..."
        try {
            items = MediaLibraryRepository.loadMediaItems(context)
            statusMessage = if (items.isEmpty()) {
                "没有扫描到本地媒体。"
            } else {
                "已加载 ${items.size} 个媒体项目。"
            }
        } catch (throwable: Throwable) {
            statusMessage = "读取相册失败：${throwable.message ?: "未知错误"}"
            items = emptyList()
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(permissionRequested) {
        if (permissionGranted || permissionRequested) {
            return@LaunchedEffect
        }
        permissionRequested = true
        requestPermissionsLauncher.launch(requiredPermissions())
    }

    val filteredItems = remember(items, filter) {
        when (filter) {
            GalleryFilter.Recent -> items
            GalleryFilter.Video -> items.filter { it.isVideo }
            GalleryFilter.Image -> items.filterNot { it.isVideo }
        }
    }

    val videoCount = remember(items) { items.count { it.isVideo } }
    val imageCount = remember(items) { items.count { !it.isVideo } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF111111),
                            Color(0xFF050505),
                            Color(0xFF0E0E0E)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = Color(0xFF171717),
                    tonalElevation = 4.dp,
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "QuickCrop 相册",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Text(
                            text = "这里展示设备里的图片和视频，点开后分别进入对应的编辑页。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB9B9B9)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SummaryPill(title = "最近", value = items.size)
                            SummaryPill(title = "视频", value = videoCount)
                            SummaryPill(title = "图片", value = imageCount)
                        }

                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9E9E9E)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GalleryTab(
                        title = "最近",
                        selected = filter == GalleryFilter.Recent,
                        onClick = { filter = GalleryFilter.Recent }
                    )
                    GalleryTab(
                        title = "视频",
                        selected = filter == GalleryFilter.Video,
                        onClick = { filter = GalleryFilter.Video }
                    )
                    GalleryTab(
                        title = "图片",
                        selected = filter == GalleryFilter.Image,
                        onClick = { filter = GalleryFilter.Image }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    AssistChip(
                        onClick = {
                            permissionRequested = false
                            requestPermissionsLauncher.launch(requiredPermissions())
                        },
                        label = { Text("刷新权限") }
                    )
                }

                if (!permissionGranted) {
                    PermissionCard(
                        onRequestPermission = {
                            permissionRequested = false
                            requestPermissionsLauncher.launch(requiredPermissions())
                        }
                    )
                } else if (isLoading) {
                    LoadingCard()
                } else if (filteredItems.isEmpty()) {
                    EmptyGalleryCard(
                        filter = filter,
                        onRetry = {
                            reloadToken += 1
                            statusMessage = "正在重新扫描相册..."
                        }
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = filteredItems,
                            key = { item -> item.uri.toString() }
                        ) { item ->
                            GalleryMediaCard(
                                item = item,
                                onClick = {
                                    if (item.isVideo) {
                                        onVideoClick(item.uri)
                                    } else {
                                        onImageClick(item.uri)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryPill(
    title: String,
    value: Int
) {
    Surface(
        color = Color(0xFF262626),
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = Color(0xFFBDBDBD), style = MaterialTheme.typography.labelMedium)
            Text(text = value.toString(), color = Color.White, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun GalleryTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) Color(0xFF7A5E52) else Color(0xFF1D1D1D),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = title,
            color = if (selected) Color.White else Color(0xFFB9B9B9),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun GalleryMediaCard(
    item: GalleryMediaItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val thumbnailSize = 320
    val thumbnail by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = item.uri,
        key2 = thumbnailSize
    ) {
        value = withContext(Dispatchers.IO) {
            MediaLibraryRepository.loadThumbnail(context, item, thumbnailSize)
        }
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191919)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.86f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF2A2A2A))
            ) {
                if (thumbnail != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumbnail!!,
                        contentDescription = item.displayName,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFF0C7A5))
                    }
                }

                Surface(
                    color = if (item.isVideo) Color(0xCCD87A5F) else Color(0xCC4C6A7A),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Text(
                        text = if (item.isVideo) "视频" else "图片",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                if (item.isVideo) {
                    Surface(
                        color = Color(0x99000000),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "▶",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }

                    Surface(
                        color = Color(0xCC000000),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = formatDuration(item.durationMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(if (item.isVideo) "视频" else "图片")
                        append(" · ")
                        append(formatSize(item.sizeBytes))
                    },
                    color = Color(0xFF9C9C9C),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    onRequestPermission: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1D1D)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("需要相册权限", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "为了展示你设备上的视频和图片，需要允许读取媒体权限。授权后会在这里显示一个自定义相册页。",
                color = Color(0xFFB8B8B8),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onRequestPermission) {
                Text("去授权")
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1D1D)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(color = Color(0xFFF0C7A5))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("正在加载媒体", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("请稍等，正在读取本地图片和视频。", color = Color(0xFFB8B8B8))
            }
        }
    }
}

@Composable
private fun EmptyGalleryCard(
    filter: GalleryFilter,
    onRetry: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1D1D)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("没有找到内容", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when (filter) {
                    GalleryFilter.Recent -> "当前相册里没有可显示的图片或视频。"
                    GalleryFilter.Video -> "当前没有可显示的视频。"
                    GalleryFilter.Image -> "当前没有可显示的图片。"
                },
                color = Color(0xFFB8B8B8)
            )
            OutlinedButton(onClick = onRetry) {
                Text("重新扫描")
            }
        }
    }
}

private fun hasMediaPermission(
    context: Context,
    permissionsResult: Map<String, Boolean>? = null
): Boolean {
    val permissions = requiredPermissions()
    return permissions.all { permission ->
        permissionsResult?.get(permission)
            ?: (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
}

private fun requiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun formatDuration(durationMs: Long): String {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val totalSeconds = safeDuration / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.CHINA, "%02d:%02d", minutes, seconds)
}

private fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) {
        return "未知大小"
    }
    val sizeMb = sizeBytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.CHINA, "%.1f MB", sizeMb)
}
