@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.luna.music

import android.Manifest
import android.database.ContentObserver
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import com.luna.music.data.MusicRepository
import com.luna.music.data.ComboRepository
import com.luna.music.data.MusicCombo
import com.luna.music.data.Track
import com.luna.music.playback.LunaPlaybackService
import com.luna.music.theme.*
import com.luna.music.ui.LunaGlassSurface
import com.luna.music.ui.LocalLunaLiquidBackdrop
import com.luna.music.ui.LocalLunaGlassLight
import com.luna.music.ui.rememberLunaGlassLight
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.luna.music.ui.AlbumArtwork
import com.luna.music.ui.NowPlayingScreen
import com.luna.music.ui.EqualizerScreen
import com.luna.music.ui.CombosScreen
import com.luna.music.ui.AboutScreen
import com.luna.music.ui.readableContent
import com.luna.music.ui.LocalLunaHazeState
import com.luna.music.ui.LunaBrandLogo
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private enum class LunaSection { Library, Equalizer, Combos, About }
private enum class LibraryView { Tracks, Artists, Albums }
private enum class LibrarySort { Title, Artist, Album }

class MainActivity : ComponentActivity() {
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private lateinit var repository: MusicRepository
    private lateinit var comboRepository: ComboRepository
    private lateinit var themeEngine: LunaThemeEngine
    private var tracks by mutableStateOf<List<Track>>(emptyList())
    private var selectedTrack by mutableStateOf<Track?>(null)
    private var isPlaying by mutableStateOf(false)
    private var permissionGranted by mutableStateOf(false)
    private var currentPalette by mutableStateOf(AlbumPalette.Fallback)
    private var paletteLoadToken = 0
    private var showNowPlaying by mutableStateOf(false)
    private var currentSection by mutableStateOf(LunaSection.Library)
    private var combos by mutableStateOf<List<MusicCombo>>(emptyList())
    private var libraryLoading by mutableStateOf(false)
    private var libraryLoadFailed by mutableStateOf(false)
    private var libraryLoadJob: Job? = null
    private var libraryLoadGeneration = 0
    private var audioObserver: ContentObserver? = null
    private val libraryRefreshHandler = Handler(Looper.getMainLooper())
    private val libraryRefreshRunnable = Runnable { if (permissionGranted) loadLibrary() }

    private fun loadLibrary() {
        if (!permissionGranted) return
        val generation = ++libraryLoadGeneration
        libraryLoadJob?.cancel()
        libraryLoading = true
        libraryLoadFailed = false
        libraryLoadJob = lifecycleScope.launch {
            try {
                val loadedTracks = repository.loadTracks()
                if (generation != libraryLoadGeneration) return@launch
                tracks = loadedTracks
                // The session may reconnect before the local library scan finishes.
                mediaController?.currentMediaItem?.let { item ->
                    tracks.firstOrNull { it.id.toString() == item.mediaId }?.let { track ->
                        selectedTrack = track
                        updatePaletteFor(track)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == libraryLoadGeneration) {
                    libraryLoadFailed = true
                    Log.e("Luna", "Unable to load the music library", error)
                }
            } finally {
                if (generation == libraryLoadGeneration) {
                    libraryLoading = false
                    libraryLoadJob = null
                }
            }
        }
    }

    private fun syncLibraryPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                         else Manifest.permission.READ_EXTERNAL_STORAGE
        val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (granted == permissionGranted) return
        permissionGranted = granted
        if (granted) {
            loadLibrary()
        } else {
            libraryLoadGeneration++
            libraryLoadJob?.cancel()
            libraryLoadJob = null
            libraryLoading = false
            libraryLoadFailed = false
            tracks = emptyList()
        }
    }

