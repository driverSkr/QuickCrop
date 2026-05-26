package com.ethan.quickcrop.ui.media.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethan.quickcrop.R

/**
 * 加载状态子页面：用于相册权限确认或媒体数据读取中的等待展示。
 */
@Composable
internal fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}

/**
 * 空相册状态子页面：用于已获得权限但没有可展示照片时提示用户。
 */
@Composable
internal fun EmptyPhotoState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painter = painterResource(id = R.drawable.img_empty_photo), contentScale = ContentScale.Crop, contentDescription = null, modifier = Modifier.size(80.dp))
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.media_picker_no_photos),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.W700
            )
        }
    }
}

/**
 * 无照片权限状态子页面：用于提示用户授予照片权限并提供跳转系统设置入口。
 */
@Composable
internal fun PermissionDeniedState(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth().offset(y = (-50).dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painter = painterResource(id = R.drawable.img_empty_photo), contentScale = ContentScale.Crop, contentDescription = null, modifier = Modifier.size(80.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.media_picker_no_photos),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.W700
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.media_picker_permission_message),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                fontWeight = FontWeight.W400,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 70.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clickable { onOpenSettings() }
                    .height(40.dp)
                    .background(Color.White, RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.media_picker_go_settings),
                    color = Color.Black,
                    fontSize = 14.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.W400
                )
            }
        }
    }
}
