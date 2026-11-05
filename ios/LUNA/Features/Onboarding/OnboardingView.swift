import SwiftUI

/// What LUNA does with your music and data, in plain words. Shown on first launch and from the
/// info button in the library. Every statement here is something the app actually does.
struct OnboardingView: View {
    let palette: LunaPalette
    /// First launch gets "Get started"; reopening it from the library gets "Done".
    let isFirstRun: Bool
    let onDone: () -> Void

    private struct Point: Identifiable {
        let id: String
        let icon: String
        let title: String
        let text: String
    }

    private let points: [Point] = [
        Point(
            id: "files",
            icon: "folder",
            title: "Bring your own music",
            text: "Import songs from the Files app. LUNA copies them into its own storage so they play offline, and only the songs you pick are touched."
        ),
        Point(
            id: "device",
            icon: "iphone",
            title: "It stays on your iPhone",
            text: "Your library, combos, favorites and lyrics cache live on this device. There is no account, no sign-in and no analytics."
        ),
        Point(
            id: "lyrics",
            icon: "text.quote",
            title: "Lyrics are the one online feature",
            text: "When you open Lyrics, the song's title, artist, album and length are sent to LRCLIB to find them. Nothing is sent otherwise."
        ),
        Point(
            id: "mic",
            icon: "mic.slash",
            title: "No microphone",
            text: "LUNA never asks to listen or record. Playback runs in the background so music keeps going with the screen locked."
        )
    ]

    var body: some View {
        ZStack {
            LunaBackground(palette: palette)
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 26) {
                        VStack(spacing: 6) {
                            Text("LUNA")
                                .font(.system(size: 40, weight: .heavy, design: .rounded))
                                .tracking(10)
                                .foregroundStyle(palette.primary.color)
                            Text("Your music, your space.")
                                .font(.system(size: 15))
                                .foregroundStyle(palette.muted.color)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.top, 36)

                        ForEach(points) { point in
                            HStack(alignment: .top, spacing: 16) {
                                Image(systemName: point.icon)
                                    .font(.system(size: 22, weight: .regular))
                                    .foregroundStyle(palette.secondary.color)
                                    .frame(width: 32)
                                    .accessibilityHidden(true)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(point.title)
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundStyle(palette.onBackground.color)
                                    Text(point.text)
                                        .font(.system(size: 14))
                                        .foregroundStyle(palette.muted.color)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                            }
                            .accessibilityElement(children: .combine)
                        }
                    }
                    .padding(.horizontal, 28)
                    .padding(.bottom, 24)
                }

                Button(action: onDone) {
                    Text(isFirstRun ? "Get started" : "Done")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(palette.background.color)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(palette.primary.color, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .padding(.horizontal, 28)
                .padding(.bottom, 24)
            }
        }
    }
}
