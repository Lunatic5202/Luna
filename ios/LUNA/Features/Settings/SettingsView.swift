import SwiftUI

/// What LUNA keeps on the phone, with a way to clear the parts that are only a cache or a history,
/// plus the version and the privacy screen. Music, favorites and combos are never cleared here.
struct SettingsView: View {
    @EnvironmentObject private var library: LibraryViewModel
    @EnvironmentObject private var favorites: FavoritesStore
    @EnvironmentObject private var combos: ComboStore
    @EnvironmentObject private var playStats: PlayStatsStore

    let palette: LunaPalette
    let onDone: () -> Void

    @State private var lyricsCache = LyricsCache.makeDefault()
    @State private var savedLyrics = LyricsCache.makeDefault().entries.count
    @State private var confirmClearLyrics = false
    @State private var confirmClearHistory = false
    @State private var showPrivacy = false

    var body: some View {
        ZStack {
            LunaBackground(palette: palette)
            VStack(spacing: 0) {
                header
                List {
                    Section("On this iPhone") {
                        row("Songs", "\(library.tracks.count)")
                        row("Favorites", "\(favorites.ids.count)")
                        row("Combos", "\(combos.combos.count)")
                    }
                    .listRowBackground(rowBackground)

                    Section {
                        row("Saved lyrics", "\(savedLyrics)")
                        Button("Clear saved lyrics", role: .destructive) { confirmClearLyrics = true }
                            .disabled(savedLyrics == 0)
                        row("Songs with play history", "\(playStats.stats.count)")
                        Button("Clear play history", role: .destructive) { confirmClearHistory = true }
                            .disabled(playStats.stats.isEmpty)
                    } header: {
                        Text("Caches and history")
                    } footer: {
                        Text("Clearing these never touches your songs, favorites or combos. Lyrics are fetched again the next time you open them.")
                    }
                    .listRowBackground(rowBackground)

                    Section("About") {
                        row("Version", AppInfo.currentVersion)
                        Button("Welcome and privacy") { showPrivacy = true }
                        Link("Source code on GitHub", destination: URL(string: "https://github.com/Lunatic5202/Luna")!)
                    }
                    .listRowBackground(rowBackground)
                }
                .scrollContentBackground(.hidden)
                .foregroundStyle(palette.onBackground.color)
                .tint(palette.primary.color)
            }
        }
        .confirmationDialog("Clear saved lyrics?", isPresented: $confirmClearLyrics, titleVisibility: .visible) {
            Button("Clear lyrics", role: .destructive) {
                lyricsCache.clear()
                savedLyrics = lyricsCache.entries.count
            }
        }
        .confirmationDialog("Clear play history?", isPresented: $confirmClearHistory, titleVisibility: .visible) {
            Button("Clear history", role: .destructive) { playStats.clearAll() }
        } message: {
            Text("Recently played and Most played start over.")
        }
        .sheet(isPresented: $showPrivacy) {
            OnboardingView(palette: palette, isFirstRun: false) { showPrivacy = false }
                .presentationDragIndicator(.visible)
        }
    }

    private var header: some View {
        HStack {
            Text("Settings")
                .font(.system(size: 22, weight: .heavy))
                .foregroundStyle(palette.onBackground.color)
            Spacer()
            Button("Done", action: onDone)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(palette.primary.color)
        }
        .padding(.horizontal, 22)
        .padding(.top, 22)
        .padding(.bottom, 8)
    }

    private var rowBackground: some View {
        Color.white.opacity(0.06)
    }

    private func row(_ title: String, _ value: String) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(value)
                .foregroundStyle(palette.muted.color)
                .monospacedDigit()
        }
        .accessibilityElement(children: .combine)
    }
}
