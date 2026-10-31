package com.luna.music.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.luna.music.theme.AlbumPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val BAR_COUNT = 48

@OptIn(UnstableApi::class)
@Composable
fun PlaybackVisualizer(
    controller: MediaController?,
    playing: Boolean,
    palette: AlbumPalette,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var enabled by remember { mutableStateOf(false) }
    val levelsFlow = remember { MutableStateFlow(List(BAR_COUNT) { 0f }) }
    val levels by levelsFlow.collectAsState()
    val sessionId = controller?.audioSessionId ?: 0

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        enabled = granted
    }

    DisposableEffect(enabled, permissionGranted, sessionId, playing) {
        var effect: Visualizer? = null
        if (enabled && permissionGranted && playing && sessionId > 0) {
            try {
                val visualizer = Visualizer(sessionId)
                visualizer.captureSize = Visualizer.getCaptureSizeRange()[1]
                visualizer.setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer,
                            waveform: ByteArray,
                            samplingRate: Int
                        ) = Unit

                        override fun onFftDataCapture(
                            visualizer: Visualizer,
                            fft: ByteArray,
                            samplingRate: Int
                        ) {
                            if (fft.size < 4) return
                            val usable = fft.size - 2
                            val next = List(BAR_COUNT) { index ->
                                val start = 2 + index * usable / BAR_COUNT
                                val end = (2 + (index + 1) * usable / BAR_COUNT)
                                    .coerceAtMost(fft.size)
                                var peak = 0f
                                var bin = start
                                while (bin + 1 < end) {
                                    val real = fft[bin].toInt().toFloat()
                                    val imaginary = fft[bin + 1].toInt().toFloat()
                                    val magnitude = sqrt(real * real + imaginary * imaginary) / 180f
                                    if (magnitude > peak) peak = magnitude
                                    bin += 2
                                }
                                peak.coerceIn(0.025f, 1f)
                            }
                            levelsFlow.value = next
                        }
                    },
                    (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(1),
                    false,
                    true
                )
                visualizer.enabled = true
                effect = visualizer
            } catch (_: RuntimeException) {
                enabled = false
            }
        } else {
            levelsFlow.value = List(BAR_COUNT) { 0f }
        }

        onDispose {
            effect?.let {
                try {
                    it.enabled = false
                    it.release()
                } catch (_: RuntimeException) {
                    // The audio session may already have been released by the player.
                }
            }
        }
    }

    // Interpolate every FFT band so the spectrum glides instead of jittering.
    val smoothedLevels = levels.map { level ->
        val animated by animateFloatAsState(
            targetValue = if (enabled && playing) level else 0.025f,
            animationSpec = tween(115, easing = FastOutSlowInEasing),
            label = "radial-visualizer-level"
        )
        animated
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val diameter = minOf(size.width, size.height)
            val radius = diameter * 0.415f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = palette.primary.copy(alpha = 0.13f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            smoothedLevels.forEachIndexed { index, level ->
                val angle = -PI / 2.0 + (2.0 * PI * index / smoothedLevels.size)
                val strength = if (enabled && playing) level else 0.025f
                val barLength = diameter * (0.008f + strength * 0.052f)
                val startRadius = radius - diameter * 0.004f
                val endRadius = startRadius + barLength
                val start = Offset(
                    center.x + cos(angle).toFloat() * startRadius,
                    center.y + sin(angle).toFloat() * startRadius
                )
                val end = Offset(
                    center.x + cos(angle).toFloat() * endRadius,
                    center.y + sin(angle).toFloat() * endRadius
                )
                val blend = index.toFloat() / smoothedLevels.size
                drawLine(
                    color = androidx.compose.ui.graphics.lerp(palette.primary, palette.secondary, blend)
                        .copy(alpha = if (enabled && playing) 0.82f else 0.28f),
                    start = start,
                    end = end,
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        IconButton(
            onClick = {
                if (enabled) {
                    enabled = false
                    levelsFlow.value = List(BAR_COUNT) { 0f }
                } else if (permissionGranted) {
                    enabled = true
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                .size(34.dp).clip(CircleShape)
                .background(palette.background.copy(alpha = 0.62f))
        ) {
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = if (enabled) "Disable visualizer" else "Enable visualizer",
                tint = if (enabled) palette.primary else palette.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
