import XCTest
import UIKit
@testable import LUNA

final class PaletteExtractorTests: XCTestCase {
    private func solid(_ color: LunaColor, count: Int = 100) -> [LunaColor] {
        Array(repeating: color, count: count)
    }

    private func hueDistance(_ a: Double, _ b: Double) -> Double {
        let d = abs(a - b)
        return min(d, 1 - d)
    }

    func testHsvRoundTrip() {
        let color = LunaColor(hue: 0.6, saturation: 0.5, value: 0.8)
        let hsv = color.hsv
        XCTAssertEqual(hsv.hue, 0.6, accuracy: 0.0001)
        XCTAssertEqual(hsv.saturation, 0.5, accuracy: 0.0001)
        XCTAssertEqual(hsv.value, 0.8, accuracy: 0.0001)
    }

    func testRedArtworkGivesRedPalette() {
        let palette = PaletteExtractor.palette(from: solid(LunaColor(red: 0.9, green: 0.1, blue: 0.1)))
        XCTAssertLessThan(hueDistance(palette.primary.hsv.hue, 0), 0.03)
        XCTAssertEqual(palette.background.hsv.value, 0.09, accuracy: 0.01)
    }

    func testMostCommonColourWins() {
        let blue = LunaColor(red: 0.1, green: 0.2, blue: 0.9)
        let red = LunaColor(red: 0.9, green: 0.1, blue: 0.1)
        let palette = PaletteExtractor.palette(from: solid(blue, count: 80) + solid(red, count: 10))
        XCTAssertLessThan(hueDistance(palette.primary.hsv.hue, blue.hsv.hue), 0.05)
    }

    func testGreyscaleArtworkKeepsFallback() {
        let grey = LunaColor(red: 0.5, green: 0.5, blue: 0.5)
        XCTAssertEqual(PaletteExtractor.palette(from: solid(grey)), .fallback)
    }

    func testNearBlackAndNearWhiteKeepFallback() {
        let pixels = solid(LunaColor(red: 0.02, green: 0.01, blue: 0.01)) + solid(LunaColor(red: 1, green: 0.98, blue: 0.98))
        XCTAssertEqual(PaletteExtractor.palette(from: pixels), .fallback)
    }

    func testNoPixelsKeepsFallback() {
        XCTAssertEqual(PaletteExtractor.palette(from: []), .fallback)
    }

    func testTextStaysReadableOnTheBackground() {
        for hue in stride(from: 0.0, to: 1.0, by: 0.1) {
            let source = LunaColor(hue: hue, saturation: 0.8, value: 0.9)
            let palette = PaletteExtractor.palette(from: solid(source))
            XCTAssertGreaterThanOrEqual(palette.primary.contrastRatio(with: palette.background), 4.5, "hue \(hue)")
            XCTAssertGreaterThanOrEqual(palette.onBackground.contrastRatio(with: palette.background), 4.5, "hue \(hue)")
        }
    }

    func testPixelsFromImageAreSampledToGrid() {
        let size = CGSize(width: 80, height: 40)
        let image = UIGraphicsImageRenderer(size: size).image { context in
            UIColor(red: 0.1, green: 0.8, blue: 0.2, alpha: 1).setFill()
            context.fill(CGRect(origin: .zero, size: size))
        }
        let pixels = PaletteExtractor.pixels(from: image, side: 8)
        XCTAssertEqual(pixels.count, 64)
        let palette = PaletteExtractor.palette(from: pixels)
        XCTAssertLessThan(hueDistance(palette.primary.hsv.hue, LunaColor(red: 0.1, green: 0.8, blue: 0.2).hsv.hue), 0.08)
    }
}
