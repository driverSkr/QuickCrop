package com.ethan.quickcrop.ui.crop.image.page

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
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
    var imageContainerSize by remember { mutableStateOf(IntSize.Zero) }
    val imageBounds = remember(bitmap, imageContainerSize) {
        calculateFitImageBounds(bitmap = bitmap, containerSize = imageContainerSize)
    }
    val cropAspectRatio = remember(selectedAspectRatio, bitmap) {
        selectedAspectRatio.toCropAspectRatio(bitmap)
    }

    BackHandler(true){}
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
            Image(painter = painterResource(R.drawable.svg_icon_back), contentDescription = null, modifier = Modifier.align(Alignment.CenterStart).clickable{
                context.finishActivity()
            })

            Text("裁剪图片", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.W700, modifier = Modifier.align(Alignment.Center))
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).onSizeChanged {
                imageContainerSize = it
            },
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
                cropBounds = imageBounds,
                aspectRatio = cropAspectRatio
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
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

private fun calculateFitImageBounds(bitmap: Bitmap?, containerSize: IntSize): Rect {
    if (bitmap == null || containerSize.width <= 0 || containerSize.height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) {
        return Rect.Zero
    }

    val containerWidth = containerSize.width.toFloat()
    val containerHeight = containerSize.height.toFloat()
    val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
    val containerRatio = containerWidth / containerHeight

    val displayWidth: Float
    val displayHeight: Float
    if (containerRatio > imageRatio) {
        displayHeight = containerHeight
        displayWidth = displayHeight * imageRatio
    } else {
        displayWidth = containerWidth
        displayHeight = displayWidth / imageRatio
    }

    val left = (containerWidth - displayWidth) / 2f
    val top = (containerHeight - displayHeight) / 2f
    return Rect(
        left = left,
        top = top,
        right = left + displayWidth,
        bottom = top + displayHeight
    )
}

private fun String.toCropAspectRatio(bitmap: Bitmap?): Float? {
    return when (this) {
        "原始比例" -> bitmap?.let { image ->
            if (image.height > 0) {
                image.width.toFloat() / image.height.toFloat()
            } else {
                null
            }
        }
        "自由比例" -> null
        else -> {
            val width = substringBefore(":").toFloatOrNull()
            val height = substringAfter(":").toFloatOrNull()
            if (width != null && height != null && height > 0f) {
                width / height
            } else {
                null
            }
        }
    }
}

@Composable
private fun AspectRatioItem(aspectRatio: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier
        .clickable{ onClick() }
        .background(color = if (isSelected) Color.White else Color.Transparent, shape = RoundedCornerShape(16.dp))
        .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = aspectRatio, fontSize = 14.sp, lineHeight = 14.sp, color = if (isSelected) Color.Black else Color.White, modifier = Modifier.align(Alignment.Center))
    }
}
