package com.luna.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.luna.music.theme.AlbumPalette
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Shared backdrop state for album-art and gradient layers behind glass surfaces.
 * The state is provided once at the Activity root so nested screens can use it.
 */
val LocalLunaHazeState = compositionLocalOf { HazeState() }

@Composable
fun LunaGlassSurface(
    palette: AlbumPalette,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    content: @Composable () -> Unit
) {
    val hazeState = LocalLunaHazeState.current
    val blurRadius = with(LocalDensity.current) { 24.dp.toPx() }

    Box(
        modifier
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = palette.background,
                    tint = HazeTint(Color.White.copy(alpha = 0.10f)),
                    blurRadius = blurRadius,
                    noiseFactor = 0.12f
                )
            )
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.15f),
                        palette.glow.copy(alpha = 0.16f),
                        palette.surface.copy(alpha = 0.30f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.42f),
                        palette.secondary.copy(alpha = 0.24f),
                        Color.White.copy(alpha = 0.09f)
                    )
                ),
                shape
            )
            .padding(1.dp)
    ) { content() }
}
