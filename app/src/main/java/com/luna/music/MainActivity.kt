@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.luna.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.unit.IntOffset
import com.luna.music.ui.EqualizerBars
import com.luna.music.ui.bouncyClickable
import com.luna.music.ui.lunaSharedArtwork
import com.luna.music.ui.staggeredEntrance
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.luna.music.data.MusicRepository
import com.luna.music.data.Track
import com.luna.music.playback.LunaPlaybackService
import com.luna.music.theme.*
import com.luna.music.ui.LunaGlassSurface
import com.luna.music.ui.AlbumArtwork
import com.luna.music.ui.NowPlayingScreen
import com.luna.music.ui.LocalLunaHazeState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private val Muted = Color(0xFFAAA7BA)

class MainActivity : ComponentActivity() {
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private lateinit var repository: MusicRepository
    private lateinit var themeEngine: LunaThemeEngine
    private var tracks by mutableStateOf<List<Track>>(emptyList())
    private var selectedTrack by mutableStateOf<Track?>(null)
    private var isPlaying by mutableStateOf(false)
    private var permissionGranted by mutableStateOf(false)
    private var currentPalette by mutableStateOf(AlbumPalette.Fallback)
    private var paletteLoadToken = 0
    private var showNowPlaying by mutableStateOf(false)

    private fun loadLibrary() {
        lifecycleScope.launch {
            tracks = repository.loadTracks()
            // The session may reconnect before the local library scan finishes.
            mediaController?.currentMediaItem?.let { item ->
                tracks.firstOrNull { it.id.toString() == item.mediaId }?.let { track ->
                    selectedTrack = track
                    updatePaletteFor(track)
                }
            }
        }
    }

    private fun updatePaletteFor(track: Track?) {
        val token = ++paletteLoadToken
        lifecycleScope.launch {
            val palette = themeEngine.paletteFor(track?.artworkUri, track?.uri)
            if (token == paletteLoadToken) currentPalette = palette
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionGranted = granted
            if (granted) loadLibrary()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = MusicRepository(this)
        themeEngine = LunaThemeEngine(this)

        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                         else Manifest.permission.READ_EXTERNAL_STORAGE
        permissionGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) loadLibrary()

        val sessionToken = SessionToken(
            this,
            android.content.ComponentName(this, LunaPlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync().also { future ->
            future.addListener({
                if (isFinishing || isDestroyed) return@addListener
                mediaController = future.get()
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                        isPlaying = isPlayingNow
                    }

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int
                    ) {
                        val track = tracks.firstOrNull { it.id.toString() == mediaItem?.mediaId } ?: return
                        selectedTrack = track
                        updatePaletteFor(track)
                    }
                })
                mediaController?.currentMediaItem?.let { item ->
                    tracks.firstOrNull { it.id.toString() == item.mediaId }?.let { track ->
                        selectedTrack = track
                        updatePaletteFor(track)
                    }
                }
            }, ContextCompat.getMainExecutor(this))
        }

        setContent {
            val animatedPalette = rememberLunaPalette(currentPalette)
            val hazeState = remember { HazeState() }
            // Back closes the player instead of leaving the app.
            BackHandler(enabled = showNowPlaying) { showNowPlaying = false }
            androidx.compose.runtime.CompositionLocalProvider(LocalLunaHazeState provides hazeState) {
            LunaTheme(animatedPalette) {
                Crossfade(
                    targetState = permissionGranted,
                    animationSpec = tween(560),
                    label = "intro-to-library"
                ) { granted ->
                if (!granted) {
                    LunaPermissionIntro(
                        palette = animatedPalette,
                        requestPermission = { permissionLauncher.launch(permission) }
                    )
                } else {
                SharedTransitionLayout {
                AnimatedContent(
                    targetState = showNowPlaying && selectedTrack != null,
                    transitionSpec = {
                        if (targetState) {
                            (fadeIn(tween(420, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = .96f, animationSpec = tween(520, easing = FastOutSlowInEasing))) togetherWith
                                (fadeOut(tween(260)) +
                                    scaleOut(targetScale = 1.04f, animationSpec = tween(420, easing = FastOutSlowInEasing)))
                        } else {
                            (fadeIn(tween(380, delayMillis = 60)) +
                                scaleIn(initialScale = 1.04f, animationSpec = tween(480, easing = FastOutSlowInEasing))) togetherWith
                                (fadeOut(tween(240)) +
                                    scaleOut(targetScale = .96f, animationSpec = tween(420, easing = FastOutSlowInEasing)))
                        }
                    },
                    label = "screen-transition"
                ) { showPlayer ->
                    if (showPlayer && selectedTrack != null) {
                    NowPlayingScreen(
                        track = selectedTrack!!,
                        palette = animatedPalette,
                        playing = isPlaying,
                        controller = mediaController,
                        sharedScope = this@SharedTransitionLayout,
                        visibilityScope = this@AnimatedContent,
                        onBack = { showNowPlaying = false },
                        onToggle = { if (mediaController?.isPlaying == true) mediaController?.pause() else mediaController?.play() },
                        onNext = { mediaController?.seekToNextMediaItem() },
                        onPrevious = { mediaController?.seekToPreviousMediaItem() }
                    )
                    } else {
                    LunaHome(
                        tracks = tracks,
                        selectedTrack = selectedTrack,
                        playing = isPlaying,
                        palette = animatedPalette,
                        sharedScope = this@SharedTransitionLayout,
                        visibilityScope = this@AnimatedContent,
                        openNowPlaying = { showNowPlaying = true },
                        play = { track ->
                            val index = tracks.indexOf(track).coerceAtLeast(0)
                            val mediaItems = tracks.map { item ->
                                MediaItem.Builder()
                                    .setUri(item.uri)
                                    .setMediaId(item.id.toString())
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(item.title)
                                            .setArtist(item.artist)
                                            .setAlbumTitle(item.album)
                                            .apply { item.artworkUri?.let(::setArtworkUri) }
                                            .build()
                                    )
                                    .build()
                            }
                            mediaController?.let { controller ->
                                controller.setMediaItems(mediaItems, index, 0L)
                                controller.prepare()
                                controller.play()
                            }
                            selectedTrack = track
                            updatePaletteFor(track)
                        },
                        toggle = {
                            if (mediaController?.isPlaying == true) {
                                mediaController?.pause()
                                isPlaying = false
                            } else {
                                mediaController?.play()
                                isPlaying = true
                            }
                        },
                        next = {
                            mediaController?.seekToNextMediaItem()
                            isPlaying = true
                        },
                        previous = {
                            mediaController?.seekToPreviousMediaItem()
                            isPlaying = true
                        }
                    )
                    }
                }
                }
                }
                }
            }
            }
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        super.onDestroy()
    }
}

