package com.luna.music.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.luna.music.R

private val googleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val LunaFont = GoogleFont("Manrope")
private val LunaFontFamily = FontFamily(
    Font(googleFont = LunaFont, fontProvider = googleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = LunaFont, fontProvider = googleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = LunaFont, fontProvider = googleFontsProvider, weight = FontWeight.SemiBold),
    Font(googleFont = LunaFont, fontProvider = googleFontsProvider, weight = FontWeight.Bold),
    Font(googleFont = LunaFont, fontProvider = googleFontsProvider, weight = FontWeight.ExtraBold)
)

@Composable
fun LunaTheme(palette: AlbumPalette, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = palette.primary,
        secondary = palette.secondary,
        background = palette.background,
        surface = palette.surface,
        onBackground = palette.onBackground,
        onSurface = palette.onBackground
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(defaultFontFamily = LunaFontFamily),
        content = content
    )
}
