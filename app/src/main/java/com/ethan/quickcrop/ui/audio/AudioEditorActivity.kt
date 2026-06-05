package com.ethan.quickcrop.ui.audio

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ethan.quickcrop.R
import com.ethan.quickcrop.ui.theme.QuickCropTheme

class AudioEditorActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuickCropTheme {
                AudioPlaceholderPage(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun AudioPlaceholderPage(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0F))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Image(
                painter = painterResource(R.drawable.fa_arrow_left),
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterStart).clickable { onBack() },
                colorFilter = ColorFilter.tint(Color(0xFF9CA3AF))
            )
            Text(
                text = "音频编辑",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF15803D), Color(0xFF0F766E))))
                .padding(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                // 当前阶段只保留可点击入口，完整波形、裁切和混音流程等待下一阶段实现。
                Text(text = "音频编辑入口", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(text = "裁切 · 混音 · 淡入淡出", color = Color.White.copy(alpha = 0.76f), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                Text(text = "功能将在图片模块检验后继续实现", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp))
            }
        }
    }
}
