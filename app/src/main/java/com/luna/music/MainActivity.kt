package com.luna.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
            val palette = themeEngine.paletteFor(track?.artworkUri)
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
            androidx.compose.runtime.CompositionLocalProvider(LocalLunaHazeState provides hazeState) {
            LunaTheme(animatedPalette) {
                if (!permissionGranted) {
                    LunaPermissionIntro(
                        palette = animatedPalette,
                        requestPermission = { permissionLauncher.launch(permission) }
                    )
                } else {
                AnimatedContent(
                    targetState = showNowPlaying && selectedTrack != null,
                    transitionSpec = {
                        (fadeIn(tween(300)) + slideInVertically { it / 14 }) togetherWith
                            (fadeOut(tween(180)) + slideOutVertically { -it / 28 })
                    },
                    label = "screen-transition"
                ) { showPlayer ->
                    if (showPlayer && selectedTrack != null) {
                    NowPlayingScreen(
                        track = selectedTrack!!,
                        palette = animatedPalette,
                        playing = isPlaying,
                        controller = mediaController,
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
                fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            Spacer(Modifier.height(42.dp))
            Text("Your music. Your space.", color = palette.onBackground,
                fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(28.dp))
            Text("NEEDED", color = palette.secondary, fontSize = 10.sp,
                letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("Music library", color = palette.onBackground,
                fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text("Find and play music stored on this device.",
                color = palette.onBackground.copy(alpha = .58f), fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            Text("OPTIONAL", color = palette.secondary, fontSize = 10.sp,
                letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("Playback visualizer", color = palette.onBackground,
                fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text("Android may show a microphone permission. It is used only to read this player's audio session; LUNA does not record your voice.",
                color = palette.onBackground.copy(alpha = .58f), fontSize = 13.sp)
            Spacer(Modifier.height(34.dp))
            Button(
                onClick = requestPermission,
                modifier = Modifier.fillMaxWidth().height(54.dp),
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
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(tracks, key = { it.id }) { track ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                                .background(if (selectedTrack?.id == track.id) palette.primary.copy(alpha = .17f) else Color.White.copy(alpha = .045f))
                                .clickable { play(track) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AlbumArtwork(track.artworkUri?.toString(), Modifier.size(46.dp), palette)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, color = palette.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1)
                            }
                            if (selectedTrack?.id == track.id) Text("PLAYING", color = palette.secondary, fontSize = 9.sp)
                        }
                    }
                }
            }

            selectedTrack?.let { track ->
                Spacer(Modifier.height(12.dp))
                LunaGlassSurface(palette, Modifier.fillMaxWidth()) {
                    Column(Modifier.clickable(onClick = openNowPlaying).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AlbumArtwork(track.artworkUri?.toString(), Modifier.size(48.dp), palette)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, color = palette.onBackground, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(track.artist, color = Muted, fontSize = 12.sp)
                            }
                            IconButton(onClick = toggle) {
                                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = palette.onBackground)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
