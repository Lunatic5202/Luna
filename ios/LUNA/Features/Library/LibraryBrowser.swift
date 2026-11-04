import SwiftUI

/// Search, view switcher (Songs / Albums / Artists), sort menu and the track list itself.
struct LibraryBrowser: View {
    let tracks: [Track]
    let palette: LunaPalette
    /// Space kept clear at the bottom so the mini player never covers the last row.
    let bottomInset: CGFloat

    @EnvironmentObject private var player: PlaybackCoordinator
    @EnvironmentObject private var library: LibraryViewModel

    @AppStorage("library.grouping") private var groupingRaw = LibraryGrouping.songs.rawValue
    @AppStorage("library.sort") private var sortRaw = LibrarySort.title.rawValue
    @State private var search = ""

    private var grouping: LibraryGrouping { LibraryGrouping(rawValue: groupingRaw) ?? .songs }
    private var sort: LibrarySort { LibrarySort(rawValue: sortRaw) ?? .title }

    var body: some View {
        let visible = LibraryQuery.sort(LibraryQuery.filter(tracks, search: search), by: sort)
        VStack(spacing: 10) {
            searchField
            HStack(spacing: 10) {
                Picker("View", selection: $groupingRaw) {
                    ForEach(LibraryGrouping.allCases) { Text($0.label).tag($0.rawValue) }
                }
                .pickerStyle(.segmented)
                sortMenu
            }
            .padding(.horizontal, 14)

            if visible.isEmpty {
                noMatches
            } else {
                list(for: visible)
            }
        }
    }

    // MARK: - Controls

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(palette.muted.color)
            TextField("Search songs, artists, albums", text: $search)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .foregroundStyle(palette.onBackground.color)
            if !search.isEmpty {
                Button {
                    search = ""
                } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(palette.muted.color)
                }
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.horizontal, 12)
        .frame(height: 40)
        .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .padding(.horizontal, 14)
    }

    private var sortMenu: some View {
        Menu {
            Picker("Sort by", selection: $sortRaw) {
                ForEach(LibrarySort.allCases) { Text($0.label).tag($0.rawValue) }
            }
        } label: {
            Image(systemName: "arrow.up.arrow.down")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(palette.onBackground.color)
                .frame(width: 40, height: 32)
                .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .accessibilityLabel("Sort")
        .accessibilityValue(sort.label)
    }

    // MARK: - Lists

    @ViewBuilder
    private func list(for visible: [Track]) -> some View {
        ScrollView {
            LazyVStack(spacing: 8, pinnedViews: [.sectionHeaders]) {
                if grouping == .songs {
                    ForEach(visible) { track in row(track, queue: visible) }
                } else {
                    ForEach(LibraryQuery.groups(from: visible, by: grouping)) { group in
                        Section {
                            ForEach(group.tracks) { track in row(track, queue: group.tracks) }
                        } header: {
                            groupHeader(group)
                        }
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.bottom, bottomInset)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private func row(_ track: Track, queue: [Track]) -> some View {
        TrackRow(
            track: track,
            palette: palette,
            isCurrent: player.currentTrack?.id == track.id,
            isPlaying: player.isPlaying
        )
        .onTapGesture {
            player.play(tracks: queue, startingAt: track)
        }
        .contextMenu {
            Button(role: .destructive) {
                library.remove(track)
                player.trackWasRemoved(track.id)
            } label: {
                Label("Remove from LUNA", systemImage: "trash")
            }
        }
    }

    private func groupHeader(_ group: TrackGroup) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(group.title)
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(palette.onBackground.color)
                .lineLimit(1)
            Text(group.subtitle)
                .font(.system(size: 12))
                .foregroundStyle(palette.muted.color)
                .lineLimit(1)
            Spacer()
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 6)
        .background(palette.background.color.opacity(0.92))
        .accessibilityAddTraits(.isHeader)
    }

    private var noMatches: some View {
        VStack(spacing: 8) {
            Spacer()
            Image(systemName: "magnifyingglass")
                .font(.system(size: 34, weight: .light))
                .foregroundStyle(palette.secondary.color)
            Text("No matches")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(palette.onBackground.color)
            Text("Nothing in your library matches \u{201C}\(search)\u{201D}.")
                .font(.system(size: 13))
                .foregroundStyle(palette.muted.color)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Spacer()
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
}
