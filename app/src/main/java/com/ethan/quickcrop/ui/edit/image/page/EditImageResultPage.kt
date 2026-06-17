package com.ethan.quickcrop.ui.edit.image.page

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethan.quickcrop.MainActivity
import com.ethan.quickcrop.R
import com.ethan.quickcrop.core.image.ImagePreviewDecoder
import com.ethan.quickcrop.extension.finishActivity
import com.ethan.quickcrop.ui.media.MediaPickActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "EditImageResultPage"

@Composable
fun EditImageResultPage(sourceUri: Uri?) {
    val context = LocalContext.current

    BackHandler(true) {
        context.finishActivity()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0F))
            .statusBarsPadding()
    ) {
        ExportSuccessPanel(
            exportedUri = sourceUri,
            onBackHome = {
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                context.finishActivity()
            },
            onContinueEdit = {
                MediaPickActivity.launch(context)
                context.finishActivity()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ExportSuccessPanel(
    exportedUri: Uri?,
    onBackHome: () -> Unit,
    onContinueEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 编辑页进入结果页前已经完成保存，这里只负责读取最终 URI 并展示保存结果。
    val exportedBitmap by produceState<Bitmap?>(initialValue = null, exportedUri) {
        value = exportedUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    ImagePreviewDecoder.decode(context = context.applicationContext, uri = uri)
                }.onFailure { throwable ->
                    Log.e(TAG, "读取编辑结果失败: $uri", throwable)
                }.getOrNull()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF18181B)),
            contentAlignment = Alignment.Center
        ) {
            if (exportedBitmap != null) {
                Image(
                    bitmap = exportedBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = if (exportedUri == null) "图片来源为空" else "图片加载中...",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(22.dp))
        FaIcon(iconRes = R.drawable.fa_check, tint = Color.White, modifier = Modifier.size(34.dp))
        Text(
            text = "导出成功！",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "图片已保存到相册",
            color = Color(0xFF9CA3AF),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 26.dp)
        )
        PrimaryBottomButton(text = "返回首页", onClick = onBackHome)
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1F2937))
                .clickable { onContinueEdit() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "继续编辑", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PrimaryBottomButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FaIcon(iconRes: Int, tint: Color, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint)
    )
}
