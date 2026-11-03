import SwiftUI

/// Cover art for a track, or a palette-coloured placeholder when the file has none.
struct ArtworkView: View {
    let track: Track?
    let size: CGFloat
    var circular = false

    @EnvironmentObject private var library: LibraryViewModel
    @Environment(\.displayScale) private var displayScale
    @State private var image: UIImage?

    private let palette = LunaPalette.fallback

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [palette.glow.color, palette.surface.color],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Image(systemName: "music.note")
                    .font(.system(size: size * 0.38, weight: .light))
                    .foregroundStyle(palette.primary.color.opacity(0.85))
            }
        }
        .frame(width: size, height: size)
        .clipShape(shape)
        .accessibilityHidden(true)
        .task(id: track?.id) {
            guard let track else {
                image = nil
                return
            }
            image = await ArtworkLoader.shared.image(
                for: track,
                url: library.url(for: track),
                maxDimension: size * displayScale
            )
        }
    }

    private var shape: AnyShape {
        circular ? AnyShape(Circle()) : AnyShape(RoundedRectangle(cornerRadius: size * 0.2, style: .continuous))
    }
}
