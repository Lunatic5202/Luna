package com.luna.music.theme

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves one palette per artwork URI. Cache is in-memory for this starter;
 * persistent cache can be added when library indexing is implemented.
 */
class LunaThemeEngine(context: Context) {
    private val extractor = AlbumColorExtractor(context.applicationContext)
    private val cache = mutableMapOf<String, AlbumPalette>()

    suspend fun paletteFor(artwork: Uri?): AlbumPalette {
        val key = artwork?.toString() ?: "fallback"
        cache[key]?.let { return it }
        return extractor.extract(artwork).also { cache[key] = it }
    }
}

@Composable
fun rememberLunaPalette(target: AlbumPalette): AlbumPalette {
    val background by androidx.compose.animation.animateColorAsState(
        target.background, androidx.compose.animation.core.tween(850), label = "luna-bg"
    )
    val surface by androidx.compose.animation.animateColorAsState(
        target.surface, androidx.compose.animation.core.tween(850), label = "luna-surface"
    )
    val primary by androidx.compose.animation.animateColorAsState(
        target.primary, androidx.compose.animation.core.tween(850), label = "luna-primary"
    )
    val secondary by androidx.compose.animation.animateColorAsState(
        target.secondary, androidx.compose.animation.core.tween(850), label = "luna-secondary"
    )
    val glow by androidx.compose.animation.animateColorAsState(
        target.glow, androidx.compose.animation.core.tween(850), label = "luna-glow"
    )
    return AlbumPalette(background, surface, primary, secondary, glow, target.onBackground)
}
