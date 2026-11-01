package com.luna.music.theme

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Resolves and caches artwork palettes per track.
 *
 * Tracks without a separate artwork URI still get their own cache entry,
 * allowing embedded artwork to be extracted from the audio file.
 */
class LunaThemeEngine(context: Context) {
    private val extractor = AlbumColorExtractor(context.applicationContext)
    private val cache = LruCache<String, AlbumPalette>(48)
    private val extractionMutex = Mutex()

    suspend fun paletteFor(artwork: Uri?, track: Uri?): AlbumPalette {
        val key = when {
            artwork != null -> "art:${artwork}"
            track != null -> "track:${track}"
            else -> FALLBACK_KEY
        }
        cache.get(key)?.let { return it }

        return extractionMutex.withLock {
            cache.get(key)?.let { return@withLock it }
            extractor.extract(artwork, track).also { cache.put(key, it) }
        }
    }

    private companion object {
        const val FALLBACK_KEY = "fallback"
    }
}

@Composable
fun rememberLunaPalette(target: AlbumPalette): AlbumPalette {
    // A long eased cross-fade makes a track change feel like the room's mood shifting.
    val spec = tween<Color>(1100, easing = FastOutSlowInEasing)
    val background by animateColorAsState(target.background, spec, label = "luna-bg")
    val surface by animateColorAsState(target.surface, spec, label = "luna-surface")
    val primary by animateColorAsState(target.primary, spec, label = "luna-primary")
    val secondary by animateColorAsState(target.secondary, spec, label = "luna-secondary")
    val glow by animateColorAsState(target.glow, spec, label = "luna-glow")
    val onBackground by animateColorAsState(target.onBackground, spec, label = "luna-on-bg")
    val accent by animateColorAsState(target.accent, spec, label = "luna-accent")
    val energy by animateFloatAsState(target.energy, tween(1200), label = "luna-energy")

    return AlbumPalette(
        background = background,
        surface = surface,
        primary = primary,
        secondary = secondary,
        glow = glow,
        onBackground = onBackground,
        accent = accent,
        energy = energy,
        vibeName = target.vibeName,
        vibeLevel = target.vibeLevel
    )
}