@Composable
private fun LunaPermissionIntro(
    palette: AlbumPalette,
    requestPermission: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(palette.glow.copy(alpha = .55f), palette.background, Color(0xFF07080D))
            )
        ).statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text("L U N A", color = palette.onBackground, fontSize = 30.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 6.sp,
                modifier = Modifier.staggeredEntrance(0, 110))
            Spacer(Modifier.height(42.dp))
            Text("Your music. Your space.", color = palette.onBackground,
                fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.staggeredEntrance(1, 110))
            Spacer(Modifier.height(28.dp))
            Column(Modifier.staggeredEntrance(2, 110)) {
                Text("NEEDED", color = palette.secondary, fontSize = 10.sp,
                    letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("Music library", color = palette.onBackground,
                    fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text("Find and play music stored on this device.",
                    color = palette.onBackground.copy(alpha = .58f), fontSize = 13.sp)
            }
            Spacer(Modifier.height(24.dp))
            Column(Modifier.staggeredEntrance(3, 110)) {
                Text("OPTIONAL", color = palette.secondary, fontSize = 10.sp,
                    letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("Playback visualizer", color = palette.onBackground,
                    fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text("Android may show a microphone permission. It is used only to read this player's audio session; LUNA does not record your voice.",
                    color = palette.onBackground.copy(alpha = .58f), fontSize = 13.sp)
            }
            Spacer(Modifier.height(34.dp))
            Button(
                onClick = requestPermission,
                modifier = Modifier.fillMaxWidth().height(54.dp).staggeredEntrance(4, 110),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
            ) {
                Text("Continue", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LunaHome(
    tracks: List<Track>,
    selectedTrack: Track?,
    playing: Boolean,
    palette: AlbumPalette,
    play: (Track) -> Unit,
    toggle: () -> Unit,
    next: () -> Unit,
    previous: () -> Unit,
    sharedScope: SharedTransitionScope,
    visibilityScope: AnimatedVisibilityScope,
    openNowPlaying: () -> Unit
) {
    Box(
        Modifier.fillMaxSize()
            .hazeSource(state = LocalLunaHazeState.current, zIndex = -1f)
            .background(Brush.verticalGradient(listOf(palette.glow.copy(alpha = .42f), palette.background, Color(0xFF07080D))))
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("L U N A", color = palette.onBackground, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                }
                Text("☾", color = palette.primary, fontSize = 32.sp)
            }
            Spacer(Modifier.height(30.dp))
            Text("Library", color = palette.onBackground, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))

            if (tracks.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No music found. Add audio files to your device.", color = Muted)
                }
            } else {
                val libraryListState = rememberLazyListState()
                val animatedTrackIds = remember { mutableSetOf<Long>() }
                var hasUserScrolled by remember { mutableStateOf(false) }
                LaunchedEffect(libraryListState) {
                    snapshotFlow {
                        libraryListState.firstVisibleItemIndex > 0 ||
                            libraryListState.firstVisibleItemScrollOffset > 0
                    }.first { it }
                    hasUserScrolled = true
                }
                LazyColumn(Modifier.weight(1f), state = libraryListState, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    itemsIndexed(tracks, key = { _, item -> item.id }) { index, track ->
                        // Lazy items leave composition while scrolling. Only animate
                        // their first appearance before the user starts scrolling.
                        val animateEntrance = remember(track.id) {
                            !hasUserScrolled && animatedTrackIds.add(track.id)
                        }
                        val isSelected = selectedTrack?.id == track.id
                        val rowColor by animateColorAsState(
                            targetValue = if (isSelected) palette.primary.copy(alpha = .19f) else Color.White.copy(alpha = .045f),
                            animationSpec = tween(320),
                            label = "library-row-selection"
                        )
                        Row(
                            Modifier.fillMaxWidth().animateItem()
                                .then(if (animateEntrance) Modifier.staggeredEntrance(index) else Modifier)
                                .animateContentSize(tween(260))
                                .bouncyClickable(RoundedCornerShape(17.dp), rowColor) { play(track) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AlbumArtwork(track.artworkUri?.toString(), Modifier.size(46.dp), palette)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, color = palette.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1)
                            }
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(tween(180)) + scaleIn(initialScale = .88f, animationSpec = tween(180)),
                                exit = fadeOut(tween(140)) + scaleOut(targetScale = .88f, animationSpec = tween(140))
                            ) {
                                EqualizerBars(palette.secondary, playing)
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedTrack != null,
                enter = fadeIn(tween(300)) +
                    slideInVertically(spring<IntOffset>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)) { it } +
                    expandVertically(tween(300)),
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 3 } + shrinkVertically(tween(180))
            ) {
                selectedTrack?.let { track ->
                    Spacer(Modifier.height(12.dp))
                    LunaGlassSurface(palette, Modifier.fillMaxWidth()) {
                        Column(Modifier.clickable(onClick = openNowPlaying).animateContentSize(tween(240)).padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AlbumArtwork(track.artworkUri?.toString(), Modifier.lunaSharedArtwork(sharedScope, visibilityScope).size(48.dp), palette, CircleShape)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    AnimatedContent(
                                        targetState = track.title,
                                        transitionSpec = {
                                            (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 }) togetherWith
                                                (fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 4 })
                                        },
                                        label = "mini-player-title"
                                    ) { title ->
                                        Text(title, color = palette.onBackground, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                    AnimatedContent(
                                        targetState = track.artist,
                                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                                        label = "mini-player-artist"
                                    ) { artist ->
                                        Text(artist, color = Muted, fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                                IconButton(onClick = toggle) {
                                    AnimatedContent(
                                        targetState = playing,
                                        transitionSpec = {
                                            (fadeIn(tween(150)) + scaleIn(initialScale = .72f, animationSpec = tween(180))) togetherWith
                                                (fadeOut(tween(100)) + scaleOut(targetScale = .72f, animationSpec = tween(120)))
                                        },
                                        label = "mini-player-play-pause"
                                    ) { isPlaying ->
                                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = palette.onBackground)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

