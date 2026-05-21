package com.ethan.quickcrop.feature.image

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ImageEditorScreen(
    sourceUri: Uri?,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeaderBar(
                sourceUri = sourceUri,
                onBack = onBack
            )

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1C1C1C)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(Color(0xFF2A2A2A), RoundedCornerShape(28.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "图片",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Text(
                            text = "编辑画布占位",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "后续会在这里接入图片裁剪、缩放、旋转和导出能力。",
                            color = Color(0xFFB8B8B8),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            ToolSection(
                title = "布局",
                description = "先保留一个完整的编辑页结构，后续可替换为裁剪比例、画布缩放等控件。"
            )

            ToolSection(
                title = "调整",
                description = "这里可以接亮度、对比度、滤镜、旋转等编辑项。"
            )

            ToolSection(
                title = "导出",
                description = "导出入口已预留，等图片功能实现后可以直接串起来。"
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("返回相册")
                }
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("暂不处理")
                }
            }
        }
    }
}

@Composable
fun ImageCropScreen(
    sourceUri: Uri? = null,
    onBack: () -> Unit = {}
) {
    ImageEditorScreen(
        sourceUri = sourceUri,
        onBack = onBack
    )
}

@Composable
private fun HeaderBar(
    sourceUri: Uri?,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
            Text(
                text = "图片编辑",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.size(1.dp))
        }

        Text(
            text = when {
                sourceUri == null -> "当前没有传入图片素材。"
                else -> "已选中图片：${sourceUri.lastPathSegment ?: sourceUri}"
            },
            color = Color(0xFFB8B8B8),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ToolSection(
    title: String,
    description: String
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = description,
                color = Color(0xFFB8B8B8),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
