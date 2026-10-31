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
import kotlinx.coroutines.delay
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
    data class Available(val text: String, val provider: String = "LRCLIB") : LyricsState
    data object NotFound : LyricsState
    data class Failed(val message: String) : LyricsState
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
                    is LyricsState.Failed -> Column(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(lyricsState.message, color = palette.onBackground.copy(alpha = .78f), fontSize = 15.sp, textAlign = TextAlign.Center)
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
                    when (val lyricsState = state) {
                        is LyricsState.Available -> "Lyrics provided by ${lyricsState.provider}"
                        else -> "Lyrics searched on LRCLIB and lyrics.ovh"
                    },
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
    val cachedProvider = cache.getString("${cacheKey}_provider", "LRCLIB") ?: "LRCLIB"
    val cachedNotFound = cache.getBoolean("${cacheKey}_not_found", false)
    val cacheTtl = if (cachedNotFound) NEGATIVE_CACHE_TTL_MS else LYRICS_CACHE_TTL_MS
    if (System.currentTimeMillis() - cachedAt in 0L..cacheTtl) {
        if (cachedText != null) return@withContext LyricsState.Available(cachedText, cachedProvider)
        if (cachedNotFound) return@withContext LyricsState.NotFound
    }

    val result = fetchLyricsFromNetwork(track, durationSeconds)
    when (result) {
        is LyricsState.Available -> if (result.text.length <= MAX_CACHED_LYRICS_CHARS) {
            cache.edit {
                putString("${cacheKey}_text", result.text)
                putString("${cacheKey}_provider", result.provider)
                putBoolean("${cacheKey}_not_found", false)
                putLong("${cacheKey}_saved", System.currentTimeMillis())
            }
            trimLyricsCache(cache, cacheKey)
        }
        LyricsState.NotFound -> {
            cache.edit {
                remove("${cacheKey}_text")
                remove("${cacheKey}_provider")
                putBoolean("${cacheKey}_not_found", true)
                putLong("${cacheKey}_saved", System.currentTimeMillis())
            }
            trimLyricsCache(cache, cacheKey)
        }
        else -> Unit // Transient failures should be retried on the next attempt.
    }
    result
}

