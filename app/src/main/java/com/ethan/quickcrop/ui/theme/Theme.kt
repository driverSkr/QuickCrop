package com.ethan.quickcrop.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Mint40,
    onPrimary = Ink90,
    primaryContainer = Mint80,
    onPrimaryContainer = Ink10,
    secondary = Sand40,
    onSecondary = Ink90,
    secondaryContainer = Sand80,
    onSecondaryContainer = Ink10,
    background = Ink90,
    onBackground = Ink10,
    surface = Ink90,
    onSurface = Ink10,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE6ECEF),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF4D5A66),
    outline = androidx.compose.ui.graphics.Color(0xFF7C8A95)
)

private val DarkColors = darkColorScheme(
    primary = Mint80,
    onPrimary = Ink10,
    primaryContainer = Mint40,
    onPrimaryContainer = Ink90,
    secondary = Sand80,
    onSecondary = Ink10,
    secondaryContainer = Sand40,
    onSecondaryContainer = Ink90,
    // 全局页面默认背景使用纯黑，避免未显式设置背景的 Compose 页面露出浅色底。
    background = Color.Black,
    onBackground = Ink90,
    surface = Color.Black,
    onSurface = Ink90,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF22313B),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB8C4CC),
    outline = androidx.compose.ui.graphics.Color(0xFF5E6C76)
)

@Composable
fun QuickCropTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        // 统一给 Compose 页面铺一层默认背景，业务页面仍可在内部覆盖自己的背景色。
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background,
            content = content
        )
    }
}
