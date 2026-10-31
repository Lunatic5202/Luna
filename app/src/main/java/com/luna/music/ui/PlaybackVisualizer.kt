package com.luna.music.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.luna.music.theme.AlbumPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.sqrt

private const val BAR_COUNT = 28

@OptIn(UnstableApi::class)
@Composable
fun PlaybackVisualizer(
    controller: MediaController?,
    playing: Boolean,
    palette: AlbumPalette
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var enabled by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }
    val levelsFlow = remember { MutableStateFlow(List(BAR_COUNT) { 0f }) }
    val levels by levelsFlow.collectAsState()
    val sessionId = controller?.audioSessionId ?: 0

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        enabled = granted
    }

    DisposableEffect(enabled, permissionGranted, sessionId) {
        var effect: Visualizer? = null
        if (enabled && permissionGranted && sessionId > 0) {
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
                                val end = (2 + (index + 1) * usable / BAR_COUNT).coerceAtMost(fft.size)
                                var peak = 0f
                                var bin = start
                                while (bin + 1 < end) {
                                    val real = fft[bin].toInt().toFloat()
                                    val imaginary = fft[bin + 1].toInt().toFloat()
                                    val magnitude = sqrt(real * real + imaginary * imaginary) / 180f
                                    if (magnitude > peak) peak = magnitude
                                    bin += 2
                                }
                                peak.coerceIn(0.04f, 1f)
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
                unavailable = false
            } catch (_: RuntimeException) {
                unavailable = true
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

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "VISUALIZER",
                    color = palette.onBackground.copy(alpha = 0.72f),
                    fontSize = 9.sp,
                    letterSpacing = 1.5.sp
                )
            }
            TextButton(
                onClick = {
                    if (enabled) {
                        enabled = false
                        levelsFlow.value = List(BAR_COUNT) { 0f }
                    } else if (permissionGranted) {
                        enabled = true
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            ) {
                Text(
                    when {
                        enabled -> "TURN OFF"
                        permissionGranted -> "ENABLE"
                        else -> "ENABLE"
                    },
                    color = palette.secondary,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Canvas(Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 2.dp)) {
            drawVisualizerBars(levels, palette.primary, palette.secondary, playing && enabled)
        }
    }
}

private fun DrawScope.drawVisualizerBars(
    levels: List<Float>,
    primary: Color,
    secondary: Color,
    active: Boolean
) {
    if (levels.isEmpty()) return
    val slot = size.width / levels.size
    val barWidth = (slot * 0.56f).coerceAtLeast(2f)
    levels.forEachIndexed { index, level ->
        val strength = if (active) level else 0.035f
        val barHeight = (size.height * (0.12f + strength * 0.88f)).coerceAtLeast(2f)
        val x = index * slot + (slot - barWidth) / 2f
        val y = (size.height - barHeight) / 2f
        drawRoundRect(
            color = if (index % 4 == 0) secondary.copy(alpha = 0.92f) else primary.copy(alpha = 0.88f),
            topLeft = Offset(x, y),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )
    }
}
