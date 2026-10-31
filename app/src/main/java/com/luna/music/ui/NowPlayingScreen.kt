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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.luna.music.ui.LocalLunaHazeState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.hazeSource
import androidx.media3.session.MediaController
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
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
import com.luna.music.theme.AlbumPalette
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingScreen(
    track: Track,
    palette: AlbumPalette,
    playing: Boolean,
    controller: MediaController?,
    sharedScope: SharedTransitionScope,
    visibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    openDrawer: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    var position by remember(track.id) { mutableFloatStateOf(0f) }
    var duration by remember(track.id) { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var queueItems by remember(controller) { mutableStateOf(readQueueItems(controller)) }
    val queueListState = rememberLazyListState()
    var draggedQueueKey by remember { mutableStateOf<String?>(null) }
    var draggedQueueOffset by remember { mutableFloatStateOf(0f) }
    val queueAutoScrollEdge = with(LocalDensity.current) { 54.dp.toPx() }
    val queueAutoScrollStep = with(LocalDensity.current) { 18.dp.toPx() }
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var volumeVisible by remember { mutableStateOf(false) }
    var showArtworkPeek by remember(track.id) { mutableStateOf(false) }
    // -1 = previous card highlighted, 0 = none, 1 = next card highlighted while holding the artwork.
    var peekSelection by remember(track.id) { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
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
    val peekScale by animateFloatAsState(
        targetValue = if (showArtworkPeek) 0.8f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "artwork-peek-scale"
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

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                queueItems = readQueueItems(controller)
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }

    val queueReorderEnabled = controller?.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS) == true
    val moveQueueEntry: (Int, Int) -> Unit = { fromIndex, toIndex ->
        if (fromIndex in queueItems.indices && toIndex in queueItems.indices && fromIndex != toIndex) {
            val reordered = queueItems.toMutableList()
            val moved = reordered.removeAt(fromIndex)
            reordered.add(toIndex, moved)
            queueItems = reordered
            if (queueReorderEnabled) controller?.moveMediaItem(fromIndex, toIndex)
        }
    }

    LaunchedEffect(draggedQueueKey) {
        while (draggedQueueKey != null) {
            val draggedInfo = queueListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == draggedQueueKey }
            if (draggedInfo != null) {
                val center = draggedInfo.offset + draggedInfo.size / 2f + draggedQueueOffset
                val layout = queueListState.layoutInfo
                when {
                    center < layout.viewportStartOffset + queueAutoScrollEdge -> queueListState.scrollBy(-queueAutoScrollStep)
                    center > layout.viewportEndOffset - queueAutoScrollEdge -> queueListState.scrollBy(queueAutoScrollStep)
                }
            }
            delay(16L)
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
                    title = item.mediaMetadata.title?.toString().orEmpty(),
                    artist = item.mediaMetadata.artist?.toString().orEmpty()
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
                    title = item.mediaMetadata.title?.toString().orEmpty(),
                    artist = item.mediaMetadata.artist?.toString().orEmpty()
                )
            }
    }

    val liquidBackdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalLunaLiquidBackdrop provides liquidBackdrop) {
    Box(
        Modifier.fillMaxSize()
            .hazeSource(state = LocalLunaHazeState.current, zIndex = -1f)
    ) {
        // Everything the glass should see must be *inside* layerBackdrop: modifiers placed
        // before it (like a background) are not recorded.
        Box(Modifier.fillMaxSize().layerBackdrop(liquidBackdrop)) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(palette.glow.copy(alpha = .65f), palette.background, palette.backgroundBottom)))
        )
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
                        palette.backgroundBottom.copy(alpha = 0.96f)
                    )
                )
            )
        )
        // Light sources behind the dome: this colour is what the frosted glass blurs and tints.
        Box(
            Modifier.fillMaxSize().drawBehind {
                val w = size.width
                val h = size.height
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(palette.primary.copy(alpha = 0.42f), palette.primary.copy(alpha = 0f)),
                        center = Offset(w * 0.5f, h * 0.80f),
                        radius = w * 0.95f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(palette.secondary.copy(alpha = 0.26f), palette.secondary.copy(alpha = 0f)),
                        center = Offset(w * 0.12f, h * 0.90f),
                        radius = w * 0.75f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(palette.glow.copy(alpha = 0.42f), palette.glow.copy(alpha = 0f)),
                        center = Offset(w * 0.92f, h * 0.72f),
                        radius = w * 0.70f
                    )
                )
            }
        )
        }
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val domeBody = DOME_HEIGHT + navBottom
        val domeTotal = domeBody + DOME_RING_INSET
        val domeShape = remember {
            GenericShape { size, _ ->
                val radius = size.width * DOME_RADIUS_FRACTION
                val cx = size.width / 2f
                addOval(Rect(cx - radius, 0f, cx + radius, radius * 2f))
            }
        }
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
                IconButton(onClick = { showLyrics = true }) {
                    Icon(Icons.Default.Lyrics, contentDescription = "Show lyrics", tint = palette.onBackground)
                }
                IconButton(onClick = openDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "Open navigation", tint = palette.onBackground)
                }
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
                        modifier = Modifier.lunaSharedArtwork(sharedScope, visibilityScope).fillMaxSize(0.84f)
                            .pointerInput(controller, track.id, previousArtwork != null, nextArtwork != null) {
                                val hasPrevious = previousArtwork != null
                                val hasNext = nextArtwork != null
                                val chooseDistance = 44.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val held = awaitHoldOrNull(down)
                                    if (held != null) {
                                        // Hold: peek the neighbouring tracks. Slide toward one and release to play it.
                                        showArtworkPeek = true
                                        peekSelection = 0
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val originX = held.position.x
                                        val finished = drag(held.id) { change ->
                                            change.consume()
                                            val dx = change.position.x - originX
                                            val selection = when {
                                                dx > chooseDistance && hasNext -> 1
                                                dx < -chooseDistance && hasPrevious -> -1
                                                else -> 0
                                            }
                                            if (selection != peekSelection) {
                                                peekSelection = selection
                                                if (selection != 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            val limit = chooseDistance * 1.6f
                                            scope.launch { dragX.snapTo(dx.coerceIn(-limit, limit) * 0.3f) }
                                        }
                                        val chosen = if (finished) peekSelection else 0
                                        showArtworkPeek = false
                                        peekSelection = 0
                                        scope.launch { dragX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
                                        if (chosen != 0) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (chosen > 0) onNext() else onPrevious()
                                        }
                                    } else {
                                        // Quick drag: swipe sideways to change track, up or down for volume.
                                        var totalX = 0f
                                        var totalY = 0f
                                        var axis = 0
                                        val finished = drag(down.id) { change ->
                                            change.consume()
                                            val amount = change.positionChange()
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
                                        if (finished && axis == 1 && abs(totalX) > 72.dp.toPx()) {
                                            if (totalX < 0f) onNext() else onPrevious()
                                        }
                                        scope.launch {
                                            dragX.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 260f))
                                        }
                                    }
                                }
                            }
                            .graphicsLayer {
                                scaleX = artworkScale * peekScale
                                scaleY = artworkScale * peekScale
                                translationX = dragX.value
                                rotationZ = dragX.value / 34f
                            }.clip(CircleShape),
                        palette = palette,
                        shape = CircleShape
                    )
                    ArtworkPeekOverlay(
                        visible = showArtworkPeek,
                        item = previousArtwork,
                        caption = "PREVIOUS",
                        emptyLabel = "Start of queue",
                        selected = peekSelection < 0,
                        palette = palette,
                        modifier = Modifier.align(Alignment.CenterStart).offset(x = (-8).dp),
                        fromLeft = true
                    )
                    ArtworkPeekOverlay(
                        visible = showArtworkPeek,
                        item = nextArtwork,
                        caption = "NEXT",
                        emptyLabel = "End of queue",
                        selected = peekSelection > 0,
                        palette = palette,
                        modifier = Modifier.align(Alignment.CenterEnd).offset(x = 8.dp),
                        fromLeft = false
                    )
                    ArtworkPeekHint(
                        visible = showArtworkPeek,
                        text = when {
                            peekSelection < 0 -> "Release to play previous"
                            peekSelection > 0 -> "Release to play next"
                            else -> "Slide to a side and let go"
                        },
                        palette = palette,
                        modifier = Modifier.align(Alignment.BottomCenter).offset(y = 6.dp)
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
                shape = domeShape,
                frosted = true
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
                            modifier = Modifier.semantics {
                                contentDescription = "Playback position"
                            },
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
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = palette.secondary)
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
                    Text("${queueItems.size} tracks · hold and drag to reorder", color = palette.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp),
                        state = queueListState
                    ) {
                        itemsIndexed(queueItems, key = { _, entry -> entry.key }) { index, entry ->
                            val item = entry.mediaItem
                            val isDragging = draggedQueueKey == entry.key
                            val isCurrent = index == (controller?.currentMediaItemIndex ?: -1)
                            val queueRowColor by animateColorAsState(
                                when {
                                    isDragging -> palette.primary.copy(alpha = .28f)
                                    isCurrent -> palette.primary.copy(alpha = .18f)
                                    else -> Color.Transparent
                                },
                                animationSpec = tween(260),
                                label = "queue-current-track"
                            )
                            Row(
                                Modifier.fillMaxWidth().animateItem()
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        translationY = if (isDragging) draggedQueueOffset else 0f
                                        scaleX = if (isDragging) 1.025f else 1f
                                        scaleY = if (isDragging) 1.025f else 1f
                                        shadowElevation = if (isDragging) 14.dp.toPx() else 0f
                                    }
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(queueRowColor)
                                    .pointerInput(entry.key, queueReorderEnabled) {
                                        if (queueReorderEnabled) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggedQueueKey = entry.key
                                                    draggedQueueOffset = 0f
                                                },
                                                onDragEnd = {
                                                    draggedQueueKey = null
                                                    draggedQueueOffset = 0f
                                                },
                                                onDragCancel = {
                                                    draggedQueueKey = null
                                                    draggedQueueOffset = 0f
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    draggedQueueOffset += amount.y
                                                    val draggedInfo = queueListState.layoutInfo.visibleItemsInfo
                                                        .firstOrNull { it.key == entry.key }
                                                    if (draggedInfo != null) {
                                                        val draggedCenter = draggedInfo.offset +
                                                            draggedInfo.size / 2f + draggedQueueOffset
                                                        val target = queueListState.layoutInfo.visibleItemsInfo
                                                            .asSequence()
                                                            .filter { it.key != entry.key }
                                                            .minByOrNull {
                                                                kotlin.math.abs(it.offset + it.size / 2f - draggedCenter)
                                                            }
                                                        if (target != null &&
                                                            kotlin.math.abs(target.offset + target.size / 2f - draggedCenter) > target.size * 0.42f
                                                        ) {
                                                            val fromIndex = queueItems.indexOfFirst { it.key == entry.key }
                                                            val toIndex = target.index
                                                            if (fromIndex >= 0 && toIndex in queueItems.indices && fromIndex != toIndex) {
                                                                draggedQueueOffset = draggedCenter -
                                                                    (target.offset + draggedInfo.size / 2f)
                                                                moveQueueEntry(fromIndex, toIndex)
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    .clickable {
                                        controller?.seekTo(index, 0L)
                                        controller?.play()
                                        showQueue = false
                                    }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (isCurrent) "♫" else "${index + 1}", color = palette.secondary, modifier = Modifier.width(28.dp))
                                AlbumArtwork(
                                    artworkUri = item.mediaMetadata.artworkUri?.toString(),
                                    modifier = Modifier.size(46.dp),
                                    palette = palette,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.mediaMetadata.title?.toString() ?: "Unknown track", color = palette.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                                    Text(item.mediaMetadata.artist?.toString() ?: "Unknown artist", color = palette.onBackground.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (isCurrent) Text("PLAYING", color = palette.secondary, fontSize = 9.sp)
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "Hold and drag to reorder",
                                    tint = palette.onBackground.copy(alpha = if (queueReorderEnabled) 0.48f else 0.18f)
                                )
                            }
                        }
                    }
                }
            }
        }
        LyricsOverlay(
            visible = showLyrics,
            track = track,
            palette = palette,
            durationSeconds = ((controller?.duration?.takeIf { it > 0L } ?: duration.toLong()) / 1000f),
            onClose = { showLyrics = false }
        )
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
}

