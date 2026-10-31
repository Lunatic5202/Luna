package com.luna.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import com.luna.music.theme.AlbumPalette

/**
 * Glass styling boundary. This initial implementation uses translucent
 * layered fills and edge highlights; backdrop blur can be enabled through
 * Haze after device/performance validation.
 */
@Composable
fun LunaGlassSurface(
    palette: AlbumPalette,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.13f),
                        palette.glow.copy(alpha = 0.13f),
                        Color.White.copy(alpha = 0.045f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.32f), palette.secondary.copy(alpha = 0.16f), Color.White.copy(alpha = 0.07f))
                ),
                shape
            )
            .padding(1.dp)
    ) { content() }
}
