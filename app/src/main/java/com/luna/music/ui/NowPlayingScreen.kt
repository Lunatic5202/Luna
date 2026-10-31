package com.luna.music.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import com.luna.music.ui.LocalLunaHazeState
import dev.chrisbanes.haze.hazeSource
import androidx.media3.session.MediaController
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.luna.music.data.Track
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.luna.music.theme.AlbumPalette
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingScreen(
    track: Track,
    palette: AlbumPalette,
    playing: Boolean,
    controller: MediaController?,
    sharedScope: SharedTransitionScope,
    visibilityScope: AnimatedVisibilityScope,
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
    var showArtworkPeek by remember(track.id) { mutableStateOf(false) }
    val volumeAlpha by animateFloatAsState(if (volumeVisible) 1f else 0f, animationSpec = tween(180), label = "volume-indicator")
    val progress by animateFloatAsState(position, animationSpec = tween(250, easing = FastOutSlowInEasing), label = "track-progress")
    var volumePercent by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
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
        targetValue = if (playing) artworkPulse else 0.93f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
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

    val previousArtwork = controller?.let { player ->
        player.previousMediaItemIndex
            .takeIf { it >= 0 && it < player.mediaItemCount }
            ?.let(player::getMediaItemAt)
            ?.let { item ->
                ArtworkPeekItem(
                    id = item.mediaId,
                    artworkUri = item.mediaMetadata.artworkUri?.toString(),
                    title = item.mediaMetadata.title?.toString().orEmpty()
                )
            }
    }
    val nextArtwork = controller?.let { player ->
        player.nextMediaItemIndex
            .takeIf { it >= 0 && it < player.mediaItemCount }
            ?.let(player::getMediaItemAt)
            ?.let { item ->
                ArtworkPeekItem(
                    id = item.mediaId,
                    artworkUri = item.mediaMetadata.artworkUri?.toString(),
                    title = item.mediaMetadata.title?.toString().orEmpty()
                )
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
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val domeBody = DOME_HEIGHT + navBottom
        val domeTotal = domeBody + DOME_RING_INSET
        Column(
            Modifier.fillMaxSize().statusBarsPadding()
                .padding(start = 26.dp, end = 26.dp, top = 18.dp, bottom = domeTotal)
        ) {
            Row(
                Modifier.fillMaxWidth().then(
                    with(visibilityScope) {
                        Modifier.animateEnterExit(
                            enter = fadeIn(tween(420, delayMillis = 140)) +
                                slideInVertically(tween(480, easing = FastOutSlowInEasing)) { -it / 2 },
                            exit = fadeOut(tween(160))
                        )
                    }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.onBackground)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING", color = palette.secondary, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
                    Text(track.album.ifBlank { "YOUR LIBRARY" }, color = palette.onBackground.copy(alpha = .65f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("\u263E", color = palette.primary, fontSize = 25.sp)
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
                    val glowAlpha by animateFloatAsState(
                        targetValue = if (playing) 0.34f else 0.12f,
                        animationSpec = tween(700, easing = FastOutSlowInEasing),
                        label = "artwork-ambient-glow"
                    )
                    Box(
                        Modifier.fillMaxSize(0.92f)
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
                        modifier = Modifier.lunaSharedArtwork(sharedScope, visibilityScope).fillMaxSize(0.84f).graphicsLayer {
                            scaleX = artworkScale
                            scaleY = artworkScale
                            translationX = dragX.value
                            rotationZ = dragX.value / 34f
                        }.clip(CircleShape)
                            .pointerInput(track.id) {
                                detectTapGestures(
                                    onPress = {
                                        tryAwaitRelease()
                                        showArtworkPeek = false
                                    },
                                    onLongPress = { showArtworkPeek = true }
                                )
                            }
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
                                        scope.launch {
                                            dragX.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 260f))
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch {
                                            dragX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f))
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        totalX += amount.x
                                        totalY += amount.y
                                        if (axis == 0 && (abs(totalX) > 10.dp.toPx() || abs(totalY) > 10.dp.toPx())) {
                                            axis = if (abs(totalX) > abs(totalY)) 1 else 2
                                        }
                                        if (axis == 1) {
                                            scope.launch { dragX.snapTo(totalX * 0.5f) }
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
                    ArtworkPeekOverlay(
                        visible = showArtworkPeek,
                        item = previousArtwork,
                        palette = palette,
                        modifier = Modifier.align(Alignment.CenterStart).offset(x = (-2).dp),
                        fromLeft = true
                    )
                    ArtworkPeekOverlay(
                        visible = showArtworkPeek,
                        item = nextArtwork,
                        palette = palette,
                        modifier = Modifier.align(Alignment.CenterEnd).offset(x = 2.dp),
                        fromLeft = false
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
            }
        }

        // Bottom dome: a large circle cropped by the screen edges.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(domeTotal).then(
                with(visibilityScope) {
                    Modifier.animateEnterExit(
                        enter = slideInVertically(tween(620, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(260)),
                        exit = slideOutVertically(tween(360, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220))
                    )
                }
            )
        ) {
            PlaybackVisualizer(
                controller = controller,
                playing = playing,
                palette = palette,
                modifier = Modifier.fillMaxSize()
            )
            LunaGlassSurface(
                palette = palette,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(domeBody),
                shape = GenericShape { size, _ ->
                    val radius = size.width * DOME_RADIUS_FRACTION
                    val cx = size.width / 2f
                    addOval(Rect(cx - radius, 0f, cx + radius, radius * 2f))
                }
            ) {
                Column(
                    Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 50.dp, bottom = navBottom + 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.fillMaxWidth().animateContentSize(tween(260)), horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedContent(
                            targetState = track.title,
                            modifier = Modifier.fillMaxWidth(),
                            transitionSpec = {
                                (fadeIn(tween(240)) + scaleIn(initialScale = .97f, animationSpec = tween(240)) +
                                    slideInVertically(animationSpec = tween(260, easing = FastOutSlowInEasing)) { it / 4 }) togetherWith
                                    (fadeOut(tween(150)) + slideOutVertically(animationSpec = tween(180)) { -it / 5 })
                            },
                            label = "now-playing-title"
                        ) { title ->
                            Text(title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = palette.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        AnimatedContent(
                            targetState = track.artist,
                            modifier = Modifier.fillMaxWidth(),
                            transitionSpec = {
                                (fadeIn(tween(220)) + slideInVertically(animationSpec = tween(240)) { it / 3 }) togetherWith
                                    (fadeOut(tween(140)) + slideOutVertically(animationSpec = tween(160)) { -it / 4 })
                            },
                            label = "now-playing-artist"
                        ) { artist ->
                            Text(artist, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = palette.secondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Column(Modifier.fillMaxWidth()) {
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
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        val shuffleTint by animateColorAsState(
                            if (controller?.shuffleModeEnabled == true) palette.primary else palette.onBackground.copy(alpha = .55f),
                            animationSpec = tween(240),
                            label = "shuffle-state"
                        )
                        BouncyIconButton(onClick = { controller?.shuffleModeEnabled = !(controller?.shuffleModeEnabled ?: false) }) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Toggle shuffle", tint = shuffleTint)
                        }
                        BouncyIconButton(onClick = onPrevious) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track", tint = palette.onBackground, modifier = Modifier.size(32.dp))
                        }
                        BouncyIconButton(
                            onClick = onToggle,
                            pressedScale = 0.9f,
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(
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
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                        }
                        BouncyIconButton(onClick = onNext) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next track", tint = palette.onBackground, modifier = Modifier.size(32.dp))
                        }
                        BouncyIconButton(onClick = {
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
                        Spacer(Modifier.width(7.dp))
                        Text("UP NEXT \u00B7 ${controller?.mediaItemCount ?: 0}", color = palette.secondary, fontSize = 10.sp, letterSpacing = 1.4.sp)
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
        // Keep the dismiss gesture in a narrow top strip so it does not compete
        // with artwork swipes, the seek bar, or the player controls.
        Box(
            Modifier.align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(onBack) {
                    var dragDistance = 0f
                    var dismissed = false
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragDistance = 0f
                            dismissed = false
                        },
                        onVerticalDrag = { change, amount ->
                            dragDistance += amount
                            if (!dismissed && dragDistance >= 72.dp.toPx()) {
                                dismissed = true
                                change.consume()
                                onBack()
                            }
                        }
                    )
                }
        )
    }
}

private data class ArtworkPeekItem(
    val id: String,
    val artworkUri: String?,
    val title: String
)

@Composable
private fun ArtworkPeekOverlay(
    visible: Boolean,
    item: ArtworkPeekItem?,
    palette: AlbumPalette,
    modifier: Modifier,
    fromLeft: Boolean
) {
    AnimatedVisibility(
        visible = visible && item != null,
        modifier = modifier,
        enter = fadeIn(tween(150)) +
            scaleIn(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) +
            slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) {
                if (fromLeft) -it / 2 else it / 2
            },
        exit = fadeOut(tween(120)) + scaleOut(animationSpec = tween(180)) +
            slideOutHorizontally(tween(180)) { if (fromLeft) -it / 3 else it / 3 }
    ) {
        item?.let {
            ArtworkPeekBubble(
                item = it,
                palette = palette,
                horizontalAlignment = if (fromLeft) Alignment.Start else Alignment.End
            )
        }
    }
}

@Composable
private fun ArtworkPeekBubble(
    item: ArtworkPeekItem,
    palette: AlbumPalette,
    horizontalAlignment: Alignment.Horizontal
) {
    Column(
        modifier = Modifier.width(92.dp),
        horizontalAlignment = horizontalAlignment
    ) {
        Box(
            Modifier.size(70.dp)
                .shadow(16.dp, CircleShape)
                .clip(CircleShape)
                .background(palette.surface)
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.82f), palette.secondary.copy(alpha = 0.6f))
                    ),
                    CircleShape
                )
                .padding(4.dp)
        ) {
            AlbumArtwork(
                artworkUri = item.artworkUri,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                palette = palette,
                shape = CircleShape
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            modifier = Modifier.fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 5.dp),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = if (horizontalAlignment == Alignment.Start) TextAlign.Start else TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val DOME_HEIGHT = 340.dp

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

