package com.ethan.quickcrop

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.compose.ui.graphics.asAndroidBitmap
import com.ethan.base.BaseActivity
import com.ethan.quickcrop.core.media.GalleryMediaItem
import com.ethan.quickcrop.core.media.MediaLibraryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

private enum class MediaFilter {
    Recent,
    Video,
    Image
}

class MainActivity : BaseActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = hasMediaPermission(result)
        statusTextView.text = if (granted) {
            "权限已授予，正在加载本地媒体。"
        } else {
            "没有读取相册权限，暂时无法展示本地媒体。"
        }

        if (granted) {
            loadMedia()
        } else {
            showEmptyState("需要相册权限后才能展示内容。")
        }
        updatePermissionControls(granted)
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var statusTextView: TextView
    private lateinit var recentCountView: TextView
    private lateinit var videoCountView: TextView
    private lateinit var imageCountView: TextView
    private lateinit var recentTabView: TextView
    private lateinit var videoTabView: TextView
    private lateinit var imageTabView: TextView
    private lateinit var refreshTabView: TextView
    private lateinit var emptyView: TextView

    private val mediaAdapter = AlbumMediaAdapter(
        scope = lifecycleScope,
        onClick = { item -> openMediaEditor(item) }
    )

    private var currentFilter = MediaFilter.Recent
    private var allItems: List<GalleryMediaItem> = emptyList()
    private var currentItems: List<GalleryMediaItem> = emptyList()
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.media_recycler)
        statusTextView = findViewById(R.id.status_text)
        recentCountView = findViewById(R.id.recent_count)
        videoCountView = findViewById(R.id.video_count)
        imageCountView = findViewById(R.id.image_count)
        recentTabView = findViewById(R.id.tab_recent)
        videoTabView = findViewById(R.id.tab_video)
        imageTabView = findViewById(R.id.tab_image)
        refreshTabView = findViewById(R.id.tab_refresh)
        emptyView = findViewById(R.id.empty_view)

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = mediaAdapter

        recentTabView.setDebouncedClickListener {
            applyFilter(MediaFilter.Recent)
        }
        videoTabView.setDebouncedClickListener {
            applyFilter(MediaFilter.Video)
        }
        imageTabView.setDebouncedClickListener {
            applyFilter(MediaFilter.Image)
        }
        refreshTabView.setDebouncedClickListener {
            requestPermissionAndLoad()
        }

        updateFilterTabs()
        updateCounters()

        if (hasMediaPermission()) {
            loadMedia()
        } else {
            showEmptyState("请先授权读取媒体权限。")
            requestPermissionAndLoad()
        }
    }

    private fun requestPermissionAndLoad() {
        permissionLauncher.launch(requiredPermissions())
    }

    private fun loadMedia() {
        if (isLoading) {
            return
        }

        isLoading = true
        statusTextView.text = "正在扫描相册中的图片和视频..."
        showEmptyState("正在加载媒体...")

        lifecycleScope.launch {
            try {
                // 媒体扫描放到协程里执行，避免在主线程里卡住 RecyclerView 滚动和首屏绘制。
                val items = MediaLibraryRepository.loadMediaItems(this@MainActivity)
                allItems = items
                applyFilter(currentFilter, refreshSelection = false)
                statusTextView.text = if (items.isEmpty()) {
                    "没有扫描到本地媒体。"
                } else {
                    "已加载 ${items.size} 个媒体项目。"
                }
                updateCounters()
            } catch (throwable: Throwable) {
                statusTextView.text = "读取相册失败：${throwable.message ?: "未知错误"}"
                showEmptyState("读取相册失败，请稍后重试。")
            } finally {
                isLoading = false
            }
        }
    }

    private fun applyFilter(
        filter: MediaFilter,
        refreshSelection: Boolean = true
    ) {
        currentFilter = filter
        if (refreshSelection) {
            updateFilterTabs()
        }

        currentItems = when (filter) {
            MediaFilter.Recent -> allItems
            MediaFilter.Video -> allItems.filter { it.isVideo }
            MediaFilter.Image -> allItems.filterNot { it.isVideo }
        }

        mediaAdapter.submitItems(currentItems)
        emptyView.visibility = if (currentItems.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (currentItems.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateCounters() {
        recentCountView.text = buildCountText("最近", allItems.size)
        videoCountView.text = buildCountText("视频", allItems.count { it.isVideo })
        imageCountView.text = buildCountText("图片", allItems.count { !it.isVideo })
    }

    private fun updateFilterTabs() {
        applyTabState(recentTabView, currentFilter == MediaFilter.Recent)
        applyTabState(videoTabView, currentFilter == MediaFilter.Video)
        applyTabState(imageTabView, currentFilter == MediaFilter.Image)
        applyTabState(refreshTabView, false, secondary = true)
    }

    private fun applyTabState(
        view: TextView,
        selected: Boolean,
        secondary: Boolean = false
    ) {
        val background = if (secondary) {
            R.drawable.bg_filter_tab_secondary
        } else if (selected) {
            R.drawable.bg_filter_tab_selected
        } else {
            R.drawable.bg_filter_tab_secondary
        }
        view.setBackgroundResource(background)
        view.setTextColor(
            ContextCompat.getColor(
                this,
                if (secondary) android.R.color.white else android.R.color.white
            )
        )
    }

    private fun showEmptyState(message: String) {
        emptyView.text = message
        emptyView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun updatePermissionControls(granted: Boolean) {
        refreshTabView.isEnabled = true
        refreshTabView.alpha = if (granted) {
            1f
        } else {
            0.9f
        }
    }

    private fun hasMediaPermission(result: Map<String, Boolean>? = null): Boolean {
        return requiredPermissions().all { permission ->
            result?.get(permission)
                ?: (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED)
        }
    }

    private fun hasMediaPermission(): Boolean {
        return hasMediaPermission(null)
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun openMediaEditor(item: GalleryMediaItem) {
        // 点击视频或图片后直接进入对应编辑页，避免再弹系统选择器。
        val intent = if (item.isVideo) {
            Intent(this, VideoEditorActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_URI, item.uri.toString())
            }
        } else {
            Intent(this, ImageEditorActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_URI, item.uri.toString())
            }
        }
        startActivity(intent)
    }

    private fun buildCountText(label: String, value: Int): String {
        return "$label $value"
    }

    companion object {
        const val EXTRA_MEDIA_URI = "extra_media_uri"
    }
}

private class AlbumMediaAdapter(
    private val scope: androidx.lifecycle.LifecycleCoroutineScope,
    private val onClick: (GalleryMediaItem) -> Unit
) : RecyclerView.Adapter<AlbumMediaAdapter.MediaViewHolder>() {
    private val items = mutableListOf<GalleryMediaItem>()

    fun submitItems(newItems: List<GalleryMediaItem>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size

            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition].uri == newItems[newItemPosition].uri
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition] == newItems[newItemPosition]
            }
        })
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): MediaViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_album_media, parent, false)
        return MediaViewHolder(view, scope, onClick)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    override fun onViewRecycled(holder: MediaViewHolder) {
        holder.recycle()
    }

    class MediaViewHolder(
        itemView: android.view.View,
        private val scope: androidx.lifecycle.LifecycleCoroutineScope,
        private val onClick: (GalleryMediaItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val thumbnailView: android.widget.ImageView = itemView.findViewById(R.id.thumbnail_view)
        private val typeBadgeView: TextView = itemView.findViewById(R.id.type_badge)
        private val playBadgeView: TextView = itemView.findViewById(R.id.play_badge)
        private val durationBadgeView: TextView = itemView.findViewById(R.id.duration_badge)
        private val nameView: TextView = itemView.findViewById(R.id.name_text)
        private val metaView: TextView = itemView.findViewById(R.id.meta_text)
        private var thumbnailJob: Job? = null

        fun bind(item: GalleryMediaItem) {
            itemView.setOnClickListener { onClick(item) }
            nameView.text = item.displayName
            metaView.text = buildString {
                append(if (item.isVideo) "视频" else "图片")
                append(" · ")
                append(formatSize(item.sizeBytes))
            }
            typeBadgeView.text = if (item.isVideo) "视频" else "图片"
            typeBadgeView.setBackgroundResource(
                if (item.isVideo) R.drawable.bg_badge_type else R.drawable.bg_badge_type
            )

            if (item.isVideo) {
                playBadgeView.visibility = View.VISIBLE
                durationBadgeView.visibility = View.VISIBLE
                durationBadgeView.text = formatDuration(item.durationMs)
            } else {
                playBadgeView.visibility = View.GONE
                durationBadgeView.visibility = View.GONE
            }

            thumbnailView.setImageDrawable(null)
            thumbnailView.setBackgroundResource(R.drawable.bg_media_preview)
            thumbnailView.tag = item.uri

            thumbnailJob?.cancel()
            thumbnailJob = scope.launch {
                // 缩略图异步加载，减少列表快速滑动时的掉帧。
                val thumbnail = MediaLibraryRepository.loadThumbnail(
                    context = itemView.context,
                    item = item,
                    sizePx = 360
                )
                if (thumbnailView.tag == item.uri) {
                    thumbnailView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    thumbnailView.setImageBitmap(thumbnail?.asAndroidBitmap())
                }
            }
        }

        fun recycle() {
            thumbnailJob?.cancel()
            thumbnailJob = null
            thumbnailView.setImageDrawable(null)
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val totalSeconds = safeDuration / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.CHINA, "%02d:%02d", minutes, seconds)
}

private fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) {
        return "未知大小"
    }
    val sizeMb = sizeBytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.CHINA, "%.1f MB", sizeMb)
}
