package com.luna.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
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
import androidx.media3.common.Player
import kotlinx.coroutines.launch
import com.luna.music.data.MusicRepository
import com.luna.music.data.Track
import com.luna.music.playback.MusicPlayer
import com.luna.music.theme.*
import com.luna.music.ui.LunaGlassSurface

private val Muted = Color(0xFFAAA7BA)

class MainActivity : ComponentActivity() {
    private lateinit var musicPlayer: MusicPlayer
    private lateinit var repository: MusicRepository
    private lateinit var themeEngine: LunaThemeEngine
    private var tracks by mutableStateOf<List<Track>>(emptyList())
    private var selectedTrack by mutableStateOf<Track?>(null)
    private var isPlaying by mutableStateOf(false)
    private var permissionGranted by mutableStateOf(false)
    private var currentPalette by mutableStateOf(AlbumPalette.Fallback)
    private var paletteLoadToken = 0

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
            if (granted) tracks = repository.loadTracks()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        musicPlayer = MusicPlayer(this)
        repository = MusicRepository(this)
        themeEngine = LunaThemeEngine(this)

        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                         else Manifest.permission.READ_EXTERNAL_STORAGE
        permissionGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) tracks = repository.loadTracks()

        musicPlayer.exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val track = tracks.firstOrNull { it.id.toString() == mediaItem?.mediaId } ?: return
                selectedTrack = track
                updatePaletteFor(track)
            }
        })

        setContent {
            val animatedPalette = rememberLunaPalette(currentPalette)
            LunaTheme(animatedPalette) {
                LunaHome(
                    tracks = tracks,
                    selectedTrack = selectedTrack,
                    playing = isPlaying,
                    permissionGranted = permissionGranted,
                    palette = animatedPalette,
                    requestPermission = { permissionLauncher.launch(permission) },
                    play = { track ->
                        val index = tracks.indexOf(track).coerceAtLeast(0)
                        musicPlayer.setQueue(tracks, index)
                        selectedTrack = track
                        isPlaying = true
                        updatePaletteFor(track)
                    },
                    toggle = {
                        if (musicPlayer.exoPlayer.isPlaying) {
                            musicPlayer.exoPlayer.pause()
                            isPlaying = false
                        } else {
                            musicPlayer.exoPlayer.play()
                            isPlaying = true
                        }
                    },
                    next = {
                        musicPlayer.exoPlayer.seekToNextMediaItem()
                        isPlaying = true
                    },
                    previous = {
                        musicPlayer.exoPlayer.seekToPreviousMediaItem()
                        isPlaying = true
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        musicPlayer.release()
        super.onDestroy()
    }
}

@Composable
private fun LunaHome(
    tracks: List<Track>,
    selectedTrack: Track?,
    playing: Boolean,
    permissionGranted: Boolean,
    palette: AlbumPalette,
    requestPermission: () -> Unit,
    play: (Track) -> Unit,
    toggle: () -> Unit,
    next: () -> Unit,
    previous: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(palette.glow.copy(alpha = .42f), palette.background, Color(0xFF07080D)))
        )
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("L U N A", color = palette.onBackground, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                    Text("MUSIC, IN ANOTHER DIMENSION", color = palette.secondary, fontSize = 9.sp, letterSpacing = 2.sp)
                }
                Text("☾", color = palette.primary, fontSize = 32.sp)
            }
            Spacer(Modifier.height(30.dp))
            Text("YOUR LIBRARY", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text("${tracks.size} TRACKS", color = palette.onBackground, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            if (!permissionGranted) {
                LunaGlassSurface(palette, Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Let LUNA find your music", color = palette.onBackground, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Your audio stays on your device. Nothing is uploaded.", color = Muted)
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = requestPermission) { Text("Grant music access") }
                    }
                }
            } else if (tracks.isEmpty()) {
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
                            Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(palette.primary, palette.secondary))),
                                contentAlignment = Alignment.Center) {
                                Text("♫", color = Color.White, fontSize = 23.sp)
                            }
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
                    Column(Modifier.padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("NOW PLAYING", color = palette.secondary, fontSize = 9.sp, letterSpacing = 2.sp)
                                Text(track.title, color = palette.onBackground, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(track.artist, color = Muted, fontSize = 12.sp)
                            }
                            MiniSpectrum(palette, playing)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = previous) { Icon(Icons.Default.SkipPrevious, null, tint = palette.onBackground) }
                            IconButton(onClick = toggle, modifier = Modifier.size(54.dp).clip(CircleShape).background(palette.primary)) {
                                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
                            }
                            IconButton(onClick = next) { Icon(Icons.Default.SkipNext, null, tint = palette.onBackground) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MiniSpectrum(palette: AlbumPalette, playing: Boolean) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "spectrum")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(12) { index ->
            val height by transition.animateFloat(
                initialValue = 5f,
                targetValue = if (playing) 10f + (index % 5) * 5f else 4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400 + index * 45, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar-$index"
            )
            Box(Modifier.width(3.dp).height(height.dp).clip(CircleShape)
                .background(if (index % 3 == 0) palette.secondary else palette.primary))
        }
    }
}
