package com.luna.music.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.music.theme.AlbumPalette
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * How much resolution a surface needs. Decoding every cover at full size for a 46dp
 * list row wastes time and fills the cache with multi-megabyte bitmaps, which makes
 * scrolling a big library constantly re-decode.
 */
enum class ArtworkQuality(val maxDimension: Int) {
    /** List rows. */
    Thumb(192),
    /** Blurred backdrops, mini player and the discs in the peek stack. */
    Medium(512),
    /** The main player disc. */
    Full(768)
}

private class CachedArtwork(val bitmap: Bitmap, val sufficient: Boolean)

/**
 * Shared in-memory artwork cache. Surfaces request the same URI at different sizes; a larger
 * copy can stand in for a smaller request, and a smaller one is shown (upscaled) while the
 * exact size decodes, so no surface flashes a placeholder.
 */
private object AlbumArtworkCache {
    private val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Bitmap?>>()

    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024L / 8L).coerceAtMost(64L * 1024L).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    private fun key(uri: String, quality: ArtworkQuality) = "$uri@${quality.maxDimension}"

    @Synchronized
    private fun get(key: String): Bitmap? = memory.get(key)?.takeUnless { it.isRecycled }

    @Synchronized
    private fun put(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) memory.put(key, bitmap)
    }

    /** Best cached copy for [quality]: same or larger size first, otherwise the largest smaller one. */
    fun cached(uri: String, quality: ArtworkQuality): CachedArtwork? {
        val sizes = ArtworkQuality.entries
        for (candidate in sizes) {
            if (candidate.maxDimension < quality.maxDimension) continue
            get(key(uri, candidate))?.let { return CachedArtwork(it, sufficient = true) }
        }
        for (candidate in sizes.asReversed()) {
            if (candidate.maxDimension >= quality.maxDimension) continue
            get(key(uri, candidate))?.let { return CachedArtwork(it, sufficient = false) }
        }
        return null
    }

    suspend fun load(context: Context, artworkUri: String, quality: ArtworkQuality): Bitmap? {
        val cacheKey = key(artworkUri, quality)
        get(cacheKey)?.let { return it }

        val promise = CompletableDeferred<Bitmap?>()
        val existing = inFlight.putIfAbsent(cacheKey, promise)
        val shared = existing ?: promise.also { owner ->
            loaderScope.launch {
                try {
                    val decoded = decode(context, artworkUri, quality.maxDimension)
                    decoded?.let { put(cacheKey, it) }
                    owner.complete(decoded)
                } catch (_: Exception) {
                    owner.complete(null)
                } finally {
                    inFlight.remove(cacheKey, owner)
                }
            }
        }

        // The shared decode belongs to the application loader, so cancellation of
        // one lazy row does not cancel the request needed by another surface.
        return shared.await()
    }

    private suspend fun decode(context: Context, artworkUri: String, maxDimension: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = artworkUri.toUri()
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    return@runCatching null
                }

                var sampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxDimension) {
                    sampleSize *= 2
                }

                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            }.getOrNull()
        }
}

@Composable
fun AlbumArtwork(
    artworkUri: String?,
    modifier: Modifier = Modifier,
    palette: AlbumPalette,
    shape: Shape = RoundedCornerShape(18.dp),
    quality: ArtworkQuality = ArtworkQuality.Full
) {
    val context = LocalContext.current
    var cached by remember(artworkUri, quality) {
        mutableStateOf(artworkUri?.takeIf(String::isNotBlank)?.let { AlbumArtworkCache.cached(it, quality) })
    }

    // Reuse decoded covers across every surface. Only decode when nothing big enough is cached.
    LaunchedEffect(artworkUri, quality, context) {
        if (!artworkUri.isNullOrBlank() && cached?.sufficient != true) {
            AlbumArtworkCache.load(context.applicationContext, artworkUri, quality)
                ?.let { cached = CachedArtwork(it, sufficient = true) }
        }
    }

    Box(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(palette.primary, palette.secondary))),
        contentAlignment = Alignment.Center
    ) {
        val image = cached?.bitmap
        if (image != null && !image.isRecycled) {
            Image(
                image.asImageBitmap(),
                contentDescription = "Album artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("♫", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
            }
        }
    }
}
