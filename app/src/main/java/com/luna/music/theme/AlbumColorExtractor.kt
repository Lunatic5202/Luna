package com.luna.music.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a compact palette from local album artwork.
 *
 * Artwork is decoded at thumbnail resolution so unusually large embedded
 * covers cannot cause avoidable memory spikes during theme changes.
 */
class AlbumColorExtractor(private val context: Context) {
    suspend fun extract(uri: Uri?): AlbumPalette = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext AlbumPalette.Fallback

        val bitmap = try {
            decodeArtwork(uri)
        } catch (_: Exception) {
            null
        } ?: return@withContext AlbumPalette.Fallback

        try {
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
                background = Color(darken(dark, 0.42f)),
                surface = Color(darken(dark, 0.62f)),
                primary = Color(vibrant),
                secondary = Color(light),
                glow = Color(muted)
            )
        } catch (_: Exception) {
            AlbumPalette.Fallback
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeArtwork(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 256)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, target: Int): Int {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= target && height / (sampleSize * 2) >= target) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun darken(color: Int, amount: Float): Int {
        val r = (AndroidColor.red(color) * amount).toInt().coerceIn(0, 255)
        val g = (AndroidColor.green(color) * amount).toInt().coerceIn(0, 255)
        val b = (AndroidColor.blue(color) * amount).toInt().coerceIn(0, 255)
        return AndroidColor.rgb(r, g, b)
    }
}