private data class QueueItemEntry(
    val key: String,
    val mediaItem: MediaItem
)

private fun readQueueItems(controller: MediaController?): List<QueueItemEntry> {
    if (controller == null) return emptyList()
    val occurrences = mutableMapOf<String, Int>()
    return (0 until controller.mediaItemCount).map { index ->
        val item = controller.getMediaItemAt(index)
        val identity = item.mediaId.ifBlank {
            item.mediaMetadata.title?.toString().orEmpty().ifBlank { "queue-item-$index" }
        }
        val occurrence = occurrences[identity] ?: 0
        occurrences[identity] = occurrence + 1
        QueueItemEntry("$identity#$occurrence", item)
    }
}

private data class ArtworkPeekItem(
    val id: String,
    val artworkUri: String?,
    val title: String,
    val artist: String
)

@Composable
private fun ArtworkPeekOverlay(
    visible: Boolean,
    item: ArtworkPeekItem?,
    caption: String,
    emptyLabel: String,
    selected: Boolean,
    palette: AlbumPalette,
    modifier: Modifier,
    fromLeft: Boolean
) {
    val cardScale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "peek-card-scale"
    )
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(150)) +
            scaleIn(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) +
            slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) {
                if (fromLeft) -it / 2 else it / 2
            },
        exit = fadeOut(tween(120)) + scaleOut(animationSpec = tween(180)) +
            slideOutHorizontally(tween(180)) { if (fromLeft) -it / 3 else it / 3 }
    ) {
        ArtworkPeekCard(
            item = item,
            caption = caption,
            emptyLabel = emptyLabel,
            selected = selected,
            palette = palette,
            modifier = Modifier.graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
        )
    }
}

