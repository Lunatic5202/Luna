import SwiftUI
import UniformTypeIdentifiers

/// Library screen with the mini player docked at the bottom and the full player as a sheet.
struct RootView: View {
    @EnvironmentObject private var library: LibraryViewModel
    @EnvironmentObject private var player: PlaybackCoordinator

    @State private var showImporter = false
    @State private var showNowPlaying = false

    private let palette = LunaPalette.fallback

    var body: some View {
        ZStack(alignment: .bottom) {
            LunaBackground(palette: palette)

            VStack(spacing: 0) {
                header
                if library.tracks.isEmpty {
                    emptyState
                } else {
                    trackList
                }
            }

            MiniPlayerBar(palette: palette) { showNowPlaying = true }
                .padding(.horizontal, 14)
                .padding(.bottom, 8)

            if library.isImporting {
                ProgressView("Importing…")
                    .padding(24)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .frame(maxHeight: .infinity)
            }
        }
        .fileImporter(
            isPresented: $showImporter,
            allowedContentTypes: [.audio],
            allowsMultipleSelection: true
        ) { result in
            Task { await library.importFiles(result) }
        }
        .sheet(isPresented: $showNowPlaying) {
            NowPlayingView(palette: palette)
                .environmentObject(player)
                .environmentObject(library)
                .presentationDragIndicator(.visible)
        }
        .alert(
            "Couldn't import",
            isPresented: Binding(
                get: { library.alertMessage != nil },
                set: { if !$0 { library.alertMessage = nil } }
            ),
            actions: { Button("OK", role: .cancel) {} },
            message: { Text(library.alertMessage ?? "") }
        )
        .alert(
            "Can't play",
            isPresented: Binding(
                get: { player.errorMessage != nil },
                set: { if !$0 { player.clearError() } }
            ),
            actions: { Button("OK", role: .cancel) {} },
            message: { Text(player.errorMessage ?? "") }
        )
    }

    private var header: some View {
        HStack {
            Text("LUNA")
                .font(.system(size: 28, weight: .heavy, design: .rounded))
                .tracking(6)
                .foregroundStyle(palette.primary.color)
            Spacer()
            Button {
                showImporter = true
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(palette.onBackground.color)
                    .frame(width: 44, height: 44)
                    .background(Color.white.opacity(0.08), in: Circle())
            }
            .accessibilityLabel("Import music from Files")
        }
        .padding(.horizontal, 22)
        .padding(.top, 8)
        .padding(.bottom, 12)
    }

    private var trackList: some View {
        // Keep the last rows clear of the mini player.
        LibraryBrowser(
            tracks: library.tracks,
            palette: palette,
            bottomInset: player.currentTrack == nil ? 16 : 88
        )
    }

    private var emptyState: some View {
        VStack(spacing: 14) {
            Spacer()
            Image(systemName: "music.note.list")
                .font(.system(size: 54, weight: .light))
                .foregroundStyle(palette.secondary.color)
            Text("No music yet")
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(palette.onBackground.color)
            Text("Import songs from the Files app and they'll live here, ready to play offline.")
                .font(.system(size: 14))
                .multilineTextAlignment(.center)
                .foregroundStyle(palette.muted.color)
                .padding(.horizontal, 40)
            Button("Import from Files") { showImporter = true }
                .buttonStyle(.borderedProminent)
                .tint(palette.primary.color)
                .foregroundStyle(palette.background.color)
                .padding(.top, 6)
            Spacer()
            Spacer()
        }
    }
}
