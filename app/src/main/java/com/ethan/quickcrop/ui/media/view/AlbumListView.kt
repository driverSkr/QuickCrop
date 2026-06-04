package com.ethan.quickcrop.ui.media.view

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.ethan.quickcrop.core.media.MediaLibraryRepository
import com.ethan.quickcrop.ui.media.MediaAlbum

/**
 * 相册分类列表子页面：展示最近项目和各相册入口，并负责相册切换点击。
 */
@Composable
internal fun AlbumList(
    albums: List<MediaAlbum>,
    selectedAlbumId: String,
    onAlbumClick: (MediaAlbum) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = albums,
            key = { it.id },
            contentType = { "album" }
        ) { album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clickable { onAlbumClick(album) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumCoverImage(
                    album = album,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = album.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = if (album.id == selectedAlbumId) FontWeight.W700 else FontWeight.W600
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = album.count.toString(),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W400
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCoverImage(
    album: MediaAlbum,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coverModifier = modifier
        .clip(RoundedCornerShape(2.dp))
        .background(Color(0xFF171719))

    if (album.coverIsVideo) {
        var videoCover by remember(album.coverUri) { mutableStateOf<Bitmap?>(null) }

        LaunchedEffect(album.coverUri) {
            // 目录封面如果是视频，也显式取帧，保证下拉相册列表不会出现空白封面。
            videoCover = MediaLibraryRepository.loadThumbnail(
                context = context,
                uri = album.coverUri,
                isVideo = true,
                sizePx = 240
            )
        }

        if (videoCover != null) {
            Image(
                bitmap = videoCover!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = coverModifier
            )
        } else {
            Box(modifier = coverModifier)
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context).data(album.coverUri).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = coverModifier
        )
    }
}
