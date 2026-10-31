@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.luna.music.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay

/** Marks the album artwork as the element that flies between the mini player and Now Playing. */
@Composable
fun Modifier.lunaSharedArtwork(
    sharedScope: SharedTransitionScope,
    visibilityScope: AnimatedVisibilityScope
): Modifier = with(sharedScope) {
    this@lunaSharedArtwork.sharedElement(
        sharedContentState = rememberSharedContentState(key = "luna-artwork"),
        animatedVisibilityScope = visibilityScope,
        boundsTransform = { _, _ ->
            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        }
    )
}

/** Springy scale that dips while the given source is pressed. */
@Composable
fun pressScale(source: InteractionSource, pressedScale: Float = 0.9f): State<Float> {
    val pressed by source.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "press-scale"
    )
}

/** Clickable surface with a springy press-down, clipped ripple and background. */
@Composable
fun Modifier.bouncyClickable(shape: Shape, color: Color, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val scale by pressScale(source, 0.965f)
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(color)
        .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
}

@Composable
fun BouncyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pressedScale: Float = 0.82f,
    content: @Composable () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val scale by pressScale(source, pressedScale)
    IconButton(
        onClick = onClick,
        interactionSource = source,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.then(modifier),
        content = content
    )
}

/** Items rise and fade in one after another the first time they appear. */
@Composable
fun Modifier.staggeredEntrance(index: Int, stepMillis: Long = 45L): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(if (index < 12) index * stepMillis else 0L)
        progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow))
    }
    return this.graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 36.dp.toPx()
    }
}

/** Three little bars that bounce while music plays and settle when paused. */
@Composable
fun EqualizerBars(color: Color, playing: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val a by transition.animateFloat(
        0.25f, 1f,
        infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq-a"
    )
    val b by transition.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq-b"
    )
    val c by transition.animateFloat(
        0.2f, 1f,
        infiniteRepeatable(tween(460, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq-c"
    )
    val mix by animateFloatAsState(if (playing) 1f else 0f, tween(320), label = "eq-mix")
    Row(
        modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(a, b, c).forEach { level ->
            Box(
                Modifier.width(3.dp)
                    .fillMaxHeight(lerp(0.22f, level, mix))
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}
