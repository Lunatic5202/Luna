package com.luna.music.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import com.luna.music.playback.LunaPlaybackService
import com.luna.music.theme.AlbumPalette
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Frequency bands, mirrored left/right so bass sits at the top-centre of the dome. */
private const val BANDS = 32
private const val BARS = BANDS * 2

/** Dome circle radius as a fraction of screen width (shared with the glass panel shape). */
internal const val DOME_RADIUS_FRACTION = 0.775f

/** Headroom above the dome so the spectrum bars have space to grow. */
internal val DOME_RING_INSET = 52.dp

@Composable
fun PlaybackVisualizer(
    @Suppress("UNUSED_PARAMETER") controller: MediaController?,
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
    var liveWanted by rememberSaveable { mutableStateOf(true) }
    val sessionId by LunaPlaybackService.audioSessionId.collectAsState()

    // Written by the capture thread, read every frame by the animation loop.
    val targets = remember { FloatArray(BANDS) }
    val display = remember { FloatArray(BANDS) }
    val peaks = remember { FloatArray(BANDS) }
    val liveFlag = remember { AtomicBoolean(false) }
    val silentFlag = remember { AtomicBoolean(false) }
    var frame by remember { mutableLongStateOf(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        liveWanted = granted
    }

    // No unprompted microphone dialog: until the user taps the icon (and grants access) the
    // bars run in synthetic mode, which still moves with playback state.

    // Real audio capture.
    DisposableEffect(liveWanted, permissionGranted, sessionId, playing) {
        var effect: Visualizer? = null
        liveFlag.set(false)
        silentFlag.set(false)
        if (liveWanted && permissionGranted && playing && sessionId > 0) {
            try {
                val visualizer = Visualizer(sessionId)
                visualizer.captureSize = Visualizer.getCaptureSizeRange()[1]
                var reference = 2f
                var quietFrames = 0
                visualizer.setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer, w: ByteArray, r: Int) = Unit

                        override fun onFftDataCapture(v: Visualizer, fft: ByteArray, rate: Int) {
                            val bins = fft.size / 2 - 1
                            if (bins < 16) return
                            // Log-spaced bands from ~40 Hz up to ~16 kHz.
                            val top = bins * 0.72f
                            val span = ln(top)
                            val raw = FloatArray(BANDS)
                            var frameMax = 0f
                            for (b in 0 until BANDS) {
                                val lo = exp(span * b / BANDS).toInt().coerceAtLeast(1)
                                val hi = max(lo + 1, exp(span * (b + 1) / BANDS).toInt())
                                var sum = 0f
                                for (k in lo until hi) {
                                    val re = fft[2 * k].toFloat()
                                    val im = fft[2 * k + 1].toFloat()
                                    sum += sqrt(re * re + im * im)
                                }
                                // Tilt up the highs, which carry far less energy than the bass.
                                val tilt = 1f + 1.8f * b / BANDS
                                raw[b] = ln(1f + sum / (hi - lo) * tilt)
                                frameMax = max(frameMax, raw[b])
                            }
                            // Slow auto-gain so quiet and loud tracks both fill the ring.
                            reference = max(1.8f, max(frameMax, reference * 0.992f))
                            for (b in 0 until BANDS) {
                                targets[b] = (raw[b] / reference).coerceIn(0f, 1f).pow(1.4f)
                            }
                            quietFrames = if (frameMax < 0.15f) quietFrames + 1 else 0
                            // Some devices hand back all zeros; fall back to synthetic motion.
                            silentFlag.set(quietFrames > 40)
                        }
                    },
                    Visualizer.getMaxCaptureRate(),
                    false,
                    true
                )
                visualizer.enabled = true
                effect = visualizer
                liveFlag.set(true)
            } catch (_: RuntimeException) {
                liveFlag.set(false)
            }
        }
        onDispose {
            liveFlag.set(false)
            effect?.let {
                try {
                    it.enabled = false
                    it.release()
                } catch (_: RuntimeException) {
                    // The player may have already released its audio session.
                }
            }
        }
    }

    // 60 fps easing: fast attack, slow decay, falling peak caps.
    LaunchedEffect(playing) {
        var last = 0L
        var settled = false
        while (playing || !settled) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                val t = now / 1e9f
                val live = liveFlag.get() && !silentFlag.get()
                var energy = 0f
                for (b in 0 until BANDS) {
                    val target = when {
                        !playing -> 0f
                        live -> targets[b]
                        else -> synthetic(b, t)
                    }
                    val rate = if (target > display[b]) 24f else 6.5f
                    display[b] += (target - display[b]) * (1f - exp(-rate * dt))
                    peaks[b] = if (display[b] >= peaks[b]) display[b]
                    else max(display[b], peaks[b] - dt * 0.5f)
                    energy += peaks[b]
                }
                settled = energy < 0.05f
                frame = now
            }
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            frame // read so the canvas redraws every animation frame
            val radius = size.width * DOME_RADIUS_FRACTION
            val top = DOME_RING_INSET.toPx()
            val center = Offset(size.width / 2f, top + radius)
            // Half-angle of the arc that is actually visible on screen.
            val half = asin((size.width / 2f / radius).coerceAtMost(1f))
            val halfDeg = Math.toDegrees(half.toDouble()).toFloat()
            drawArc(
                color = palette.primary.copy(alpha = 0.22f),
                startAngle = 270f - halfDeg,
                sweepAngle = 2f * halfDeg,
                useCenter = false,
                topLeft = Offset(center.x - radius, top),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = 1.dp.toPx())
            )

            val maxLen = (DOME_RING_INSET.toPx() - 6.dp.toPx())
            val minLen = 3.dp.toPx()
            val arcLength = 2f * half * radius
            val barWidth = arcLength / BARS * 0.5f
            val last = (BARS - 1).toFloat()
            for (i in 0 until BARS) {
                val distance = abs(i - last / 2f)            // 0.5 at centre .. 31.5 at the edges
                val band = (distance - 0.5f).toInt().coerceIn(0, BANDS - 1)
                val level = display[band]
                val angle = -PI / 2.0 - half + 2.0 * half * (i / last)
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                val start = Offset(center.x + dx * radius, center.y + dy * radius)
                val end = Offset(start.x + dx * (minLen + level * maxLen), start.y + dy * (minLen + level * maxLen))
                val color = lerp(palette.primary, palette.secondary, distance / (BARS / 2f))
                    .let { lerp(it, Color.White, level * 0.35f) }
                // Soft glow underlay, then the crisp bar.
                drawLine(color.copy(alpha = 0.10f + 0.22f * level), start, end, barWidth * 2.6f, StrokeCap.Round)
                drawLine(color.copy(alpha = 0.50f + 0.50f * level), start, end, barWidth, StrokeCap.Round)
                // Peak cap that floats and falls.
                val capLen = minLen + peaks[band] * maxLen + 5.dp.toPx()
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f + 0.55f * peaks[band]),
                    radius = barWidth * 0.42f,
                    center = Offset(start.x + dx * capLen, start.y + dy * capLen)
                )
            }
        }

        IconButton(
            onClick = {
                when {
                    liveWanted && permissionGranted -> liveWanted = false
                    permissionGranted -> liveWanted = true
                    else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                .size(34.dp).clip(CircleShape)
                .background(palette.background.copy(alpha = 0.62f))
        ) {
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = if (liveWanted) "Use simulated visualizer" else "Use live audio visualizer",
                tint = if (liveWanted && permissionGranted) palette.primary else palette.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Beat-like motion used when live audio capture isn't available. */
private fun synthetic(band: Int, t: Float): Float {
    val x = band / BANDS.toFloat()
    val beat = sin(t * 2f * PI.toFloat() * 1.9f).coerceAtLeast(0f).pow(5)
    val slow = 0.5f + 0.5f * sin(t * 2.3f + band * 0.55f)
    val fast = 0.5f + 0.5f * sin(t * 4.1f - band * 0.9f)
    val body = 0.20f + 0.28f * slow + 0.16f * fast
    return ((body + beat * 0.5f * (1f - x)) * (1f - 0.3f * x)).coerceIn(0.04f, 1f)
}
