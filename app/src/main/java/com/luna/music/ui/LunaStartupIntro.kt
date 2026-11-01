package com.luna.music.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.music.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** The intro only plays on a cold start, not when the activity is recreated. */
internal object StartupIntroState {
    var played = false
}

private val IntroCyan = Color(0xFF3CD7FF)
private val IntroGold = Color(0xFFFFC857)
private val IntroCoral = Color(0xFFFF6B8A)

private val LOGO_SIZE = 150.dp
private val STAGE_SIZE = 300.dp
private val RING_GAP = 22.dp

/**
 * Startup animation: the moon blooms in, a gradient ring draws itself around it,
 * the wordmark rises, then everything dissolves into the app. Tap to skip.
 *
 * Every animated value is read inside graphicsLayer or draw lambdas, so the
 * intro never triggers recomposition while the app is busy composing underneath.
 */
@Composable
fun LunaStartupIntro(onFinished: () -> Unit) {
    val reveal = remember { Animatable(0f) }
    val sweep = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    var skipped by remember { mutableStateOf(false) }

    LaunchedEffect(skipped) {
        if (skipped) {
            exit.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        } else {
            coroutineScope {
                launch { reveal.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 110f)) }
                launch {
                    delay(120)
                    sweep.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
                }
                launch {
                    delay(520)
                    word.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
                }
            }
            delay(250)
            exit.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
        onFinished()
    }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { skipped = true } },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(STAGE_SIZE).graphicsLayer {
                    val s = 1f + 0.35f * exit.value
                    scaleX = s
                    scaleY = s
                },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.luna_brand_logo),
                    contentDescription = "Luna",
                    modifier = Modifier.size(LOGO_SIZE).graphicsLayer {
                        val s = 0.6f + 0.4f * reveal.value
                        scaleX = s
                        scaleY = s
                        alpha = (reveal.value * 2.2f).coerceIn(0f, 1f)
                    }
                )
                Canvas(Modifier.fillMaxSize()) {
                    val c = center
                    val half = size.minDimension / 2f
                    val rv = reveal.value.coerceIn(0f, 1f)

                    // Additive bloom: the logo is an opaque black square, so light is
                    // added on top of it instead of being drawn behind it.
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to IntroCyan.copy(alpha = 0.30f * rv),
                            0.55f to IntroCoral.copy(alpha = 0.16f * rv),
                            1f to Color.Transparent,
                            center = c,
                            radius = half * (0.55f + 0.45f * rv)
                        ),
                        radius = half,
                        center = c,
                        blendMode = BlendMode.Plus
                    )

                    val progress = sweep.value
                    if (progress > 0f) {
                        val ringRadius = LOGO_SIZE.toPx() / 2f + RING_GAP.toPx()
                        rotate(-90f, c) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(IntroCyan, IntroGold, IntroCoral, IntroCyan),
                                    center = c
                                ),
                                startAngle = 0f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                topLeft = Offset(c.x - ringRadius, c.y - ringRadius),
                                size = Size(ringRadius * 2f, ringRadius * 2f),
                                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                                alpha = 0.9f
                            )
                        }
                        // A bright head leads the ring and fades as it closes.
                        val angle = Math.toRadians(-90.0 + 360.0 * progress)
                        val head = Offset(
                            c.x + ringRadius * cos(angle).toFloat(),
                            c.y + ringRadius * sin(angle).toFloat()
                        )
                        val headAlpha = 1f - ((progress - 0.85f) / 0.15f).coerceIn(0f, 1f)
                        drawCircle(Color.White.copy(alpha = 0.35f * headAlpha), 9.dp.toPx(), head)
                        drawCircle(Color.White.copy(alpha = 0.95f * headAlpha), 3.dp.toPx(), head)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "LUNA",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 10.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = word.value
                    translationY = (1f - word.value) * 14.dp.toPx()
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "your music, your space",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = (word.value - 0.3f).coerceAtLeast(0f) / 0.7f
                    translationY = (1f - word.value) * 18.dp.toPx()
                }
            )
        }
    }
}
