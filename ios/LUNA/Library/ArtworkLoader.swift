import UIKit

/// Decodes embedded cover art once per size and keeps a bounded cache, so scrolling the library
/// doesn't re-decode full-size covers for 48pt thumbnails.
final class ArtworkLoader {
    static let shared = ArtworkLoader()

    private let cache = NSCache<NSString, UIImage>()

    init() {
        cache.countLimit = 150
    }

    /// `maxDimension` is in pixels. Returns nil when the file has no embedded artwork.
    func image(for track: Track, url: URL?, maxDimension: CGFloat) async -> UIImage? {
        let key = "\(track.id)@\(Int(maxDimension))" as NSString
        if let cached = cache.object(forKey: key) { return cached }

        guard let url,
              let data = await AudioMetadataReader.artworkData(url: url),
              let full = UIImage(data: data) else { return nil }

        let longestSide = max(full.size.width, full.size.height)
        guard longestSide > 0 else { return nil }
        let scale = min(1, maxDimension / longestSide)
        let target = CGSize(width: full.size.width * scale, height: full.size.height * scale)
        let image = await full.byPreparingThumbnail(ofSize: target) ?? full

        cache.setObject(image, forKey: key)
        return image
    }
}
