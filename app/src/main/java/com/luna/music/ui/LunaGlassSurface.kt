package com.luna.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.luna.music.theme.AlbumPalette
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Shared backdrop state for album-art and gradient layers behind glass surfaces.
 * Each screen provides its own captured background to the surfaces it contains.
 */
val LocalLunaHazeState = compositionLocalOf { HazeState() }
val LocalLunaLiquidBackdrop = compositionLocalOf<LayerBackdrop?> { null }

@Composable
fun LunaGlassSurface(
    palette: AlbumPalette,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    content: @Composable () -> Unit
) {
    val hazeState = LocalLunaHazeState.current
    val liquidBackdrop = LocalLunaLiquidBackdrop.current
    val supportsLens = shape is CornerBasedShape
    val liquidSurface = if (liquidBackdrop != null) {
        Modifier.drawBackdrop(
            backdrop = liquidBackdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(2.dp.toPx())
                if (supportsLens) lens(10.dp.toPx(), 22.dp.toPx(), depthEffect = true)
            },
            onDrawSurface = {
                drawRect(palette.surface.copy(alpha = 0.14f))
                drawRect(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.16f),
                            palette.glow.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
            }
        )
    } else {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = palette.background,
                tint = HazeTint(Color.White.copy(alpha = 0.10f)),
                blurRadius = 24.dp,
                noiseFactor = 0.12f
            )
        ).background(
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.15f),
                    palette.glow.copy(alpha = 0.16f),
                    palette.surface.copy(alpha = 0.30f)
                )
            )
        )
    }
    Box(
        modifier
            .clip(shape)
            .then(liquidSurface)
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

