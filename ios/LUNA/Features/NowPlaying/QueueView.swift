import SwiftUI

/// What is playing now and what comes next. Tap a song to jump to it, swipe to remove it, and
/// use Edit to drag songs into a new order.
struct QueueView: View {
    @EnvironmentObject private var player: PlaybackCoordinator
    let palette: LunaPalette

    @State private var editMode: EditMode = .inactive

    var body: some View {
        ZStack {
            LunaBackground(palette: palette)
            VStack(spacing: 0) {
                header
                list
            }
        }
        .onChange(of: player.upcoming.isEmpty) { _, isEmpty in
            if isEmpty { editMode = .inactive }
        }
    }

    private var header: some View {
        HStack {
            Text("Queue")
                .font(.system(size: 22, weight: .heavy))
                .foregroundStyle(palette.onBackground.color)
            Spacer()
            if !player.upcoming.isEmpty {
                Button(editMode == .active ? "Done" : "Edit") {
                    withAnimation { editMode = editMode == .active ? .inactive : .active }
                }
                .foregroundStyle(palette.primary.color)
                Button("Clear", role: .destructive) {
                    player.clearUpcoming()
                }
                .padding(.leading, 12)
            }
        }
        .font(.system(size: 15, weight: .semibold))
        .padding(.horizontal, 22)
        .padding(.top, 22)
        .padding(.bottom, 8)
    }

    private var list: some View {
        List {
            if let current = player.currentTrack {
                Section {
                    row(current, isCurrent: true)
                } header: {
                    sectionTitle("NOW PLAYING")
                }
            }

            Section {
                if player.upcoming.isEmpty {
                    Text("Nothing queued after this song.")
                        .font(.system(size: 13))
                        .foregroundStyle(palette.muted.color)
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                } else {
                    ForEach(player.upcoming) { track in
                        row(track, isCurrent: false)
                            .onTapGesture {
                                if editMode == .inactive { player.jumpToUpcoming(track.id) }
                            }
                    }
                    .onMove { player.moveUpcoming(from: $0, to: $1) }
                    .onDelete { player.removeUpcoming(at: $0) }
                }
            } header: {
                sectionTitle("UP NEXT · \(player.upcoming.count)")
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .environment(\.editMode, $editMode)
    }

    private func row(_ track: Track, isCurrent: Bool) -> some View {
        TrackRow(track: track, palette: palette, isCurrent: isCurrent, isPlaying: player.isPlaying)
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: 4, leading: 14, bottom: 4, trailing: 14))
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold))
            .tracking(2)
            .foregroundStyle(palette.secondary.color)
            .textCase(nil)
    }
}
