package com.ethan.quickcrop.ui.media.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
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
                AsyncImage(
                    model = ImageRequest.Builder(context).data(album.coverUri).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF171719))
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
