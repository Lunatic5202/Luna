import XCTest
@testable import LUNA

final class LunaPaletteTests: XCTestCase {
    func testArgbLiteralMatchesAndroidFallbackPrimary() {
        // 0xFFD2B5FF is the Android fallback primary: R=0xD2, G=0xB5, B=0xFF.
        let color = LunaColor(argb: 0xFFD2B5FF)
        XCTAssertEqual(color.red, 210.0 / 255, accuracy: 0.0001)
        XCTAssertEqual(color.green, 181.0 / 255, accuracy: 0.0001)
        XCTAssertEqual(color.blue, 1, accuracy: 0.0001)
        XCTAssertEqual(color.alpha, 1, accuracy: 0.0001)
    }

    func testAlphaIsReadFromTheHighByte() {
        let color = LunaColor(argb: 0x80FF0000)
        XCTAssertEqual(color.alpha, 128.0 / 255, accuracy: 0.0001)
        XCTAssertEqual(color.red, 1, accuracy: 0.0001)
    }

    func testBackgroundBottomIsDarkerWithSameAlpha() {
        let palette = LunaPalette.fallback
        let bottom = palette.backgroundBottom
        XCTAssertEqual(bottom.red, palette.background.red * 0.76, accuracy: 0.0001)
        XCTAssertEqual(bottom.green, palette.background.green * 0.76, accuracy: 0.0001)
        XCTAssertEqual(bottom.blue, palette.background.blue * 0.76, accuracy: 0.0001)
        XCTAssertEqual(bottom.alpha, palette.background.alpha, accuracy: 0.0001)
    }

    func testMutedTextKeepsColourButDropsAlpha() {
        let palette = LunaPalette.fallback
        XCTAssertEqual(palette.muted.alpha, 0.72, accuracy: 0.0001)
        XCTAssertEqual(palette.muted.red, palette.onBackground.red, accuracy: 0.0001)
    }

    func testContrastRatioExtremes() {
        let black = LunaColor(argb: 0xFF000000)
        let white = LunaColor(argb: 0xFFFFFFFF)
        XCTAssertEqual(black.contrastRatio(with: white), 21, accuracy: 0.01)
        XCTAssertEqual(white.contrastRatio(with: white), 1, accuracy: 0.0001)
    }

    func testFallbackTextIsReadableOnBackground() {
        let palette = LunaPalette.fallback
        // WCAG AAA for body text is 7:1.
        XCTAssertGreaterThanOrEqual(palette.onBackground.contrastRatio(with: palette.background), 7)
        XCTAssertGreaterThanOrEqual(palette.primary.contrastRatio(with: palette.background), 7)
    }
}
