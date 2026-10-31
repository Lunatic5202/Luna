package com.luna.music.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import com.luna.music.ui.LocalLunaHazeState
import dev.chrisbanes.haze.hazeSource
import androidx.media3.session.MediaController
import com.luna.music.data.Track
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.luna.music.theme.AlbumPalette
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
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
    var showQueue by remember { mutableStateOf(false) }
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var volumeVisible by remember { mutableStateOf(false) }
    val volumeAlpha by animateFloatAsState(if (volumeVisible) 1f else 0f, animationSpec = tween(180), label = "volume-indicator")
    val progress by animateFloatAsState(position, animationSpec = tween(250, easing = FastOutSlowInEasing), label = "track-progress")
    var volumePercent by remember { mutableFloatStateOf(0f) }
    val artworkPulse by rememberInfiniteTransition(label = "artwork-pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.018f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "artwork-pulse-scale"
    )
    val artworkScale by animateFloatAsState(
        targetValue = if (playing) artworkPulse else 1f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "artwork-playing-scale"
    )

    LaunchedEffect(volumePercent) {
        if (volumeVisible) {
            delay(750)
            volumeVisible = false
        }
    }

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
        Modifier.fillMaxSize()
            .hazeSource(state = LocalLunaHazeState.current, zIndex = -1f)
            .background(Brush.verticalGradient(listOf(palette.glow.copy(alpha = .65f), palette.background, Color(0xFF07080D))))
    ) {
        AlbumArtwork(
            artworkUri = track.artworkUri?.toString(),
            modifier = Modifier.fillMaxSize().blur(42.dp).alpha(0.28f),
            palette = palette,
            shape = RectangleShape
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        palette.glow.copy(alpha = 0.68f),
                        palette.background.copy(alpha = 0.88f),
                        Color(0xFF07080D).copy(alpha = 0.96f)
                    )
                )
            )
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 26.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.onBackground)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING", color = palette.secondary, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
                    Text(track.album.ifBlank { "YOUR LIBRARY" }, color = palette.onBackground.copy(alpha = .65f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("☾", color = palette.primary, fontSize = 25.sp)
            }

            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                val glowAlpha by animateFloatAsState(
                    targetValue = if (playing) 0.34f else 0.12f,
                    animationSpec = tween(700, easing = FastOutSlowInEasing),
                    label = "artwork-ambient-glow"
                )
                Box(
                    Modifier.fillMaxSize(0.9f)
                        .graphicsLayer {
                            scaleX = artworkPulse
                            scaleY = artworkPulse
                            alpha = glowAlpha
                        }
                        .blur(34.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    palette.primary.copy(alpha = 0.62f),
                                    palette.secondary.copy(alpha = 0.24f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )
                AlbumArtwork(
                    artworkUri = track.artworkUri?.toString(),
                    modifier = Modifier.fillMaxSize(0.78f).graphicsLayer {
                        scaleX = artworkScale
                        scaleY = artworkScale
                    }.clip(CircleShape)
                        .pointerInput(controller, track.id) {
                            var totalX = 0f
                            var totalY = 0f
                            var axis = 0
                            detectDragGestures(
                                onDragStart = {
                                    totalX = 0f
                                    totalY = 0f
                                    axis = 0
                                },
                                onDragEnd = {
                                    if (axis == 1 && abs(totalX) > 72.dp.toPx()) {
                                        if (totalX < 0f) onNext() else onPrevious()
                                    }
                                },
                                onDragCancel = { },
                                onDrag = { change, amount ->
                                    change.consume()
                                    totalX += amount.x
                                    totalY += amount.y
                                    if (axis == 0 && (abs(totalX) > 10.dp.toPx() || abs(totalY) > 10.dp.toPx())) {
                                        axis = if (abs(totalX) > abs(totalY)) 1 else 2
                                    }
                                    if (axis == 2 && controller != null) {
                                        val nextVolume = (controller.volume - amount.y / size.height * 1.35f).coerceIn(0f, 1f)
                                        controller.volume = nextVolume
                                        volumePercent = nextVolume * 100f
                                        volumeVisible = true
                                    }
                                }
                            )
                        },
                    palette = palette,
                    shape = CircleShape
                )
                if (volumeAlpha > 0.01f) {
                    Box(
                        Modifier.alpha(volumeAlpha).clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.48f))
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            "${volumePercent.toInt()}%",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().height(306.dp),
                contentAlignment = Alignment.Center
            ) {
                PlaybackVisualizer(
                    controller = controller,
                    playing = playing,
                    palette = palette,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.18f }
                )
                LunaGlassSurface(
                    palette = palette,
                    modifier = Modifier.fillMaxWidth().height(292.dp).graphicsLayer { scaleX = 1.18f },
                    shape = GenericShape { size, _ ->
                        addOval(Rect(0f, 0f, size.width, size.height))
                    }
                ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Column(Modifier.animateContentSize(tween(260))) {
                    AnimatedContent(
                        targetState = track.title,
                        transitionSpec = { fadeIn(tween(240)) + scaleIn(initialScale = .97f, animationSpec = tween(240)) togetherWith fadeOut(tween(160)) },
                        label = "now-playing-title"
                    ) { title ->
                        Text(title, color = palette.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    AnimatedContent(
                        targetState = track.artist,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                        label = "now-playing-artist"
                    ) { artist ->
                        Text(artist, color = palette.secondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(12.dp))
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val shuffleTint by animateColorAsState(
                        if (controller?.shuffleModeEnabled == true) palette.primary else palette.onBackground.copy(alpha = .55f),
                        animationSpec = tween(240),
                        label = "shuffle-state"
                    )
                    IconButton(onClick = { controller?.shuffleModeEnabled = !(controller?.shuffleModeEnabled ?: false) }) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Toggle shuffle", tint = shuffleTint)
                    }
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track", tint = palette.onBackground, modifier = Modifier.padding(4.dp))
                    }
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.padding(horizontal = 24.dp).clip(CircleShape).background(
                            Brush.linearGradient(listOf(palette.primary, palette.secondary))
                        )
                    ) {
                        AnimatedContent(
                            targetState = playing,
                            transitionSpec = {
                                (fadeIn(tween(150)) + scaleIn(initialScale = .72f, animationSpec = tween(180))) togetherWith
                                    (fadeOut(tween(100)) + scaleOut(targetScale = .72f, animationSpec = tween(120)))
                            },
                            label = "main-play-pause"
                        ) { isPlaying ->
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play", tint = Color.White, modifier = Modifier.padding(9.dp))
                        }
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next track", tint = palette.onBackground, modifier = Modifier.padding(4.dp))
                    }
                    IconButton(onClick = {
                        controller?.repeatMode = when (controller?.repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    }) {
                        val repeat = controller?.repeatMode ?: Player.REPEAT_MODE_OFF
                        val repeatTint by animateColorAsState(
                            if (repeat == Player.REPEAT_MODE_OFF) palette.onBackground.copy(alpha = .55f) else palette.primary,
                            animationSpec = tween(240),
                            label = "repeat-state"
                        )
                        AnimatedContent(
                            targetState = repeat,
                            transitionSpec = { fadeIn(tween(160)) + scaleIn(initialScale = .82f, animationSpec = tween(180)) togetherWith fadeOut(tween(100)) },
                            label = "repeat-icon"
                        ) { mode ->
                            Icon(
                                if (mode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                contentDescription = "Repeat mode",
                                tint = repeatTint
                            )
                        }
                    }
                }
                androidx.compose.material3.TextButton(onClick = { showQueue = true }) {
                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = palette.secondary)
                    androidx.compose.foundation.layout.Spacer(Modifier.width(7.dp))
                    Text("UP NEXT · ${controller?.mediaItemCount ?: 0}", color = palette.secondary, fontSize = 10.sp, letterSpacing = 1.4.sp)
                }
                }
                }
            }
        }
        if (showQueue) {
            ModalBottomSheet(
                onDismissRequest = { showQueue = false },
                sheetState = queueSheetState,
                containerColor = palette.background,
                contentColor = palette.onBackground
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
                    Text("UP NEXT", color = palette.secondary, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Text("${controller?.mediaItemCount ?: 0} tracks in this session", color = palette.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp)) {
                        itemsIndexed((0 until (controller?.mediaItemCount ?: 0)).toList()) { index, _ ->
                            val item = controller?.getMediaItemAt(index)
                            val isCurrent = index == (controller?.currentMediaItemIndex ?: -1)
                            val queueRowColor by animateColorAsState(
                                if (isCurrent) palette.primary.copy(alpha = .18f) else Color.Transparent,
                                animationSpec = tween(260),
                                label = "queue-current-track"
                            )
                            Row(
                                Modifier.fillMaxWidth().animateItem().clip(RoundedCornerShape(14.dp))
                                    .background(queueRowColor)
                                    .clickable {
                                        controller?.seekTo(index, 0L)
                                        controller?.play()
                                        showQueue = false
                                    }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (isCurrent) "♫" else "${index + 1}", color = palette.secondary, modifier = Modifier.width(28.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item?.mediaMetadata?.title?.toString() ?: "Unknown track", color = palette.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                                    Text(item?.mediaMetadata?.artist?.toString() ?: "Unknown artist", color = palette.onBackground.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (isCurrent) Text("PLAYING", color = palette.secondary, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
