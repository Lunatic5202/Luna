import SwiftUI

/// Lyrics for the song that is playing. Synced lyrics follow the song and highlight the current
/// line (tap a line to jump there); plain lyrics just scroll.
struct LyricsView: View {
    @EnvironmentObject private var player: PlaybackCoordinator
    let palette: LunaPalette

    @StateObject private var store = LyricsStore.makeDefault()

    var body: some View {
        ZStack {
            LunaBackground(palette: palette)
            VStack(spacing: 0) {
                header
                content
                footer
            }
        }
        .task(id: player.currentTrack?.id) {
            await store.load(for: player.currentTrack)
        }
    }

    private var header: some View {
        VStack(spacing: 2) {
            Text(player.currentTrack?.displayTitle ?? "Lyrics")
                .font(.system(size: 18, weight: .heavy))
                .foregroundStyle(palette.onBackground.color)
                .lineLimit(1)
            if let artist = player.currentTrack?.displayArtist {
                Text(artist)
                    .font(.system(size: 13))
                    .foregroundStyle(palette.secondary.color)
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 28)
        .padding(.top, 24)
        .padding(.bottom, 10)
    }

    @ViewBuilder
    private var content: some View {
        switch store.state {
        case .loading:
            centered { ProgressView().tint(palette.primary.color) }
        case .notFound:
            message("No lyrics found for this song.")
        case .failed(let text):
            VStack(spacing: 12) {
                message(text)
                Button("Try again") {
                    Task { await store.load(for: player.currentTrack) }
                }
                .buttonStyle(.borderedProminent)
                .tint(palette.primary.color)
                .foregroundStyle(palette.background.color)
            }
            .frame(maxHeight: .infinity)
        case .available(let text, let synced):
            let lines = synced ? LyricsParser.parse(text) : []
            if lines.isEmpty {
                PlainLyrics(text: synced ? LyricsParser.plainText(from: text) : text, palette: palette)
            } else {
                SyncedLyrics(lines: lines, palette: palette, progress: player.progress) { player.seek(to: $0) }
            }
        }
    }

    private var footer: some View {
        Text("Lyrics from LRCLIB. Only the title, artist, album and length are sent, when you open this screen.")
            .font(.system(size: 10))
            .multilineTextAlignment(.center)
            .foregroundStyle(palette.muted.color)
            .padding(.horizontal, 28)
            .padding(.vertical, 10)
    }

    private func centered<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        content().frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func message(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 15))
            .multilineTextAlignment(.center)
            .foregroundStyle(palette.muted.color)
            .padding(.horizontal, 40)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct PlainLyrics: View {
    let text: String
    let palette: LunaPalette

    var body: some View {
        ScrollView {
            Text(text)
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(palette.onBackground.color)
                .lineSpacing(6)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                .padding(.vertical, 20)
        }
    }
}

/// The only lyrics view that watches the playhead.
private struct SyncedLyrics: View {
    let lines: [LyricLine]
    let palette: LunaPalette
    @ObservedObject var progress: PlaybackProgress
    let onSeek: (TimeInterval) -> Void

    var body: some View {
        let current = LyricsParser.currentLineIndex(in: lines, at: progress.position)
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 18) {
                    ForEach(Array(lines.enumerated()), id: \.offset) { item in
                        Text(item.element.text)
                            .font(.system(size: 22, weight: .bold))
                            .foregroundStyle(item.offset == current ? palette.primary.color : palette.muted.color.opacity(0.55))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                            .onTapGesture { onSeek(item.element.time) }
                            .id(item.offset)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 60)
            }
            .onChange(of: current) { _, newValue in
                guard let newValue else { return }
                withAnimation(.easeInOut(duration: 0.4)) { proxy.scrollTo(newValue, anchor: .center) }
            }
        }
    }
}
