package com.ethan.quickcrop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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
    background = Ink10,
    onBackground = Ink90,
    surface = Ink10,
    onSurface = Ink90,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF22313B),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB8C4CC),
    outline = androidx.compose.ui.graphics.Color(0xFF5E6C76)
)

@Composable
fun QuickCropTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

