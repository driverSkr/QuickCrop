package com.ethan.quickcrop.ui.media.view

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EdgeEffect
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.ethan.quickcrop.R
import com.ethan.quickcrop.ui.media.MediaPhoto

/**
 * 图片网格子页面：使用 AndroidView 承载 RecyclerView，负责缩略图展示、滚动复用和预览入口点击分发。
 */
@Composable
internal fun PhotoGrid(
    photos: List<MediaPhoto>,
    firstVisiblePositionProvider: () -> Int,
    onFirstVisiblePositionChange: (Int) -> Unit,
    onPhotoClick: (MediaPhoto) -> Unit,
    onPreviewClick: (MediaPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxWidth().clipToBounds(),
        factory = { context ->
            val spanCount = 3
            val photoAdapter = MediaPhotoGridAdapter()
            PhotoGridRecyclerView(context).apply {
                layoutManager = GridLayoutManager(context, spanCount)
                adapter = photoAdapter
                itemAnimator = null
                // 顶部边界下拉时不交给父级嵌套滚动，也不绘制 RecyclerView 的边缘拉伸效果。
                isNestedScrollingEnabled = false
                clipToPadding = false
                clipChildren = true
                overScrollMode = View.OVER_SCROLL_NEVER
                edgeEffectFactory = NoEdgeEffectFactory
                setHasFixedSize(true)
                setItemViewCacheSize(24)
                recycledViewPool.setMaxRecycledViews(0, 36)
                setPadding(context.dpToPx(12), 0, context.dpToPx(12), context.dpToPx(24))
                addItemDecoration(GridSpacingItemDecoration(spanCount, context.dpToPx(4)))
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        val position = (recyclerView.layoutManager as? GridLayoutManager)
                            ?.findFirstVisibleItemPosition()
                            ?: RecyclerView.NO_POSITION
                        if (position != RecyclerView.NO_POSITION) {
                            onFirstVisiblePositionChange(position)
                        }
                    }
                })
            }
        },
        update = { recyclerView ->
            val photoAdapter = recyclerView.adapter as MediaPhotoGridAdapter
            photoAdapter.onPhotoClick = onPhotoClick
            photoAdapter.onPreviewClick = onPreviewClick
            photoAdapter.submitList(photos) {
                // 仅恢复 AndroidView 重建时的滚动位置，预览浮层返回时 RecyclerView 自身会保留位置。
                if (!recyclerView.hasRestoredInitialPosition && photos.isNotEmpty()) {
                    recyclerView.hasRestoredInitialPosition = true
                    recyclerView.post {
                        val safePosition = firstVisiblePositionProvider().coerceIn(0, photos.lastIndex)
                        (recyclerView.layoutManager as? GridLayoutManager)
                            ?.scrollToPositionWithOffset(safePosition, 0)
                    }
                }
            }
        }
    )
}

private class PhotoGridRecyclerView(context: Context) : RecyclerView(context) {
    var hasRestoredInitialPosition: Boolean = false

    override fun overScrollBy(
        deltaX: Int,
        deltaY: Int,
        scrollX: Int,
        scrollY: Int,
        scrollRangeX: Int,
        scrollRangeY: Int,
        maxOverScrollX: Int,
        maxOverScrollY: Int,
        isTouchEvent: Boolean
    ): Boolean {
        // 顶部或底部到达边界后直接拦住过度滚动，避免 AndroidView 拉伸影响顶部栏绘制。
        return false
    }
}

private object NoEdgeEffectFactory : RecyclerView.EdgeEffectFactory() {
    override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
        return object : EdgeEffect(view.context) {
            override fun onPull(deltaDistance: Float) = Unit

            override fun onPull(deltaDistance: Float, displacement: Float) = Unit

            override fun onAbsorb(velocity: Int) = Unit

            override fun onRelease() = Unit

            override fun draw(canvas: android.graphics.Canvas): Boolean = false

            override fun isFinished(): Boolean = true
        }
    }
}

