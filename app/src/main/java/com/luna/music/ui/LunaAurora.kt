package com.luna.music.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.luna.music.theme.AlbumPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Slow, drifting colour wash that carries the current track's mood. Three soft blobs in the
 * artwork's colours orbit on whole-number harmonics, so the loop is seamless. More energetic
 * artwork drifts wider; paused music settles to a dimmer glow.
 *
 * Everything animated is read inside the Canvas draw lambda, so it never recomposes.
 */
@Composable
fun LunaAurora(
    palette: AlbumPalette,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val clock = rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(42_000, easing = LinearEasing), RepeatMode.Restart),
        label = "aurora-phase"
    )
    val intensity by animateFloatAsState(
        targetValue = if (playing) 1f else 0.5f,
        animationSpec = tween(900),
        label = "aurora-intensity"
    )

    Canvas(modifier.fillMaxSize()) {
        val phase = clock.value * 2f * PI.toFloat()
        val reach = 0.08f + 0.14f * palette.energy
        val radius = size.maxDimension * 0.62f

        aurora(palette.primary, 0.28f + reach * sin(phase), 0.20f + reach * cos(phase * 2f), radius, 0.30f * intensity)
        aurora(palette.accent, 0.78f + reach * cos(phase), 0.52f + reach * sin(phase * 3f), radius * 0.9f, 0.22f * intensity)
        aurora(palette.glow, 0.40f + reach * sin(phase * 2f), 0.88f + reach * cos(phase), radius, 0.34f * intensity)
    }
}

private fun DrawScope.aurora(color: Color, x: Float, y: Float, radius: Float, alpha: Float) {
    val center = Offset(size.width * x, size.height * y)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}
