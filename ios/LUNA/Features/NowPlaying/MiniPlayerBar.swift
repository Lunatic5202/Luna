import SwiftUI

struct MiniPlayerBar: View {
    @EnvironmentObject private var player: PlaybackCoordinator
    let palette: LunaPalette
    let onOpen: () -> Void

    var body: some View {
        if let track = player.currentTrack {
            HStack(spacing: 10) {
                Button(action: onOpen) {
                    HStack(spacing: 12) {
                        ArtworkView(track: track, size: 44, circular: true)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(track.displayTitle)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(palette.onBackground.color)
                                .lineLimit(1)
                            Text(track.displayArtist)
                                .font(.system(size: 12))
                                .foregroundStyle(palette.muted.color)
                                .lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Open player, \(track.displayTitle)")

                Button {
                    player.togglePlayPause()
                } label: {
                    Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 20))
                        .frame(width: 40, height: 40)
                }
                .accessibilityLabel(player.isPlaying ? "Pause" : "Play")

                Button {
                    player.next()
                } label: {
                    Image(systemName: "forward.fill")
                        .font(.system(size: 18))
                        .frame(width: 40, height: 40)
                }
                .accessibilityLabel("Next track")
            }
            .foregroundStyle(palette.onBackground.color)
            .padding(.vertical, 8)
            .padding(.leading, 10)
            .padding(.trailing, 6)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.14), lineWidth: 1)
            )
        }
    }
}
