package com.luna.music.ui

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.core.content.edit
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.music.data.Track
import com.luna.music.theme.AlbumPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.io.InputStreamReader
import java.net.URL
import java.security.MessageDigest

private sealed interface LyricsState {
    data object Loading : LyricsState
    data class Available(val text: String) : LyricsState
    data object NotFound : LyricsState
    data object Failed : LyricsState
}

@Composable
fun LyricsOverlay(
    visible: Boolean,
    track: Track,
    palette: AlbumPalette,
    durationSeconds: Float,
    onClose: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    var state by remember(track.id) { mutableStateOf<LyricsState>(LyricsState.Loading) }
    LaunchedEffect(visible, track.id, durationSeconds, state) {
        if (visible && state is LyricsState.Loading) {
            state = fetchLyrics(context, track, durationSeconds)
        }
    }
    BackHandler(enabled = visible, onBack = onClose)

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(260)) + slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 8 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { it / 10 }
    ) {
        val rotation by rememberInfiniteTransition(label = "lyrics-artwork").animateFloat(
            initialValue = -8f,
            targetValue = 8f,
            animationSpec = infiniteRepeatable(
                animation = tween(18_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "lyrics-artwork-rotation"
        )
        Box(Modifier.fillMaxSize().background(palette.background)) {
            AlbumArtwork(
                artworkUri = track.artworkUri?.toString(),
                palette = palette,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).align(Alignment.Center)
                    .graphicsLayer { rotationZ = rotation; scaleX = 1.12f; scaleY = 1.12f }
                    .blur(48.dp).alpha(.26f)
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(palette.background.copy(alpha = .68f), palette.background.copy(alpha = .92f))
                    )
                )
            )
            Column(
                Modifier.fillMaxSize()
                    .statusBarsPadding().navigationBarsPadding()
                    .padding(top = 12.dp, start = 26.dp, end = 26.dp, bottom = 30.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close lyrics", tint = palette.onBackground)
                    }
                    Text(
                        "LYRICS", modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                        color = palette.secondary, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.size(48.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    track.title, color = palette.onBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(track.artist, color = palette.secondary, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(28.dp))
                when (val lyricsState = state) {
                    LyricsState.Loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = palette.secondary)
                    }
                    LyricsState.NotFound -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        EmptyLyrics(palette, "Lyrics aren’t available for this track.")
                    }
                    LyricsState.Failed -> Column(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Couldn’t load lyrics. Check your connection and try again.", color = palette.onBackground.copy(alpha = .78f), fontSize = 15.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { state = LyricsState.Loading }) { Text("Try again") }
                    }
                    is LyricsState.Available -> Text(
                        lyricsState.text,
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                        color = palette.onBackground, fontSize = 19.sp, lineHeight = 31.sp, textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Lyrics provided by LRCLIB",
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.onBackground.copy(alpha = .48f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EmptyLyrics(palette: AlbumPalette, message: String) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.MusicNote, contentDescription = null, tint = palette.secondary.copy(alpha = .75f))
        Spacer(Modifier.height(10.dp))
        Text(message, color = palette.onBackground.copy(alpha = .72f), fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

private suspend fun fetchLyrics(context: Context, track: Track, durationSeconds: Float): LyricsState = withContext(Dispatchers.IO) {
    val cache = context.getSharedPreferences("lyrics_cache", Context.MODE_PRIVATE)
    val cacheKey = lyricsCacheKey(track)
    val cachedAt = cache.getLong("${cacheKey}_saved", 0L)
    val cachedText = cache.getString("${cacheKey}_text", null)
    val cachedNotFound = cache.getBoolean("${cacheKey}_not_found", false)
    val cacheTtl = if (cachedNotFound) NEGATIVE_CACHE_TTL_MS else LYRICS_CACHE_TTL_MS
    if (System.currentTimeMillis() - cachedAt in 0L..cacheTtl) {
        if (cachedText != null) return@withContext LyricsState.Available(cachedText)
        if (cachedNotFound) return@withContext LyricsState.NotFound
    }

    val result = fetchLyricsFromNetwork(track, durationSeconds)
    when (result) {
        is LyricsState.Available -> cache.edit {
            putString("${cacheKey}_text", result.text)
            putBoolean("${cacheKey}_not_found", false)
            putLong("${cacheKey}_saved", System.currentTimeMillis())
        }
        LyricsState.NotFound -> cache.edit {
            remove("${cacheKey}_text")
            putBoolean("${cacheKey}_not_found", true)
            putLong("${cacheKey}_saved", System.currentTimeMillis())
        }
        else -> Unit // Transient failures should be retried on the next attempt.
    }
    result
}

private fun lyricsCacheKey(track: Track): String {
    val identity = listOf(
        normalizeMetadata(track.title),
        normalizeMetadata(track.artist),
        normalizeMetadata(track.album)
    ).joinToString("\u0000")
    return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

private suspend fun fetchLyricsFromNetwork(track: Track, durationSeconds: Float): LyricsState = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        if (durationSeconds > 0f) {
            val queryBuilder = Uri.Builder()
                .appendQueryParameter("track_name", track.title)
                .appendQueryParameter("artist_name", track.artist)
                .appendQueryParameter("duration", durationSeconds.toString())
            if (track.album.isNotBlank()) queryBuilder.appendQueryParameter("album_name", track.album)
            val query = queryBuilder.build().encodedQuery.orEmpty()
            val request = openLyricsRequest("https://lrclib.net/api/get?$query")
            connection = request
            when (request.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> Unit
                in 200..299 -> {
                    val json = JSONObject(BufferedReader(InputStreamReader(request.inputStream, Charsets.UTF_8)).use(::readLimitedResponse))
                    lyricsFrom(json)?.let { return@withContext LyricsState.Available(it) }
                    Unit
                }
                else -> return@withContext LyricsState.Failed
            }
            request.disconnect()
            connection = null
        }
        searchLyrics(track)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        LyricsState.Failed
    } finally {
        connection?.disconnect()
    }
}

private fun openLyricsRequest(url: String): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 7_000
        readTimeout = 7_000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "LunaMusic/1.0 (https://github.com/Lunatic5202/Luna)")
    }

private suspend fun searchLyrics(track: Track): LyricsState {
    val query = Uri.Builder()
        .appendQueryParameter("q", "${track.title} ${track.artist}")
        .build().encodedQuery.orEmpty()
    val request = openLyricsRequest("https://lrclib.net/api/search?$query")
    try {
        if (request.responseCode !in 200..299) return LyricsState.Failed
        val results = JSONArray(BufferedReader(InputStreamReader(request.inputStream, Charsets.UTF_8)).use(::readLimitedResponse))
        val title = normalizeMetadata(track.title)
        val artist = normalizeMetadata(track.artist)
        val candidates = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
            .filter { normalizeMetadata(it.optString("trackName")) == title && normalizeMetadata(it.optString("artistName")) == artist }
        val album = normalizeMetadata(track.album)
        val best = candidates.firstOrNull { album.isNotEmpty() && normalizeMetadata(it.optString("albumName")) == album }
            ?: candidates.firstOrNull()
        return lyricsFrom(best ?: return LyricsState.NotFound)?.let(LyricsState::Available) ?: LyricsState.NotFound
    } finally {
        request.disconnect()
    }
}

private fun lyricsFrom(json: JSONObject?): String? {
    if (json == null) return null
    val rawLyrics = json.optString("syncedLyrics").takeIf(String::isNotBlank)
        ?: json.optString("plainLyrics").takeIf(String::isNotBlank)
    return rawLyrics?.let(::removeTimestamps)?.takeIf(String::isNotBlank)
}

private fun normalizeMetadata(value: String): String = value
    .trim()
    .lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()

private fun removeTimestamps(lyrics: String): String = lyrics
    .lineSequence()
    .map { LRC_TAG.replace(LRC_TIMESTAMP.replace(it, ""), "").trim() }
    .filter(String::isNotBlank)
    .joinToString("\n")

private val LRC_TIMESTAMP = Regex("\\[\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?]")
private val LRC_TAG = Regex("\\[(?:ar|al|ti|by|offset|re|ve):[^]]*]", RegexOption.IGNORE_CASE)

private fun readLimitedResponse(reader: BufferedReader): String {
    val result = StringBuilder()
    val buffer = CharArray(4_096)
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) return result.toString()
        require(result.length + count <= MAX_RESPONSE_CHARS) { "Lyrics response exceeded size limit" }
        result.append(buffer, 0, count)
    }
}

private const val MAX_RESPONSE_CHARS = 1_000_000
private const val LYRICS_CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
private const val NEGATIVE_CACHE_TTL_MS = 12L * 60 * 60 * 1000
