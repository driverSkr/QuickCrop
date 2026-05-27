package com.ethan.quickcrop.ui.crop.image.page

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethan.quickcrop.R
import com.ethan.quickcrop.extension.finishActivity
import com.ethan.quickcrop.ui.crop.image.view.ResizableCropBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CropImagePage(sourceUri: Uri?) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, sourceUri) {
        value = sourceUri?.let { uri ->
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            }
        }
    }
    val aspectRatioList = listOf("原始比例", "自由比例", "1:1", "16:9", "9:16", "5:4", "4:5")
    var selectedAspectRatio by remember { mutableStateOf(aspectRatioList[0]) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
            Image(painter = painterResource(R.drawable.svg_icon_back), contentDescription = null, modifier = Modifier.align(Alignment.CenterStart).clickable{
                context.finishActivity()
            })

            Text("裁剪图片", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.W700, modifier = Modifier.align(Alignment.Center))
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(text = "图片加载中...")
            }

            ResizableCropBox(
                modifier = Modifier.fillMaxSize(),
                aspectRatio = null
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(aspectRatioList.size) { index ->
                    AspectRatioItem(aspectRatio = aspectRatioList[index], isSelected = aspectRatioList[index] == selectedAspectRatio, onClick = {
                        selectedAspectRatio = aspectRatioList[index]
                    })
                }
            }
        }
    }
}

@Composable
private fun AspectRatioItem(aspectRatio: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier
        .clickable{ onClick() }
        .background(color = if (isSelected) Color.White else Color.Transparent, shape = RoundedCornerShape(12.dp))
        .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(text = aspectRatio, color = if (isSelected) Color.Black else Color.White, modifier = Modifier)
    }
}
