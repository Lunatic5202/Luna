import SwiftUI

/// Search, view switcher (Songs / Albums / Artists / Combos), sort menu and the track list itself.
struct LibraryBrowser: View {
    let tracks: [Track]
    let palette: LunaPalette
    /// Space kept clear at the bottom so the mini player never covers the last row.
    let bottomInset: CGFloat

    @EnvironmentObject private var player: PlaybackCoordinator
    @EnvironmentObject private var library: LibraryViewModel
    @EnvironmentObject private var combos: ComboStore

    @AppStorage("library.grouping") private var groupingRaw = LibraryGrouping.songs.rawValue
    @AppStorage("library.sort") private var sortRaw = LibrarySort.title.rawValue
    @State private var search = ""
    @State private var newComboTrack: Track?
    @State private var newComboName = ""
    @State private var showNewCombo = false
    @State private var renameTarget: Combo?
    @State private var renameText = ""
    @State private var showRename = false

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


            if grouping == .combos {
                combosContent
            } else if visible.isEmpty {
                noMatches
            } else {
                list(for: visible)
            }
        }
        .alert("New Combo", isPresented: $showNewCombo) {
            TextField("Name", text: $newComboName)
            Button("Create") {
                if let track = newComboTrack { combos.create(name: newComboName, trackIDs: [track.id]) }
                newComboTrack = nil
            }
            Button("Cancel", role: .cancel) { newComboTrack = nil }
        } message: {
            Text("Give your combo a name.")
        }
        .alert("Rename Combo", isPresented: $showRename) {
            TextField("Name", text: $renameText)
            Button("Save") {
                if let combo = renameTarget { combos.rename(combo.id, to: renameText) }
                renameTarget = nil
            }
            Button("Cancel", role: .cancel) { renameTarget = nil }
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

    private func row(_ track: Track, queue: [Track], in combo: Combo? = nil) -> some View {
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
            Button {
                player.playNext(track)
            } label: {
                Label("Play Next", systemImage: "text.insert")
            }
            Button {
                player.addToQueue(track)
            } label: {
                Label("Add to Queue", systemImage: "text.append")
            }
            addToComboMenu(track)
            if let combo {
                Button {
                    combos.remove(track.id, from: combo.id)
                } label: {
                    Label("Remove from Combo", systemImage: "minus.circle")
                }
            }
            Divider()
            Button(role: .destructive) {
                library.remove(track)
                combos.forget(track.id)
                player.trackWasRemoved(track.id)
            } label: {
                Label("Remove from LUNA", systemImage: "trash")
            }
        }
    }


    // MARK: - Combos

    private struct ComboEntry: Identifiable {
        let combo: Combo
        let tracks: [Track]
        var id: String { combo.id }
    }

    /// Each combo with its tracks. While searching, combos with no matching song are hidden.
    private var comboEntries: [ComboEntry] {
        let searching = !search.trimmingCharacters(in: .whitespaces).isEmpty
        return combos.combos.compactMap { combo in
            let shown = LibraryQuery.filter(combos.tracks(in: combo, from: tracks), search: search)
            return searching && shown.isEmpty ? nil : ComboEntry(combo: combo, tracks: shown)
        }
    }

    @ViewBuilder
    private var combosContent: some View {
        if combos.combos.isEmpty {
            emptyCombos
        } else {
            let entries = comboEntries
            if entries.isEmpty {
                noMatches
            } else {
                ScrollView {
                    LazyVStack(spacing: 8, pinnedViews: [.sectionHeaders]) {
                        ForEach(entries) { entry in
                            Section {
                                if entry.tracks.isEmpty {
                                    Text("No songs yet. Long-press a song and choose Add to Combo.")
                                        .font(.system(size: 13))
                                        .foregroundStyle(palette.muted.color)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(.horizontal, 4)
                                } else {
                                    ForEach(entry.tracks) { track in
                                        row(track, queue: entry.tracks, in: entry.combo)
                                    }
                                }
                            } header: {
                                comboHeader(entry)
                            }
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.bottom, bottomInset)
                }
                .scrollDismissesKeyboard(.interactively)
            }
        }
    }

    private func comboHeader(_ entry: ComboEntry) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(entry.combo.name)
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(palette.onBackground.color)
                .lineLimit(1)
            Text(entry.tracks.count == 1 ? "1 song" : "\(entry.tracks.count) songs")
                .font(.system(size: 12))
                .foregroundStyle(palette.muted.color)
            Spacer()
            Menu {
                if let first = entry.tracks.first {
                    Button {
                        player.play(tracks: entry.tracks, startingAt: first)
                    } label: {
                        Label("Play", systemImage: "play.fill")
                    }
                }
                Button {
                    renameTarget = entry.combo
                    renameText = entry.combo.name
                    showRename = true
                } label: {
                    Label("Rename", systemImage: "pencil")
                }
                Button(role: .destructive) {
                    combos.delete(entry.combo.id)
                } label: {
                    Label("Delete Combo", systemImage: "trash")
                }
            } label: {
                Image(systemName: "ellipsis.circle")
                    .font(.system(size: 18))
                    .foregroundStyle(palette.onBackground.color)
                    .frame(width: 36, height: 28)
            }
            .accessibilityLabel("Combo options for \(entry.combo.name)")
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 6)
        .background(palette.background.color.opacity(0.92))
        .accessibilityAddTraits(.isHeader)
    }

    private var emptyCombos: some View {
        VStack(spacing: 8) {
            Spacer()
            Image(systemName: "rectangle.stack")
                .font(.system(size: 34, weight: .light))
                .foregroundStyle(palette.secondary.color)
            Text("No combos yet")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(palette.onBackground.color)
            Text("Long-press any song and choose Add to Combo to start one.")
                .font(.system(size: 13))
                .foregroundStyle(palette.muted.color)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Spacer()
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private func addToComboMenu(_ track: Track) -> some View {
        Menu {
            ForEach(combos.combos) { combo in
                Button(combo.name) { combos.add(track.id, to: combo.id) }
            }
            if !combos.combos.isEmpty { Divider() }
            Button {
                newComboTrack = track
                newComboName = ""
                showNewCombo = true
            } label: {
                Label("New Combo\u{2026}", systemImage: "plus")
            }
        } label: {
            Label("Add to Combo", systemImage: "rectangle.stack.badge.plus")
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
