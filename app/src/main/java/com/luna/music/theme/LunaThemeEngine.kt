package com.luna.music.theme

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Resolves and caches artwork palettes.
 *
 * The cache is bounded so a large library cannot grow the app's memory use
 * indefinitely. A mutex prevents duplicate concurrent decodes for the same
 * rapid sequence of track changes.
 */
class LunaThemeEngine(context: Context) {
    private val extractor = AlbumColorExtractor(context.applicationContext)
    private val cache = LruCache<String, AlbumPalette>(48)
    private val extractionMutex = Mutex()

    suspend fun paletteFor(artwork: Uri?): AlbumPalette {
        val key = artwork?.toString() ?: FALLBACK_KEY
        cache.get(key)?.let { return it }

        return extractionMutex.withLock {
            cache.get(key)?.let { return@withLock it }
            extractor.extract(artwork).also { cache.put(key, it) }
        }
    }

    private companion object {
        const val FALLBACK_KEY = "fallback"
    }
}

@Composable
fun rememberLunaPalette(target: AlbumPalette): AlbumPalette {
    val background by animateColorAsState(target.background, tween(850), label = "luna-bg")
    val surface by animateColorAsState(target.surface, tween(850), label = "luna-surface")
    val primary by animateColorAsState(target.primary, tween(850), label = "luna-primary")
    val secondary by animateColorAsState(target.secondary, tween(850), label = "luna-secondary")
    val glow by animateColorAsState(target.glow, tween(850), label = "luna-glow")
    val onBackground by animateColorAsState(target.onBackground, tween(850), label = "luna-on-bg")

    return AlbumPalette(background, surface, primary, secondary, glow, onBackground)
}
