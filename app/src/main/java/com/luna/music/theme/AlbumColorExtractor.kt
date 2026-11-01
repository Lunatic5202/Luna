package com.luna.music.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
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
                val vividPrimary = boostVibrancy(vibrant, saturation = 1.14f, brightness = 1.04f)
                val vividSecondary = boostVibrancy(light, saturation = 1.10f, brightness = 1.02f)
                val vividGlow = boostVibrancy(muted, saturation = 1.20f, brightness = 1.06f)

                val hsl = FloatArray(3).also { ColorUtils.colorToHSL(vividPrimary, it) }
                val accent = if (hsl[1] < 0.12f) vividSecondary else ColorUtils.HSLToColor(
                    floatArrayOf((hsl[0] + 36f) % 360f, hsl[1].coerceIn(0.5f, 0.9f), 0.62f)
                )
                val energy = (
                    hsl[1] * 0.65f +
                        (1f - kotlin.math.abs(hsl[2] - 0.55f) * 1.6f).coerceIn(0f, 1f) * 0.35f
                    ).coerceIn(0f, 1f)

                AlbumPalette(
                    background = Color(darken(dark, 0.48f)),
                    surface = Color(darken(dark, 0.72f)),
                    primary = Color(vividPrimary),
                    secondary = Color(vividSecondary),
                    glow = Color(vividGlow),
                    onBackground = Color(0xFFF5F6F8),
                    accent = Color(accent),
                    energy = energy,
                    vibeName = vibeName(hsl[0], hsl[1]),
                    vibeLevel = when {
                        energy >= 0.66f -> "ENERGETIC"
                        energy >= 0.40f -> "GROOVY"
                        else -> "CALM"
                    }
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

    /** Names the mood by hue band; near-grey artwork is MONO. */
    private fun vibeName(hue: Float, saturation: Float): String = when {
        saturation < 0.14f -> "MONO"
        hue < 15f || hue >= 345f -> "EMBER"
        hue < 45f -> "SUNSET"
        hue < 70f -> "SOLAR"
        hue < 160f -> "VERDANT"
        hue < 200f -> "LAGOON"
        hue < 255f -> "OCEAN"
        hue < 295f -> "TWILIGHT"
        else -> "NEON"
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

    private fun boostVibrancy(color: Int, saturation: Float, brightness: Float): Int {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * saturation).coerceIn(0f, 0.94f)
        hsv[2] = (hsv[2] * brightness).coerceAtMost(0.96f)
        return AndroidColor.HSVToColor(hsv)
    }
}
