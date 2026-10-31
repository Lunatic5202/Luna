package com.luna.music.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.palette.graphics.Palette
import androidx.core.graphics.drawable.toBitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlbumColorExtractor(private val context: Context) {
    suspend fun extract(uri: Uri?): AlbumPalette = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext AlbumPalette.Fallback
        try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext AlbumPalette.Fallback

            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            val vibrant = palette.vibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: AndroidColor.rgb(120, 90, 180)
            val muted = palette.mutedSwatch?.rgb
                ?: palette.darkMutedSwatch?.rgb
                ?: vibrant
            val dark = palette.darkVibrantSwatch?.rgb
                ?: palette.darkMutedSwatch?.rgb
                ?: darken(vibrant, 0.78f)
            val light = palette.lightVibrantSwatch?.rgb
                ?: palette.lightMutedSwatch?.rgb
                ?: AndroidColor.rgb(210, 195, 235)

            AlbumPalette(
                background = Color(darken(dark, 0.55f)),
                surface = Color(darken(dark, 0.34f)),
                primary = Color(vibrant),
                secondary = Color(light),
                glow = Color(muted)
            )
        } catch (_: Exception) {
            AlbumPalette.Fallback
        }
    }

    private fun darken(color: Int, amount: Float): Int {
        val r = (AndroidColor.red(color) * amount).toInt().coerceIn(0, 255)
        val g = (AndroidColor.green(color) * amount).toInt().coerceIn(0, 255)
        val b = (AndroidColor.blue(color) * amount).toInt().coerceIn(0, 255)
        return AndroidColor.rgb(r, g, b)
    }
}
