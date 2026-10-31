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
 * Shared in-memory artwork cache. Library rows, the mini-player and the
 * fullscreen player all request the same URI, so a cover decoded while
 * scrolling is immediately reusable by the shared-element transition.
 */
private object AlbumArtworkCache {
    private const val MAX_DECODED_DIMENSION = 768
    private val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Bitmap?>>()

    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024L / 8L).coerceAtMost(64L * 1024L).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    @Synchronized
    fun get(key: String): Bitmap? = memory.get(key)?.takeUnless { it.isRecycled }

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) memory.put(key, bitmap)
    }

    suspend fun load(context: Context, artworkUri: String): Bitmap? {
        get(artworkUri)?.let { return it }

        val promise = CompletableDeferred<Bitmap?>()
        val existing = inFlight.putIfAbsent(artworkUri, promise)
        val shared = existing ?: promise.also { owner ->
            loaderScope.launch {
                try {
                    val decoded = decode(context, artworkUri)
                    decoded?.let { put(artworkUri, it) }
                    owner.complete(decoded)
                } catch (_: Exception) {
                    owner.complete(null)
                } finally {
                    inFlight.remove(artworkUri, owner)
                }
            }
        }

        // The shared decode belongs to the application loader, so cancellation of
        // one lazy row does not cancel the request needed by another surface.
        return shared.await()
    }

    private suspend fun decode(context: Context, artworkUri: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val uri = artworkUri.toUri()
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@runCatching null
            }

            var sampleSize = 1
            while (
                maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_DECODED_DIMENSION
            ) {
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
    shape: Shape = RoundedCornerShape(18.dp)
) {
    val context = LocalContext.current
    var bitmap by remember(artworkUri) {
        mutableStateOf(artworkUri?.takeIf(String::isNotBlank)?.let(AlbumArtworkCache::get))
    }

    // Reuse decoded covers across every surface. In particular, artwork
    // already shown in a library row/mini-player is ready for fullscreen.
    LaunchedEffect(artworkUri, context) {
        if (!artworkUri.isNullOrBlank()) {
            bitmap = AlbumArtworkCache.load(context.applicationContext, artworkUri)
        }
    }

    Box(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(palette.primary, palette.secondary))),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
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

