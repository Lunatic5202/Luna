import SwiftUI

struct TrackRow: View {
    let track: Track
    let palette: LunaPalette
    let isCurrent: Bool
    let isPlaying: Bool

    var body: some View {
        HStack(spacing: 12) {
            ArtworkView(track: track, size: 48)

            VStack(alignment: .leading, spacing: 2) {
                Text(track.displayTitle)
                    .font(.system(size: 16, weight: isCurrent ? .bold : .semibold))
                    .foregroundStyle(isCurrent ? palette.primary.color : palette.onBackground.color)
                    .lineLimit(1)
                Text(track.displayArtist)
                    .font(.system(size: 12))
                    .foregroundStyle(palette.muted.color)
                    .lineLimit(1)
            }

            Spacer(minLength: 8)

            if isCurrent {
                Image(systemName: isPlaying ? "waveform" : "pause.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(palette.secondary.color)
            } else if track.duration > 0 {
                Text(TimeFormat.clock(track.duration))
                    .font(.system(size: 12))
                    .monospacedDigit()
                    .foregroundStyle(palette.muted.color)
            }
        }
        .padding(12)
        .background(
            isCurrent ? palette.primary.withAlpha(0.18).color : Color.white.opacity(0.045),
            in: RoundedRectangle(cornerRadius: 17, style: .continuous)
        )
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(track.displayTitle) by \(track.displayArtist)")
        .accessibilityAddTraits(.isButton)
    }
}