    private fun registerAudioObserver() {
        if (!permissionGranted || audioObserver != null) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                libraryRefreshHandler.removeCallbacks(libraryRefreshRunnable)
                libraryRefreshHandler.postDelayed(libraryRefreshRunnable, 450L)
            }
        }
        try {
            contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
            audioObserver = observer
        } catch (error: SecurityException) {
            Log.w("Luna", "Music library permission changed while registering updates", error)
            syncLibraryPermission()
        }
    }

    private fun unregisterAudioObserver() {
        audioObserver?.let(contentResolver::unregisterContentObserver)
        audioObserver = null
        libraryRefreshHandler.removeCallbacks(libraryRefreshRunnable)
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
        comboRepository = ComboRepository(this)
        combos = comboRepository.load()
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
                try {
                    val controller = future.get()
                    mediaController = controller
                    controller.addListener(object : Player.Listener {
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
                    controller.currentMediaItem?.let { item ->
                        tracks.firstOrNull { it.id.toString() == item.mediaId }?.let { track ->
                            selectedTrack = track
                            updatePaletteFor(track)
                        }
                    }
                } catch (error: Exception) {
                    Log.e("Luna", "Unable to connect to the playback service", error)
                }
            }, ContextCompat.getMainExecutor(this))
        }

        setContent {
            val animatedPalette = rememberLunaPalette(currentPalette)
            val hazeState = remember { HazeState() }
            val glassLight = rememberLunaGlassLight()
            LunaTheme(animatedPalette) {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val uiScope = rememberCoroutineScope()
            val openDrawer: () -> Unit = { uiScope.launch { drawerState.open() } }
            val closeDrawer: () -> Unit = { uiScope.launch { drawerState.close() } }
            BackHandler(enabled = showNowPlaying || currentSection != LunaSection.Library) {
                if (showNowPlaying) showNowPlaying = false else currentSection = LunaSection.Library
            }
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = permissionGranted,
                drawerContent = {
                    androidx.compose.runtime.CompositionLocalProvider(LocalLunaGlassLight provides glassLight) {
                    LunaNavigationDrawer(
                        palette = animatedPalette,
                        selected = currentSection,
                        onSelect = { destination ->
                            currentSection = destination
                            showNowPlaying = false
                            closeDrawer()
                        }
                    )
                    }
                }
            ) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalLunaHazeState provides hazeState,
                LocalLunaGlassLight provides glassLight
            ) {
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
                SharedTransitionLayout(
                    modifier = Modifier.fillMaxSize().background(animatedPalette.background)
                ) {
                AnimatedContent(
                    targetState = showNowPlaying && selectedTrack != null,
                    modifier = Modifier.fillMaxSize().background(animatedPalette.background),
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
                    val screenVisibilityScope = this@AnimatedContent
                    val nowPlayingTrack = selectedTrack
                    if (showPlayer && nowPlayingTrack != null) {
                    NowPlayingScreen(
                        track = nowPlayingTrack,
                        palette = animatedPalette,
                        playing = isPlaying,
                        controller = mediaController,
                        sharedScope = this@SharedTransitionLayout,
                        visibilityScope = screenVisibilityScope,
                        onBack = { showNowPlaying = false },
                        openDrawer = openDrawer,
                        onToggle = { if (mediaController?.isPlaying == true) mediaController?.pause() else mediaController?.play() },
                        onNext = { mediaController?.seekToNextMediaItem() },
                        onPrevious = { mediaController?.seekToPreviousMediaItem() }
                    )
                    } else {
                    AnimatedContent(
                        targetState = currentSection,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                        label = "library-sections"
                    ) { section -> when (section) {
                    LunaSection.Library -> LunaHome(
                        tracks = tracks,
                        libraryLoading = libraryLoading,
                        libraryLoadFailed = libraryLoadFailed,
                        reloadLibrary = ::loadLibrary,
                        selectedTrack = selectedTrack,
                        playing = isPlaying,
                        palette = animatedPalette,
                        sharedScope = this@SharedTransitionLayout,
                        visibilityScope = screenVisibilityScope,
                        openNowPlaying = { showNowPlaying = true },
                        openDrawer = openDrawer,
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
                    LunaSection.Equalizer -> EqualizerScreen(animatedPalette, openDrawer)
                    LunaSection.Combos -> CombosScreen(
                        palette = animatedPalette,
                        tracks = tracks,
                        combos = combos,
                        openDrawer = openDrawer,
                        onCreate = { name, ids -> combos = comboRepository.create(name, ids) },
                        onDelete = { id -> combos = comboRepository.delete(id) },
                        onPlay = { combo ->
                            val comboTracks = combo.trackIds.mapNotNull { id -> tracks.firstOrNull { it.id == id } }
                            if (comboTracks.isNotEmpty()) {
                                val mediaItems = comboTracks.map { item ->
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
                                    controller.setMediaItems(mediaItems, 0, 0L)
                                    controller.prepare()
                                    controller.play()
                                }
                                selectedTrack = comboTracks.first()
                                currentSection = LunaSection.Library
                                updatePaletteFor(comboTracks.first())
                                showNowPlaying = true
                            }
                        }
                    )
                    LunaSection.About -> AboutScreen(animatedPalette, openDrawer)
                    } }
                    }
                }
                }
                }
            }
        }
            }
            }
    }
    }

    override fun onResume() {
        super.onResume()
        syncLibraryPermission()
        registerAudioObserver()
    }

    override fun onPause() {
        unregisterAudioObserver()
        super.onPause()
    }

    override fun onDestroy() {
        unregisterAudioObserver()
        libraryLoadGeneration++
        libraryLoadJob?.cancel()
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
                listOf(palette.glow.copy(alpha = .55f), palette.background, palette.backgroundBottom)
            )
        ).statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            LunaBrandLogo(
                modifier = Modifier.size(148.dp).staggeredEntrance(0, 110),
                contentDescription = "Luna music player"
            )
            Spacer(Modifier.height(28.dp))
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
                Text("Continue", color = readableContent(palette.primary), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LunaHome(
    tracks: List<Track>,
    libraryLoading: Boolean,
    libraryLoadFailed: Boolean,
    reloadLibrary: () -> Unit,
    selectedTrack: Track?,
    playing: Boolean,
    palette: AlbumPalette,
    play: (Track) -> Unit,
    toggle: () -> Unit,
    next: () -> Unit,
    previous: () -> Unit,
    sharedScope: SharedTransitionScope,
    visibilityScope: AnimatedVisibilityScope,
    openNowPlaying: () -> Unit,
    openDrawer: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedViewName by rememberSaveable { mutableStateOf(LibraryView.Tracks.name) }
    var selectedSortName by rememberSaveable { mutableStateOf(LibrarySort.Title.name) }
    var showSortMenu by remember { mutableStateOf(false) }
    val libraryView = LibraryView.entries.firstOrNull { it.name == selectedViewName } ?: LibraryView.Tracks
    val librarySort = LibrarySort.entries.firstOrNull { it.name == selectedSortName } ?: LibrarySort.Title
    val visibleTracks = remember(tracks, searchQuery, librarySort) {
        val query = searchQuery.trim()
        tracks.asSequence()
            .filter { query.isEmpty() || listOf(it.title, it.artist, it.album).any { value -> value.contains(query, ignoreCase = true) } }
            .sortedWith(when (librarySort) {
                LibrarySort.Title -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
                LibrarySort.Artist -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                LibrarySort.Album -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.album }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            })
            .toList()
    }
    val groupedTracks = remember(visibleTracks, libraryView) {
        when (libraryView) {
            LibraryView.Tracks -> emptyList()
            LibraryView.Artists -> visibleTracks
                .groupBy { it.artist.ifBlank { "Unknown artist" } }
                .entries.sortedBy { it.key.lowercase() }
                .map { it.key to it.value }
            LibraryView.Albums -> visibleTracks
                .groupBy { "${it.album.ifBlank { "Unknown album" }}\u0000${it.artist.ifBlank { "Unknown artist" }}" }
                .entries.sortedBy { it.key.lowercase() }
                .map { it.key to it.value }
        }
    }
    val liquidBackdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalLunaLiquidBackdrop provides liquidBackdrop) {
    Box(
        Modifier.fillMaxSize()
            .hazeSource(state = LocalLunaHazeState.current, zIndex = -1f)
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(palette.glow.copy(alpha = .42f), palette.background, palette.backgroundBottom)))
                .layerBackdrop(liquidBackdrop)
        )
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                LunaBrandLogo(
                    modifier = Modifier.size(82.dp),
                    contentDescription = "Luna music player"
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = openDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "Open navigation", tint = palette.onBackground)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Library", color = palette.onBackground, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            if (tracks.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        libraryLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = palette.secondary)
                            Spacer(Modifier.height(14.dp))
                            Text("Scanning your music library…", color = palette.muted)
                        }
                        libraryLoadFailed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Couldn’t load your music library.", color = palette.muted)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = reloadLibrary) { Text("Try again") }
                        }
                        else -> Text("No music found. Add audio files to your device.", color = palette.muted)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Search songs, artists, albums") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.secondary,
                            unfocusedBorderColor = palette.onBackground.copy(alpha = .28f),
                            cursorColor = palette.secondary
                        )
                    )
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort by ${librarySort.name.lowercase()}", tint = palette.onBackground)
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            LibrarySort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text("Sort by ${sort.name.lowercase()}") },
                                    onClick = { selectedSortName = sort.name; showSortMenu = false },
                                    leadingIcon = { if (sort == librarySort) Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryView.entries.forEach { view ->
                        FilterChip(
                            selected = libraryView == view,
                            onClick = { selectedViewName = view.name },
                            label = { Text(view.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.primary.copy(alpha = .22f),
                                selectedLabelColor = palette.onBackground,
                                labelColor = palette.onBackground.copy(alpha = .78f)
                            )
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val libraryListState = rememberLazyListState()
                if (visibleTracks.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No matches. Try a different search.", color = palette.muted)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), state = libraryListState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (libraryView) {
                            LibraryView.Tracks -> items(visibleTracks, key = { "track:${it.id}" }) { track ->
                                LibraryTrackRow(track, palette, selectedTrack?.id == track.id, playing) { play(it) }
                            }
                            LibraryView.Artists -> groupedTracks.forEach { (artist, artistTracks) ->
                                    item(key = "artist:$artist") {
                                        LibraryGroupHeading("$artist · ${artistTracks.size}", palette)
                                    }
                                    items(artistTracks, key = { "artist:${artist}:${it.id}" }) { track ->
                                        LibraryTrackRow(track, palette, selectedTrack?.id == track.id, playing) { play(it) }
                                    }
                                }
                            LibraryView.Albums -> groupedTracks.forEach { (key, albumTracks) ->
                                val (album, artist) = key.split("\u0000", limit = 2)
                                item(key = "album:$key") {
                                    LibraryGroupHeading("$album · $artist · ${albumTracks.size}", palette)
                                }
                                items(albumTracks, key = { "album:${key}:${it.id}" }) { track ->
                                    LibraryTrackRow(track, palette, selectedTrack?.id == track.id, playing) { play(it) }
                                }
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
                        Column(
                            Modifier.clickable(onClick = openNowPlaying)
                                .semantics {
                                    contentDescription = "Now playing ${track.title} by ${track.artist}"
                                    role = Role.Button
                                }
                                .animateContentSize(tween(240)).padding(14.dp)
                        ) {
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
                                        Text(artist, color = palette.muted, fontSize = 12.sp, maxLines = 1)
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
                                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play", tint = palette.onBackground)
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
}

@Composable
private fun LibraryGroupHeading(title: String, palette: AlbumPalette) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 12.dp, bottom = 2.dp),
        color = palette.secondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun LibraryTrackRow(
    track: Track,
    palette: AlbumPalette,
    selected: Boolean,
    playing: Boolean,
    play: (Track) -> Unit
) {
    val rowColor by animateColorAsState(
        targetValue = if (selected) palette.primary.copy(alpha = .19f) else Color.White.copy(alpha = .045f),
        animationSpec = tween(320),
        label = "library-row-selection"
    )
    Row(
        Modifier.fillMaxWidth()
            .bouncyClickable(RoundedCornerShape(17.dp), rowColor) { play(track) }
            .semantics(mergeDescendants = true) {
                contentDescription = "${track.title} by ${track.artist}"
                role = Role.Button
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtwork(track.artworkUri?.toString(), Modifier.size(46.dp), palette)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = palette.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(track.artist, color = palette.muted, fontSize = 12.sp, maxLines = 1)
        }
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = .88f, animationSpec = tween(180)),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = .88f, animationSpec = tween(140))
        ) {
            EqualizerBars(palette.secondary, playing)
        }
    }
}

@Composable
private fun LunaNavigationDrawer(
    palette: AlbumPalette,
    selected: LunaSection,
    onSelect: (LunaSection) -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(304.dp)) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(palette.glow.copy(alpha = 0.55f), palette.background, palette.backgroundBottom)))
                .padding(horizontal = 18.dp, vertical = 26.dp)
        ) {
            LunaBrandLogo(
                modifier = Modifier.size(112.dp),
                contentDescription = "Luna music player"
            )
            Spacer(Modifier.height(6.dp))
            Text("Your music, your space.", color = palette.onBackground.copy(alpha = 0.78f), fontSize = 13.sp)
            Spacer(Modifier.height(28.dp))
            Text("YOUR SPACE", color = palette.onBackground.copy(alpha = 0.68f), fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
            LunaDrawerItem(palette, selected == LunaSection.Library, "Library", Icons.Default.LibraryMusic) { onSelect(LunaSection.Library) }
            LunaDrawerItem(palette, selected == LunaSection.Equalizer, "Equalizer", Icons.Default.GraphicEq) { onSelect(LunaSection.Equalizer) }
            LunaDrawerItem(palette, selected == LunaSection.Combos, "Combos", Icons.AutoMirrored.Filled.QueueMusic) { onSelect(LunaSection.Combos) }
            Spacer(Modifier.height(14.dp))
            LunaDrawerItem(palette, selected == LunaSection.About, "About", Icons.Default.Info) { onSelect(LunaSection.About) }
            Spacer(Modifier.weight(1f))
            Text("Built by Lunatic", color = palette.onBackground.copy(alpha = 0.68f), fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, top = 18.dp))
        }
    }
}

@Composable
private fun LunaDrawerItem(
    palette: AlbumPalette,
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val item: @Composable () -> Unit = {
        NavigationDrawerItem(
            selected = selected,
            onClick = onClick,
            icon = { Icon(icon, contentDescription = null) },
            label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = Color.Transparent,
                unselectedContainerColor = Color.Transparent,
                selectedIconColor = palette.onBackground,
                unselectedIconColor = palette.onBackground.copy(alpha = 0.78f),
                selectedTextColor = palette.onBackground,
                unselectedTextColor = palette.onBackground.copy(alpha = 0.88f)
            )
        )
    }
    if (selected) {
        // The active destination sits on a tinted glass pill that catches the tilt light.
        LunaGlassSurface(
            palette = palette,
            modifier = Modifier.fillMaxWidth().background(palette.primary.copy(alpha = 0.14f)),
            shape = RoundedCornerShape(28.dp),
            refraction = false
        ) { item() }
    } else {
        item()
    }
}

