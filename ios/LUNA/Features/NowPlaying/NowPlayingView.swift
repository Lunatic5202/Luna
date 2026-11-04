import SwiftUI

struct NowPlayingView: View {
    @EnvironmentObject private var player: PlaybackCoordinator
    @Environment(\.dismiss) private var dismiss
    let palette: LunaPalette

    var body: some View {
        ZStack {
            LunaBackground(palette: palette)

            if let track = player.currentTrack {
                VStack(spacing: 22) {
                    HStack {
                        Spacer()
                        SleepTimerButton(timer: player.sleepTimer, palette: palette)
                    }
                    .padding(.horizontal, 20)

                    Spacer(minLength: 8)

                    ArtworkView(track: track, size: 292, circular: true)
                        .shadow(color: palette.primary.withAlpha(0.35).color, radius: 36)

                    VStack(spacing: 4) {
                        Text(track.displayTitle)
                            .font(.system(size: 24, weight: .bold))
                            .foregroundStyle(palette.onBackground.color)
                            .lineLimit(1)
                        Text(track.displayArtist)
                            .font(.system(size: 15))
                            .foregroundStyle(palette.secondary.color)
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 28)

                    ProgressSlider(progress: player.progress, palette: palette) { player.seek(to: $0) }
                        .padding(.horizontal, 28)

                    transportControls
                        .padding(.horizontal, 24)

                    Spacer(minLength: 8)
                }
            }
        }
        .onChange(of: player.currentTrack == nil) { _, isEmpty in
            if isEmpty { dismiss() }
        }
    }

    private var transportControls: some View {
        HStack {
            Button { player.toggleShuffle() } label: {
                Image(systemName: "shuffle")
                    .foregroundStyle(player.shuffleEnabled ? palette.primary.color : palette.muted.color)
            }
            .accessibilityLabel("Shuffle")
            .accessibilityValue(player.shuffleEnabled ? "On" : "Off")

            Spacer()

            Button { player.previous() } label: {
                Image(systemName: "backward.fill").font(.system(size: 26))
            }
            .accessibilityLabel("Previous track")

            Spacer()

            Button { player.togglePlayPause() } label: {
                Image(systemName: player.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                    .font(.system(size: 68))
                    .foregroundStyle(palette.primary.color)
            }
            .accessibilityLabel(player.isPlaying ? "Pause" : "Play")

            Spacer()

            Button { player.next() } label: {
                Image(systemName: "forward.fill").font(.system(size: 26))
            }
            .accessibilityLabel("Next track")

            Spacer()

            Button { player.cycleRepeat() } label: {
                Image(systemName: player.repeatMode == .one ? "repeat.1" : "repeat")
                    .foregroundStyle(player.repeatMode == .off ? palette.muted.color : palette.primary.color)
            }
            .accessibilityLabel("Repeat")
            .accessibilityValue(player.repeatMode.rawValue.capitalized)
        }
        .font(.system(size: 20))
        .foregroundStyle(palette.onBackground.color)
    }
}

/// The only view that watches the playhead, so the rest of the player isn't redrawn with it.
private struct ProgressSlider: View {
    @ObservedObject var progress: PlaybackProgress
    let palette: LunaPalette
    let onSeek: (TimeInterval) -> Void

    @State private var scrubValue: Double?

    var body: some View {
        VStack(spacing: 6) {
            Slider(
                value: Binding(
                    get: { scrubValue ?? min(progress.position, upperBound) },
                    set: { scrubValue = $0 }
                ),
                in: 0...upperBound,
                onEditingChanged: { editing in
                    if !editing, let value = scrubValue {
                        onSeek(value)
                        scrubValue = nil
                    }
                }
            )
            .tint(palette.primary.color)
            .disabled(progress.duration <= 0)
            .accessibilityLabel("Playback position")

            HStack {
                Text(TimeFormat.clock(scrubValue ?? progress.position))
                Spacer()
                Text(TimeFormat.clock(progress.duration))
            }
            .font(.system(size: 11))
            .monospacedDigit()
            .foregroundStyle(palette.muted.color)
        }
    }

    private var upperBound: Double { max(progress.duration, 1) }
}