@Composable
private fun ArtworkPeekCard(
    item: ArtworkPeekItem?,
    caption: String,
    emptyLabel: String,
    selected: Boolean,
    palette: AlbumPalette,
    modifier: Modifier
) {
    val ringColor = if (selected) palette.primary else Color.White.copy(alpha = 0.5f)
    LunaGlassSurface(
        palette = palette,
        modifier = modifier.width(108.dp).alpha(if (item == null) 0.62f else 1f),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(if (selected) palette.primary.copy(alpha = 0.22f) else Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                caption,
                color = if (selected) palette.primary else palette.onBackground.copy(alpha = 0.7f),
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                Modifier.size(72.dp)
                    .clip(CircleShape)
                    .border(if (selected) 2.dp else 1.dp, ringColor, CircleShape)
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                if (item != null) {
                    AlbumArtwork(
                        artworkUri = item.artworkUri,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        palette = palette,
                        shape = CircleShape
                    )
                } else {
                    Text("\u2014", color = palette.onBackground.copy(alpha = 0.6f), fontSize = 24.sp)
                }
            }
            Text(
                item?.title ?: emptyLabel,
                modifier = Modifier.fillMaxWidth(),
                color = palette.onBackground,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item != null && item.artist.isNotBlank()) {
                Text(
                    item.artist,
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.onBackground.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ArtworkPeekHint(
    visible: Boolean,
    text: String,
    palette: AlbumPalette,
    modifier: Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(180, delayMillis = 80)) + slideInVertically(tween(220)) { it / 2 },
        exit = fadeOut(tween(100))
    ) {
        LunaGlassSurface(palette = palette, shape = RoundedCornerShape(50), refraction = false) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = palette.onBackground,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Waits for [down] to be held still for the system long-press timeout. Returns the latest pointer
 * state when the hold completes, or null if the finger lifted, moved beyond touch slop or the
 * gesture was taken over first.
 */
private suspend fun AwaitPointerEventScope.awaitHoldOrNull(down: PointerInputChange): PointerInputChange? {
    var latest = down
    val interrupted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
            latest = change
            if (change.isConsumed || !change.pressed) return@withTimeoutOrNull true
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                return@withTimeoutOrNull true
            }
        }
        @Suppress("UNREACHABLE_CODE")
        false
    }
    return if (interrupted == null) latest else null
}

private val DOME_HEIGHT = 340.dp

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

