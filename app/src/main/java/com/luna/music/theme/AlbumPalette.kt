package com.luna.music.theme

import androidx.compose.ui.graphics.Color

data class AlbumPalette(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val glow: Color,
    val onBackground: Color = Color(0xFFF7F4FF)
) {
    companion object {
        val Fallback = AlbumPalette(
            background = Color(0xFF090912),
            surface = Color(0xFF191727),
            primary = Color(0xFFAA83FF),
            secondary = Color(0xFF68DCEB),
            glow = Color(0xFF55358E)
        )
    }
}
