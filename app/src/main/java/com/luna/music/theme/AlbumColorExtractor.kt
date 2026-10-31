package com.luna.music.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a compact adaptive palette from explicit or embedded track artwork.
 * Images are decoded at thumbnail resolution to keep memory use predictable.
 */
class AlbumColorExtractor(private val context: Context) {
    suspend fun extract(artworkUri: Uri?, trackUri: Uri?): AlbumPalette =
        withContext(Dispatchers.IO) {
            val bitmap = artworkUri?.let { runCatching { decodeArtwork(it) }.getOrNull() }
                ?: trackUri?.let { runCatching { decodeEmbeddedArtwork(it) }.getOrNull() }
                ?: return@withContext AlbumPalette.Fallback

            try {
                val palette = Palette.from(bitmap).maximumColorCount(24).generate()
                val dominant = palette.dominantSwatch?.rgb ?: AndroidColor.rgb(90, 96, 105)
                val vibrant = palette.vibrantSwatch?.rgb
                    ?: palette.lightVibrantSwatch?.rgb
                    ?: palette.mutedSwatch?.rgb
                    ?: dominant
                val muted = palette.mutedSwatch?.rgb
                    ?: palette.darkMutedSwatch?.rgb
                    ?: dominant
                val dark = palette.darkMutedSwatch?.rgb
                    ?: palette.darkVibrantSwatch?.rgb
                    ?: darken(dominant, 0.48f)
                val light = palette.lightVibrantSwatch?.rgb
                    ?: palette.lightMutedSwatch?.rgb
                    ?: lighten(vibrant, 0.34f)

                AlbumPalette(
                    background = Color(darken(dark, 0.48f)),
                    surface = Color(darken(dark, 0.72f)),
                    primary = Color(vibrant),
                    secondary = Color(light),
                    glow = Color(muted),
                    onBackground = Color(0xFFF5F6F8)
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

    private fun decodeEmbeddedArtwork(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        val bytes = try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } finally {
            retriever.release()
        } ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 256)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun calculateInSampleSize(width: Int, height: Int, target: Int): Int {
        var sampleSize = 1
        while (maxOf(width, height) / sampleSize > target) sampleSize *= 2
        return sampleSize
    }

    private fun darken(color: Int, amount: Float): Int =
        AndroidColor.rgb(
            (AndroidColor.red(color) * amount).toInt().coerceIn(0, 255),
            (AndroidColor.green(color) * amount).toInt().coerceIn(0, 255),
            (AndroidColor.blue(color) * amount).toInt().coerceIn(0, 255)
        )

    private fun lighten(color: Int, amount: Float): Int =
        AndroidColor.rgb(
            (AndroidColor.red(color) + (255 - AndroidColor.red(color)) * amount).toInt(),
            (AndroidColor.green(color) + (255 - AndroidColor.green(color)) * amount).toInt(),
            (AndroidColor.blue(color) + (255 - AndroidColor.blue(color)) * amount).toInt()
        )
}
