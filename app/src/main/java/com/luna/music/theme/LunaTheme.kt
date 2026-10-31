package com.luna.music.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun LunaTheme(palette: AlbumPalette, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = palette.primary,
        secondary = palette.secondary,
        background = palette.background,
        surface = palette.surface,
        onBackground = palette.onBackground,
        onSurface = palette.onBackground
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
