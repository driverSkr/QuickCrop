package com.ethan.quickcrop.ui.edit.image.view

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 滤镜编辑面板，横向展示滤镜缩略图并切换当前滤镜。
 */
@Composable
fun FilterEditorContent(
    bitmap: Bitmap?,
    selectedFilter: ImageFilterOption,
    onFilterClick: (ImageFilterOption) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 默认滤镜在面板内部构造，保持调用方只关心当前选择和点击回调。
            val filters = ImageFilterOption.defaults()
            items(filters.size) { index ->
                FilterCard(
                    bitmap = bitmap,
                    option = filters[index],
                    selected = filters[index].name == selectedFilter.name,
                    onClick = { onFilterClick(filters[index]) }
                )
            }
        }
    }
}

/**
 * 滤镜缩略卡片，展示预览图或短名称，并提供选中态边框。
 */
@Composable
private fun FilterCard(bitmap: Bitmap?, option: ImageFilterOption, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1F2937))
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) Color(0xFF7C3AED) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = option.composeMatrix?.let { ColorFilter.colorMatrix(it) }
                )
            } else {
                Text(text = option.shortName, color = Color.White, fontSize = 16.sp)
            }
        }
        Text(
            text = option.name,
            color = if (selected) Color(0xFFC084FC) else Color(0xFF9CA3AF),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/**
 * 滤镜选项，分别保存 Compose 预览矩阵和 Android 导出矩阵。
 */
data class ImageFilterOption(
    val name: String,
    val shortName: String,
    val composeMatrix: ColorMatrix?,
    val androidMatrix: android.graphics.ColorMatrix?
) {
    /** 是否为原图效果，原图不需要额外颜色矩阵。 */
    val isOriginal: Boolean
        get() = composeMatrix == null && androidMatrix == null

    /**
     * 内置滤镜工厂，集中维护预览和导出需要的滤镜矩阵。
     */
    companion object {
        /** 原图滤镜，不做任何颜色变换。 */
        fun original() = ImageFilterOption("原图", "原", null, null)

        /**
         * 返回编辑页展示的默认滤镜列表。
         */
        fun defaults(): List<ImageFilterOption> {
            return listOf(
                original(),
                saturationFilter("黑白", "黑", 0f),
                colorScaleFilter("复古", "旧", red = 1.12f, green = 0.95f, blue = 0.72f),
                colorScaleFilter("鲜亮", "亮", red = 1.12f, green = 1.12f, blue = 1.04f),
                colorScaleFilter("暖调", "暖", red = 1.16f, green = 1.04f, blue = 0.88f),
                colorScaleFilter("冷调", "冷", red = 0.9f, green = 1.02f, blue = 1.18f),
                colorScaleFilter("高对比", "高", red = 1.18f, green = 1.18f, blue = 1.18f)
            )
        }

        /**
         * 创建饱和度滤镜，同时生成 Compose 和 Android 两套矩阵。
         */
        private fun saturationFilter(name: String, shortName: String, saturation: Float): ImageFilterOption {
            val compose = ColorMatrix().apply { setToSaturation(saturation) }
            val android = android.graphics.ColorMatrix().apply { setSaturation(saturation) }
            return ImageFilterOption(name, shortName, compose, android)
        }

        /**
         * 创建 RGB 通道缩放滤镜，用于复古、冷暖色和高对比等预设。
         */
        private fun colorScaleFilter(name: String, shortName: String, red: Float, green: Float, blue: Float): ImageFilterOption {
            val values = floatArrayOf(
                red, 0f, 0f, 0f, 0f,
                0f, green, 0f, 0f, 0f,
                0f, 0f, blue, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            return ImageFilterOption(
                name = name,
                shortName = shortName,
                composeMatrix = ColorMatrix(values),
                androidMatrix = android.graphics.ColorMatrix(values)
            )
        }
    }
}