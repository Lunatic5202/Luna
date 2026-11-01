package com.luna.music.theme

import androidx.compose.ui.graphics.Color

data class AlbumPalette(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val glow: Color,
    val onBackground: Color = Color(0xFFF5F6F8),
    /** Third colour for ambient effects, an analogous hue to [primary]. */
    val accent: Color = secondary,
    /** 0..1: how lively the artwork feels. Drives how far the ambient aurora drifts. */
    val energy: Float = 0.5f,
    /** Short mood name derived from the artwork hue, e.g. EMBER or OCEAN. */
    val vibeName: String = "TWILIGHT",
    /** CALM, GROOVY or ENERGETIC. Kept separate from [energy] so labels don't flicker mid-fade. */
    val vibeLevel: String = "CALM"
) {
    /** e.g. "EMBER · ENERGETIC" */
    fun vibeLabel(): String = "$vibeName · $vibeLevel"

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
        // Luna's violet accent palette stays visible while artwork colours load or are unavailable.
        val Fallback = AlbumPalette(
            background = Color(0xFF0E0B14),
            surface = Color(0xFF1B1524),
            primary = Color(0xFFD2B5FF),
            secondary = Color(0xFFFFB5D2),
            glow = Color(0xFF68478C),
            onBackground = Color(0xFFF5F6F8)
        )
    }
}
