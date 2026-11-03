import SwiftUI

/// The palette every screen draws with: LUNA's violet until a track with artwork plays, then
/// the colours of that cover, cross-faded. Palettes are cached per track and the cache is bounded.
@MainActor
final class PaletteStore: ObservableObject {
    @Published private(set) var palette = LunaPalette.fallback

    private var cache: [String: LunaPalette] = [:]
    private let cacheLimit = 100

    func update(for track: Track?, url: URL?) async {
        guard let track else {
            apply(.fallback)
            return
        }
        if let cached = cache[track.id] {
            apply(cached)
            return
        }
        guard let image = await ArtworkLoader.shared.image(for: track, url: url, maxDimension: 48) else {
            apply(.fallback)
            return
        }
        // A newer track may have taken over while the cover was decoding.
        if Task.isCancelled { return }

        let result = PaletteExtractor.palette(from: PaletteExtractor.pixels(from: image))
        if cache.count >= cacheLimit { cache.removeAll() }
        cache[track.id] = result
        apply(result)
    }

    private func apply(_ next: LunaPalette) {
        guard next != palette else { return }
        withAnimation(.easeInOut(duration: 1.1)) { palette = next }
    }
}
