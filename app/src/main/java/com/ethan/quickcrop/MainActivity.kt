package com.ethan.quickcrop

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethan.base.BaseActivity
import com.ethan.quickcrop.ui.edit.audio.AudioEditorActivity
import com.ethan.quickcrop.ui.media.MediaPickActivity
import com.ethan.quickcrop.ui.media.MediaPickType
import com.ethan.quickcrop.ui.theme.QuickCropTheme

class MainActivity : BaseActivity() {
    override val logTag: String = TAG

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuickCropTheme {
                HomePage(
                    onImageClick = { MediaPickActivity.launch(this@MainActivity, MediaPickType.IMAGE) },
                    onVideoClick = { MediaPickActivity.launch(this@MainActivity, MediaPickType.VIDEO) },
                    onAudioClick = { openPlaceholderEditor(AudioEditorActivity::class.java, "音频编辑") }
                )
            }
        }
    }

    private fun openPlaceholderEditor(targetClass: Class<*>, moduleName: String) {
        val started = startActivitySafely(Intent(this, targetClass))
        if (!started) {
            Log.w(TAG, "打开${moduleName}入口失败")
            Toast.makeText(this, "${moduleName}暂时无法打开", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
private fun HomePage(
    onImageClick: () -> Unit,
    onVideoClick: () -> Unit,
    onAudioClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0F))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 52.dp, bottom = 20.dp)
    ) {
        HomeTopBar()
        Spacer(modifier = Modifier.height(24.dp))

        FeatureEntranceCard(
            title = "图片编辑",
            subtitle = "裁剪 · 滤镜 · 旋转 · 调节",
            iconRes = R.drawable.fa_image,
            gradient = Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFDB2777))),
            onClick = onImageClick
        )
        Spacer(modifier = Modifier.height(12.dp))
        FeatureEntranceCard(
            title = "视频编辑",
            subtitle = "剪辑 · 拼接 · 速度 · 字幕",
            iconRes = R.drawable.fa_film,
            gradient = Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF06B6D4))),
            onClick = onVideoClick
        )
        Spacer(modifier = Modifier.height(12.dp))
        FeatureEntranceCard(
            title = "音频编辑",
            subtitle = "裁切 · 混音 · 淡入淡出",
            iconRes = R.drawable.fa_music,
            gradient = Brush.linearGradient(listOf(Color(0xFF16A34A), Color(0xFF0D9488))),
            onClick = onAudioClick
        )

        Spacer(modifier = Modifier.height(28.dp))
        RecentProjectsPreview()
    }
}

@Composable
private fun HomeTopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 首页头像仅作为品牌识别，后续接入用户体系时可替换为真实头像。
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))),
                contentAlignment = Alignment.Center
            ) {
                FaIcon(iconRes = R.drawable.fa_play, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "Ethan", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(text = "今天创作点什么？", color = Color(0xFF9CA3AF), fontSize = 12.sp)
            }
        }

        FaIcon(iconRes = R.drawable.fa_cog, tint = Color(0xFF9CA3AF), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun FeatureEntranceCard(
    title: String,
    subtitle: String,
    iconRes: Int,
    gradient: Brush,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            FaIcon(iconRes = iconRes, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(text = subtitle, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
        }
        FaIcon(iconRes = R.drawable.fa_chevron_right, tint = Color.White.copy(alpha = 0.58f), modifier = Modifier.size(14.dp))
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

@Composable
private fun RecentProjectsPreview() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = "最近项目", color = Color(0xFFD1D5DB), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(text = "查看全部 ›", color = Color(0xFF6B7280), fontSize = 12.sp)
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { index ->
            // 近期项目先提供首页视觉占位，后续接入草稿系统后再替换为真实封面。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(recentProjectBrush(index))
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = listOf("风景照.jpg", "头像裁剪", "海边日落")[index],
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }
    }
}

private fun recentProjectBrush(index: Int): Brush {
    val colors = listOf(
        listOf(Color(0xFF1E3A8A), Color(0xFF7C3AED)),
        listOf(Color(0xFF831843), Color(0xFFDB2777)),
        listOf(Color(0xFF0F766E), Color(0xFFF59E0B))
    )
    return Brush.linearGradient(colors[index])
}
