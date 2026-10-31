package com.luna.music.theme

import androidx.compose.ui.graphics.Color

data class AlbumPalette(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val glow: Color,
    val onBackground: Color = Color(0xFFF5F6F8)
) {
    /** Shared secondary text color for use across every screen. */
    val muted: Color get() = onBackground.copy(alpha = 0.72f)

    /** A palette-derived gradient endpoint that keeps the app's backgrounds in the same family. */
    val backgroundBottom: Color get() = Color(
        red = background.red * 0.76f,
        green = background.green * 0.76f,
        blue = background.blue * 0.76f,
        alpha = background.alpha
    )

    companion object {
        // Neutral while artwork is loading or unavailable; track colours replace this palette.
        val Fallback = AlbumPalette(
            background = Color(0xFF0B0D10),
            surface = Color(0xFF171A1F),
            primary = Color(0xFFD6D9DE),
            secondary = Color(0xFFAEB5BF),
            glow = Color(0xFF303640),
            onBackground = Color(0xFFF5F6F8)
        )
    }
}
