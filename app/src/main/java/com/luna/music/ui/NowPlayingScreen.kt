package com.luna.music.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.luna.music.data.Track
import com.luna.music.theme.AlbumPalette
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(
    track: Track,
    palette: AlbumPalette,
    playing: Boolean,
    controller: MediaController?,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    var position by remember(track.id) { mutableFloatStateOf(0f) }
    var duration by remember(track.id) { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(position, animationSpec = tween(250, easing = FastOutSlowInEasing), label = "track-progress")

    LaunchedEffect(controller, playing, track.id, dragging) {
        while (true) {
            if (!dragging && controller != null) {
                val total = controller.duration.coerceAtLeast(0L)
                duration = total.toFloat()
                position = controller.currentPosition.coerceAtLeast(0L).toFloat().coerceAtMost(duration)
            }
            delay(400)
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(palette.glow.copy(alpha = .65f), palette.background, Color(0xFF07080D)))
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.onBackground)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", color = palette.secondary, fontSize = 10.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.Bold)
                    Text(track.album.ifBlank { "YOUR LIBRARY" }, color = palette.onBackground.copy(alpha = .65f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("☾", color = palette.primary, fontSize = 25.sp)
            }

            AlbumArtwork(
                artworkUri = track.artworkUri?.toString(),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape),
                palette = palette
            )

            Column(Modifier.fillMaxWidth()) {
                Text(track.title, color = palette.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = palette.secondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(22.dp))
                Slider(
                    value = if (duration > 0f) progress.coerceIn(0f, duration) else 0f,
                    onValueChange = {
                        dragging = true
                        position = it
                    },
                    onValueChangeFinished = {
                        controller?.seekTo(position.toLong())
                        dragging = false
                    },
                    valueRange = 0f..duration.coerceAtLeast(1f),
                    enabled = duration > 0f,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = palette.onBackground,
                        activeTrackColor = palette.primary,
                        inactiveTrackColor = palette.onBackground.copy(alpha = .18f)
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position.toLong()), color = palette.onBackground.copy(alpha = .6f), fontSize = 11.sp)
                    Text(formatTime(duration.toLong()), color = palette.onBackground.copy(alpha = .6f), fontSize = 11.sp)
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track", tint = palette.onBackground, modifier = Modifier.padding(4.dp))
                    }
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.padding(horizontal = 24.dp).clip(CircleShape).background(
                            Brush.linearGradient(listOf(palette.primary, palette.secondary))
                        )
                    ) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.padding(9.dp))
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next track", tint = palette.onBackground, modifier = Modifier.padding(4.dp))
                    }
                }
            }
            Text("A LITTLE UNIVERSE, JUST FOR THIS SONG", color = palette.onBackground.copy(alpha = .35f), fontSize = 8.sp, letterSpacing = 1.8.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