private fun trimLyricsCache(cache: android.content.SharedPreferences, newestKey: String) {
    val savedEntries = cache.all.entries
        .filter { it.key.endsWith("_saved") }
        .map { it.key.removeSuffix("_saved") to (it.value as? Long ?: 0L) }
    val overflow = savedEntries.size - MAX_CACHED_LYRICS
    if (overflow <= 0) return
    val oldest = savedEntries.asSequence()
        .filter { it.first != newestKey }
        .sortedBy { it.second }
        .take(overflow)
        .map { it.first }
        .toList()
    if (oldest.isNotEmpty()) cache.edit {
        oldest.forEach { key ->
            remove("${key}_text")
            remove("${key}_provider")
            remove("${key}_not_found")
            remove("${key}_saved")
        }
    }
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
    if (durationSeconds > 0f) {
        var connection: HttpURLConnection? = null
        try {
            val queryBuilder = Uri.Builder()
                .appendQueryParameter("track_name", track.title)
                .appendQueryParameter("artist_name", track.artist)
                .appendQueryParameter("duration", durationSeconds.toInt().toString())
            if (track.album.isNotBlank()) queryBuilder.appendQueryParameter("album_name", track.album)
            val query = queryBuilder.build().encodedQuery.orEmpty()
            val request = openLyricsRequest("https://lrclib.net/api/get?$query")
            connection = request
            when (request.responseCode) {
                in 200..299 -> {
                    val json = JSONObject(BufferedReader(InputStreamReader(request.inputStream, Charsets.UTF_8)).use(::readLimitedResponse))
                    lyricsFrom(json)?.let { return@withContext LyricsState.Available(it) }
                }
                // Exact lookup can fail transiently. Search remains a useful fallback.
                else -> Unit
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A failed exact lookup should not prevent the broader search request.
        } finally {
            connection?.disconnect()
        }
    }
    val lrclibResult = searchLyrics(track)
    if (lrclibResult is LyricsState.Available) return@withContext lrclibResult

    val alternateResult = fetchLyricsFromLyricsOvh(track)
    when {
        alternateResult is LyricsState.Available -> alternateResult
        lrclibResult is LyricsState.Failed && alternateResult is LyricsState.Failed ->
            LyricsState.Failed("Both lyrics services are temporarily unavailable. Try again shortly.")
        lrclibResult is LyricsState.Failed -> lrclibResult
        alternateResult is LyricsState.Failed -> alternateResult
        else -> LyricsState.NotFound
    }
}

private suspend fun fetchLyricsFromLyricsOvh(track: Track): LyricsState {
    if (track.title.isBlank() || track.artist.isBlank()) return LyricsState.NotFound
    var connection: HttpURLConnection? = null
    return try {
        val endpoint = Uri.parse("https://api.lyrics.ovh").buildUpon()
            .appendPath("v1")
            .appendPath(track.artist)
            .appendPath(track.title)
            .build()
        val request = openLyricsRequest(endpoint.toString())
        connection = request
        when (val status = request.responseCode) {
            in 200..299 -> {
                val json = JSONObject(BufferedReader(InputStreamReader(request.inputStream, Charsets.UTF_8)).use(::readLimitedResponse))
                json.optString("lyrics").trim().takeIf(String::isNotBlank)
                    ?.let { LyricsState.Available(it, "lyrics.ovh") }
                    ?: LyricsState.NotFound
            }
            HttpURLConnection.HTTP_NOT_FOUND -> LyricsState.NotFound
            429 -> LyricsState.Failed("The alternate lyrics service is rate-limited. Try again shortly.")
            in 500..599 -> LyricsState.Failed("The alternate lyrics service is temporarily unavailable.")
            else -> LyricsState.Failed("The alternate lyrics service rejected the request (HTTP $status).")
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        LyricsState.Failed("Couldn’t reach the alternate lyrics service.")
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
    repeat(2) { attempt ->
        var request: HttpURLConnection? = null
        try {
            val connection = openLyricsRequest("https://lrclib.net/api/search?$query")
            request = connection
            val status = connection.responseCode
            if (status in 200..299) {
                val results = JSONArray(BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use(::readLimitedResponse))
                val title = normalizeMetadata(track.title)
                val artist = normalizeMetadata(track.artist)
                val candidates = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
                    .filter { normalizeMetadata(it.optString("trackName")) == title && normalizeMetadata(it.optString("artistName")) == artist }
                val album = normalizeMetadata(track.album)
                val best = candidates.firstOrNull { album.isNotEmpty() && normalizeMetadata(it.optString("albumName")) == album }
                    ?: candidates.firstOrNull()
                return lyricsFrom(best ?: return LyricsState.NotFound)?.let(LyricsState::Available) ?: LyricsState.NotFound
            }
            if (status in 500..599 && attempt == 0) {
                delay(LYRICS_RETRY_DELAY_MS)
                return@repeat
            }
            return when (status) {
                429 -> LyricsState.Failed("Lyrics are temporarily rate-limited. Try again shortly.")
                in 500..599 -> LyricsState.Failed("The lyrics service is temporarily unavailable. Try again shortly.")
                else -> LyricsState.Failed("The lyrics service rejected the request (HTTP $status). Try again later.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (attempt == 0) {
                delay(LYRICS_RETRY_DELAY_MS)
            } else {
                return LyricsState.Failed("Couldn’t reach the lyrics service. Check your connection and try again.")
            }
        } finally {
            request?.disconnect()
        }
    }
    return LyricsState.Failed("The lyrics service is temporarily unavailable. Try again shortly.")
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
private const val MAX_CACHED_LYRICS = 100
private const val MAX_CACHED_LYRICS_CHARS = 100_000
private const val LYRICS_RETRY_DELAY_MS = 650L
