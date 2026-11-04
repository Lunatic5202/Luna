import UIKit

extension LunaColor {
    /// `hue` is 0...1 (a full turn), `saturation` and `value` are 0...1.
    init(hue: Double, saturation: Double, value: Double) {
        let h = (hue - hue.rounded(.down)) * 6
        let sector = Int(h) % 6
        let f = h - Double(Int(h))
        let p = value * (1 - saturation)
        let q = value * (1 - saturation * f)
        let t = value * (1 - saturation * (1 - f))
        switch sector {
        case 0: self.init(red: value, green: t, blue: p)
        case 1: self.init(red: q, green: value, blue: p)
        case 2: self.init(red: p, green: value, blue: t)
        case 3: self.init(red: p, green: q, blue: value)
        case 4: self.init(red: t, green: p, blue: value)
        default: self.init(red: value, green: p, blue: q)
        }
    }

    /// Hue 0...1, saturation 0...1, value 0...1.
    var hsv: (hue: Double, saturation: Double, value: Double) {
        let high = max(red, green, blue)
        let low = min(red, green, blue)
        let delta = high - low
        guard delta > 0 else { return (0, 0, high) }
        var hue: Double
        if high == red {
            hue = (green - blue) / delta
        } else if high == green {
            hue = 2 + (blue - red) / delta
        } else {
            hue = 4 + (red - green) / delta
        }
        hue /= 6
        if hue < 0 { hue += 1 }
        return (hue, delta / high, high)
    }
}

/// Turns cover art into a `LunaPalette`: the dominant colourful hue sets a dark background,
/// a soft glow and two light accents. Greyscale or near-black/white art keeps LUNA's own
/// violet fallback rather than producing a muddy palette.
enum PaletteExtractor {
    static let hueBuckets = 12
    /// Pixels paler, greyer or darker than this carry no usable colour.
    static let minSaturation = 0.2
    static let minValue = 0.15

    static func palette(from pixels: [LunaColor]) -> LunaPalette {
        var weight = [Double](repeating: 0, count: hueBuckets)
        var hueSum = [Double](repeating: 0, count: hueBuckets)
        var satSum = [Double](repeating: 0, count: hueBuckets)

        for pixel in pixels {
            let (hue, saturation, value) = pixel.hsv
            guard saturation >= minSaturation, value >= minValue else { continue }
            let bucket = min(hueBuckets - 1, Int(hue * Double(hueBuckets)))
            // Vivid, bright pixels count for more than dull ones.
            let w = saturation * value
            weight[bucket] += w
            hueSum[bucket] += hue * w
            satSum[bucket] += saturation * w
        }

        guard let best = weight.indices.max(by: { weight[$0] < weight[$1] }), weight[best] > 0 else {
            return .fallback
        }

        let hue = hueSum[best] / weight[best]
        let saturation = satSum[best] / weight[best]
        let accentHue = hue + 0.08

        return LunaPalette(
            background: LunaColor(hue: hue, saturation: clamp(saturation * 0.6, 0.25, 0.55), value: 0.09),
            surface: LunaColor(hue: hue, saturation: clamp(saturation * 0.5, 0.2, 0.45), value: 0.16),
            primary: LunaColor(hue: hue, saturation: 0.35, value: 1),
            secondary: LunaColor(hue: accentHue, saturation: 0.30, value: 1),
            glow: LunaColor(hue: hue, saturation: clamp(saturation, 0.35, 0.8), value: 0.55)
        )
    }

    /// Samples an image down to `side` x `side` pixels so extraction costs the same for any cover size.
    static func pixels(from image: UIImage, side: Int = 24) -> [LunaColor] {
        guard let cgImage = image.cgImage, side > 0 else { return [] }
        var bytes = [UInt8](repeating: 0, count: side * side * 4)
        let drew: Bool = bytes.withUnsafeMutableBytes { buffer in
            guard let context = CGContext(
                data: buffer.baseAddress,
                width: side,
                height: side,
                bitsPerComponent: 8,
                bytesPerRow: side * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            context.interpolationQuality = .medium
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: side, height: side))
            return true
        }
        guard drew else { return [] }
        return stride(from: 0, to: bytes.count, by: 4).map { index in
            LunaColor(
                red: Double(bytes[index]) / 255,
                green: Double(bytes[index + 1]) / 255,
                blue: Double(bytes[index + 2]) / 255
            )
        }
    }

    private static func clamp(_ value: Double, _ low: Double, _ high: Double) -> Double {
        min(max(value, low), high)
    }
}
