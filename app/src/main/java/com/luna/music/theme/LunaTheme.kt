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
    val baseTypography = Typography()
    val lunaTypography = Typography(
        displayLarge = baseTypography.displayLarge.copy(fontFamily = LunaFontFamily),
        displayMedium = baseTypography.displayMedium.copy(fontFamily = LunaFontFamily),
        displaySmall = baseTypography.displaySmall.copy(fontFamily = LunaFontFamily),
        headlineLarge = baseTypography.headlineLarge.copy(fontFamily = LunaFontFamily),
        headlineMedium = baseTypography.headlineMedium.copy(fontFamily = LunaFontFamily),
        headlineSmall = baseTypography.headlineSmall.copy(fontFamily = LunaFontFamily),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = LunaFontFamily),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = LunaFontFamily),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = LunaFontFamily),
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = LunaFontFamily),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = LunaFontFamily),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = LunaFontFamily),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = LunaFontFamily),
        labelMedium = baseTypography.labelMedium.copy(fontFamily = LunaFontFamily),
        labelSmall = baseTypography.labelSmall.copy(fontFamily = LunaFontFamily)
    )
    MaterialTheme(colorScheme = scheme, typography = lunaTypography, content = content)
}
