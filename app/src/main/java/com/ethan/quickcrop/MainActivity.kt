package com.ethan.quickcrop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ethan.base.BaseActivity
import com.ethan.quickcrop.ui.album.AlbumView
import com.ethan.quickcrop.ui.theme.QuickCropTheme

class MainActivity : BaseActivity() {
    private var albumView: AlbumView? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        albumView?.onPermissionResult(hasMediaPermission(result))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuickCropTheme {
                MainScreen()
            }
        }
    }

    @Composable
    private fun MainScreen() {
        val scope = lifecycleScope
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                AlbumView(context).also { view ->
                    albumView = view
                    view.onPermissionRequest = {
                        requestPermissionAndLoad()
                    }
                    view.bind(
                        scope = scope,
                        hasMediaPermission = hasMediaPermission()
                    )
                }
            },
            update = { view ->
                albumView = view
            },
            onRelease = { view ->
                if (albumView === view) {
                    albumView = null
                }
            }
        )
    }

    private fun requestPermissionAndLoad() {
        permissionLauncher.launch(requiredPermissions())
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
}
