import SwiftUI

/// A colour kept as plain components so palettes can be compared, blended and
/// unit-tested without going through `Color`.
struct LunaColor: Equatable {
    var red: Double
    var green: Double
    var blue: Double
    var alpha: Double = 1

    /// 0xAARRGGBB, the same literal format the Android palette uses.
    init(argb: UInt32) {
        alpha = Double((argb >> 24) & 0xFF) / 255
        red = Double((argb >> 16) & 0xFF) / 255
        green = Double((argb >> 8) & 0xFF) / 255
        blue = Double(argb & 0xFF) / 255
    }

    init(red: Double, green: Double, blue: Double, alpha: Double = 1) {
        self.red = red
        self.green = green
        self.blue = blue
        self.alpha = alpha
    }

    var color: Color {
        Color(.sRGB, red: red, green: green, blue: blue, opacity: alpha)
    }

    func withAlpha(_ value: Double) -> LunaColor {
        LunaColor(red: red, green: green, blue: blue, alpha: value)
    }

    /// WCAG relative luminance, 0 (black) to 1 (white).
    var relativeLuminance: Double {
        func linear(_ channel: Double) -> Double {
            channel <= 0.03928 ? channel / 12.92 : pow((channel + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
    }

    /// WCAG contrast ratio between two colours, 1 (none) to 21 (black on white).
    func contrastRatio(with other: LunaColor) -> Double {
        let a = relativeLuminance
        let b = other.relativeLuminance
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }
}

/// LUNA's colour tokens. Mirrors `AlbumPalette` on Android so both apps share one identity;
/// artwork-derived palettes plug into the same shape later.
struct LunaPalette: Equatable {
    var background: LunaColor
    var surface: LunaColor
    var primary: LunaColor
    var secondary: LunaColor
    var glow: LunaColor
    var onBackground: LunaColor = LunaColor(argb: 0xFFF5F6F8)

    /// Secondary text colour used across every screen.
    var muted: LunaColor { onBackground.withAlpha(0.72) }

    /// Darker gradient endpoint that keeps backgrounds in the palette's family.
    var backgroundBottom: LunaColor {
        LunaColor(
            red: background.red * 0.76,
            green: background.green * 0.76,
            blue: background.blue * 0.76,
            alpha: background.alpha
        )
    }

    /// LUNA's violet palette, shown while artwork colours load or are unavailable.
    static let fallback = LunaPalette(
        background: LunaColor(argb: 0xFF0E0B14),
        surface: LunaColor(argb: 0xFF1B1524),
        primary: LunaColor(argb: 0xFFD2B5FF),
        secondary: LunaColor(argb: 0xFFFFB5D2),
        glow: LunaColor(argb: 0xFF68478C)
    )
}
