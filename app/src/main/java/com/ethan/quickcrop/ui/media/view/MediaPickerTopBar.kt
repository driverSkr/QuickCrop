package com.ethan.quickcrop.ui.media.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ethan.quickcrop.R

@Composable
internal fun MediaPickerTopBar(
    title: String,
    expanded: Boolean,
    showAlbumEntrance: Boolean,
    onClose: () -> Unit,
    onTitleClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(1f)
            .background(Color(0xFF0C0C0F))
            .statusBarsPadding()
    ) {
        // Keep the background under the status bar while only offsetting the toolbar content.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.svg_icon_back),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(32.dp)
                    .clickable { onClose() }
            )
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = showAlbumEntrance) { onTitleClick() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W700,
                    maxLines = 1
                )
                if (showAlbumEntrance) {
                    Spacer(modifier = Modifier.width(6.dp))
                    AlbumArrow(expanded = expanded)
                }
            }
        }
    }
}

@Composable
private fun AlbumArrow(expanded: Boolean) {
    Canvas(modifier = Modifier.size(10.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        if (expanded) {
            drawLine(Color.White, Offset(1.dp.toPx(), 7.dp.toPx()), Offset(5.dp.toPx(), 3.dp.toPx()), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(5.dp.toPx(), 3.dp.toPx()), Offset(9.dp.toPx(), 7.dp.toPx()), strokeWidth = stroke.width, cap = StrokeCap.Round)
        } else {
            drawLine(Color.White, Offset(1.dp.toPx(), 3.dp.toPx()), Offset(5.dp.toPx(), 7.dp.toPx()), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(5.dp.toPx(), 7.dp.toPx()), Offset(9.dp.toPx(), 3.dp.toPx()), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }
}