private class MediaPhotoGridAdapter : ListAdapter<MediaPhoto, MediaPhotoGridViewHolder>(MediaPhotoDiffCallback) {
    var onPhotoClick: (MediaPhoto) -> Unit = {}
    var onPreviewClick: (MediaPhoto) -> Unit = {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaPhotoGridViewHolder {
        return MediaPhotoGridViewHolder.create(parent)
    }

    override fun onBindViewHolder(holder: MediaPhotoGridViewHolder, position: Int) {
        holder.bind(getItem(position), onPhotoClick, onPreviewClick)
    }

    override fun onViewRecycled(holder: MediaPhotoGridViewHolder) {
        holder.clear()
    }
}

private object MediaPhotoDiffCallback : DiffUtil.ItemCallback<MediaPhoto>() {
    override fun areItemsTheSame(oldItem: MediaPhoto, newItem: MediaPhoto): Boolean {
        return oldItem.id == newItem.id && oldItem.isVideo == newItem.isVideo
    }

    override fun areContentsTheSame(oldItem: MediaPhoto, newItem: MediaPhoto): Boolean {
        return oldItem == newItem
    }
}

private class MediaPhotoGridViewHolder private constructor(
    itemView: SquarePhotoFrameLayout,
    private val imageView: ImageView,
    private val previewButton: FrameLayout,
    private val durationBadgeView: TextView
) : RecyclerView.ViewHolder(itemView) {

    fun bind(
        photo: MediaPhoto,
        onPhotoClick: (MediaPhoto) -> Unit,
        onPreviewClick: (MediaPhoto) -> Unit
    ) {
        itemView.setOnClickListener { onPhotoClick(photo) }
        // 预览入口独立消费点击，避免误触发图片导入。
        previewButton.setOnClickListener { onPreviewClick(photo) }
        imageView.load(photo.uri) {
            crossfade(false)
            placeholder(ColorDrawable(AndroidColor.TRANSPARENT))
            error(ColorDrawable(AndroidColor.TRANSPARENT))
        }
        if (photo.isVideo) {
            durationBadgeView.visibility = View.VISIBLE
            durationBadgeView.text = formatDuration(photo.durationMs)
        } else {
            durationBadgeView.visibility = View.GONE
            durationBadgeView.text = null
        }
    }

    fun clear() {
        itemView.setOnClickListener(null)
        previewButton.setOnClickListener(null)
        imageView.setImageDrawable(null)
        durationBadgeView.visibility = View.GONE
        durationBadgeView.text = null
    }

    companion object {
        fun create(parent: ViewGroup): MediaPhotoGridViewHolder {
            val context = parent.context
            val root = SquarePhotoFrameLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                background = GradientDrawable().apply {
                    cornerRadius = context.dpToPxFloat(2f)
                    setColor(AndroidColor.rgb(23, 23, 25))
                }
                clipToOutline = true
            }
            val imageView = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            val previewButtonSize = context.dpToPx(28)
            val previewIconSize = context.dpToPx(16)
            val previewButton = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(previewButtonSize, previewButtonSize).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                }
                isClickable = true
                isFocusable = true
                addView(
                    ImageView(context).apply {
                        setImageResource(R.drawable.svg_image_enlarge)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    },
                    FrameLayout.LayoutParams(previewIconSize, previewIconSize, Gravity.CENTER)
                )
            }
            val durationBadgeView = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.START
                    leftMargin = context.dpToPx(6)
                    bottomMargin = context.dpToPx(6)
                }
                setTextColor(AndroidColor.WHITE)
                textSize = 11f
                includeFontPadding = false
                setPadding(context.dpToPx(5), context.dpToPx(3), context.dpToPx(5), context.dpToPx(3))
                background = GradientDrawable().apply {
                    cornerRadius = context.dpToPxFloat(8f)
                    setColor(AndroidColor.argb(178, 0, 0, 0))
                }
                visibility = View.GONE
            }
            root.addView(imageView)
            root.addView(durationBadgeView)
            root.addView(previewButton)
            return MediaPhotoGridViewHolder(root, imageView, previewButton, durationBadgeView)
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val totalSeconds = safeDuration / 1000L
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private class SquarePhotoFrameLayout(context: Context) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val squareSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        // 固定网格项为正方形，避免图片加载完成后重新撑开布局。
        super.onMeasure(squareSpec, squareSpec)
    }
}

private class GridSpacingItemDecoration(
    private val spanCount: Int,
    private val spacing: Int
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val column = position % spanCount
        outRect.left = column * spacing / spanCount
        outRect.right = spacing - (column + 1) * spacing / spanCount
        outRect.bottom = spacing
    }
}

private fun Context.dpToPx(value: Int): Int {
    return (value * resources.displayMetrics.density + 0.5f).toInt()
}

private fun Context.dpToPxFloat(value: Float): Float {
    return value * resources.displayMetrics.density
}
