package com.ethan.quickcrop.ui.media.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.ethan.quickcrop.R
import com.ethan.quickcrop.ui.media.MediaPhoto

private const val PREVIEW_MIN_SCALE = 1f
private const val PREVIEW_MAX_SCALE = 4f

/**
 * 图片放大预览子页面：展示大图、支持左右滑动、双指缩放、拖拽查看细节和确认选择。
 */
@Composable
internal fun MediaPreviewPage(
    photos: List<MediaPhoto>,
    initialIndex: Int,
    onClose: () -> Unit,
    onConfirm: (MediaPhoto) -> Unit
) {
    if (photos.isEmpty()) return
    // 预览浮层消费系统返回/侧滑返回手势，只关闭大图预览，不退出整个相册页。
    BackHandler {
        onClose()
    }

    val safeInitialIndex = initialIndex.coerceIn(0, photos.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = safeInitialIndex,
        pageCount = { photos.size }
    )

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0C0C0F))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 预览页空白区域必须消费点击，避免事件穿透到底层相册标题并展开分类。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() }
        )

        Image(
            painter = painterResource(R.drawable.svg_icon_back),
            contentDescription = null,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp, end = 12.dp)
                .size(32.dp)
                .align(Alignment.TopStart)
                .clickable { onClose() }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .padding(top = 112.dp, bottom = 112.dp)
        ) { page ->
            ZoomablePreviewImage(
                photo = photos[page],
                onBlankClick = onClose
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .fillMaxWidth()
                .height(56.dp)
                .background(Color.White, RoundedCornerShape(54.dp))
                .clickable {
                    photos.getOrNull(pagerState.currentPage)?.let(onConfirm)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.media_picker_confirm_selection),
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.W700
            )
        }
    }
}

@Composable
private fun ZoomablePreviewImage(
    photo: MediaPhoto,
    onBlankClick: () -> Unit
) {
    val context = LocalContext.current
    var previewScale by remember(photo.id) { mutableStateOf(PREVIEW_MIN_SCALE) }
    var previewOffset by remember(photo.id) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(photo.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPosition = down.position
                    var hasMultiplePointers = false
                    var hasMoved = false

                    do {
                        val event = awaitPointerEvent()
                        val currentPosition = event.changes.firstOrNull { it.id == down.id }?.position
                        if (event.changes.count { it.pressed } > 1) {
                            hasMultiplePointers = true
                        }
                        if (currentPosition != null && (currentPosition - downPosition).getDistance() > viewConfiguration.touchSlop) {
                            hasMoved = true
                        }

                        val nextScale = (previewScale * event.calculateZoom()).coerceIn(PREVIEW_MIN_SCALE, PREVIEW_MAX_SCALE)
                        val maxTranslateX = ((size.width * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
                        val maxTranslateY = ((size.height * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
                        val shouldHandlePan = event.changes.size > 1 || previewScale > PREVIEW_MIN_SCALE

                        // 双指缩放或放大后的单指拖动由预览页消费，普通左右滑动留给 Pager。
                        if (event.changes.size > 1 || shouldHandlePan) {
                            val pan = event.calculatePan()
                            previewOffset = if (nextScale <= PREVIEW_MIN_SCALE) {
                                Offset.Zero
                            } else {
                                Offset(
                                    x = (previewOffset.x + pan.x).coerceIn(-maxTranslateX, maxTranslateX),
                                    y = (previewOffset.y + pan.y).coerceIn(-maxTranslateY, maxTranslateY)
                                )
                            }
                            previewScale = nextScale
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })

                    // 单指轻点图片实际绘制区域外的空白处时关闭预览，不影响图片区域点击和左右滑动。
                    val isTap = !hasMultiplePointers && !hasMoved
                    if (isTap &&
                        previewScale <= PREVIEW_MIN_SCALE + 0.01f &&
                        isBlankPreviewArea(photo, size.width, size.height, downPosition)
                    ) {
                        onBlankClick()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(photo.uri).build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = previewScale
                    scaleY = previewScale
                    translationX = previewOffset.x
                    translationY = previewOffset.y
                }
        )
    }
}

private fun isBlankPreviewArea(
    photo: MediaPhoto,
    containerWidth: Int,
    containerHeight: Int,
    position: Offset
): Boolean {
    if (containerWidth <= 0 || containerHeight <= 0 || photo.width <= 0 || photo.height <= 0) {
        return false
    }
    val imageBounds = calculateFitImageBounds(
        imageWidth = photo.width,
        imageHeight = photo.height,
        containerWidth = containerWidth,
        containerHeight = containerHeight
    )
    return !imageBounds.contains(position)
}

private fun calculateFitImageBounds(
    imageWidth: Int,
    imageHeight: Int,
    containerWidth: Int,
    containerHeight: Int
): Rect {
    val imageRatio = imageWidth.toFloat() / imageHeight.toFloat()
    val containerRatio = containerWidth.toFloat() / containerHeight.toFloat()
    val displayWidth: Float
    val displayHeight: Float

    if (imageRatio > containerRatio) {
        displayWidth = containerWidth.toFloat()
        displayHeight = displayWidth / imageRatio
    } else {
        displayHeight = containerHeight.toFloat()
        displayWidth = displayHeight * imageRatio
    }

    val left = (containerWidth - displayWidth) / 2f
    val top = (containerHeight - displayHeight) / 2f
    return Rect(left, top, left + displayWidth, top + displayHeight)
}
