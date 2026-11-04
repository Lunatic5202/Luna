import Foundation

enum RepeatMode: String, Codable, CaseIterable {
    case off, all, one

    /// off -> all -> one -> off, the order the repeat button cycles through.
    var next: RepeatMode {
        switch self {
        case .off: return .all
        case .all: return .one
        case .one: return .off
        }
    }
}

/// Pure queue logic (order, shuffle, repeat) with no audio or UI in it, so it can be unit-tested.
struct PlaybackQueue: Equatable {
    private(set) var tracks: [Track]
    /// Playback order as indices into `tracks`. The identity order unless shuffled.
    private(set) var order: [Int]
    /// Where we are within `order`.
    private(set) var position: Int
    private(set) var shuffleEnabled: Bool
    var repeatMode: RepeatMode

    init(
        tracks: [Track],
        startIndex: Int = 0,
        shuffle: Bool = false,
        repeatMode: RepeatMode = .off
    ) {
        self.tracks = tracks
        self.order = Array(tracks.indices)
        self.position = tracks.indices.contains(startIndex) ? startIndex : 0
        self.shuffleEnabled = false
        self.repeatMode = repeatMode
        if shuffle { setShuffle(true) }
    }

    var count: Int { tracks.count }

    var current: Track? {
        order.indices.contains(position) ? tracks[order[position]] : nil
    }

    /// Tracks after the current one, in playback order.
    var upcoming: [Track] {
        guard !order.isEmpty else { return [] }
        return order.dropFirst(position + 1).map { tracks[$0] }
    }

    /// Turning shuffle on keeps the current track first and shuffles the rest.
    /// Turning it off restores the original order and stays on the current track.
    mutating func setShuffle<G: RandomNumberGenerator>(_ enabled: Bool, using generator: inout G) {
        guard enabled != shuffleEnabled else { return }
        shuffleEnabled = enabled
        guard order.indices.contains(position) else { return }
        let currentTrackIndex = order[position]
        if enabled {
            var rest = tracks.indices.filter { $0 != currentTrackIndex }
            rest.shuffle(using: &generator)
            order = [currentTrackIndex] + rest
            position = 0
        } else {
            order = Array(tracks.indices)
            position = currentTrackIndex
        }
    }

    mutating func setShuffle(_ enabled: Bool) {
        var generator = SystemRandomNumberGenerator()
        setShuffle(enabled, using: &generator)
    }

    /// Moves to the next track and returns it, or nil when playback should stop.
    ///
    /// `userInitiated` is true for the next button and false when a track finishes by itself:
    /// repeat-one only holds a track on its own ending, not when the listener skips it.
    @discardableResult
    mutating func advance(userInitiated: Bool) -> Track? {
        guard !order.isEmpty else { return nil }
        if repeatMode == .one && !userInitiated { return current }
        if position + 1 < order.count {
            position += 1
            return current
        }
        if repeatMode == .all || repeatMode == .one {
            position = 0
            return current
        }
        return nil
    }

    /// Moves to the previous track and returns the track now current. At the very start with
    /// repeat off it stays put, so callers compare ids to know whether anything moved.
    @discardableResult
    mutating func goBack() -> Track? {
        guard !order.isEmpty else { return nil }
        if position > 0 {
            position -= 1
        } else if repeatMode != .off {
            position = order.count - 1
        }
        return current
    }

    @discardableResult
    mutating func jump(to id: String) -> Track? {
        guard let target = order.firstIndex(where: { tracks[$0].id == id }) else { return nil }
        position = target
        return current
    }

    // MARK: - Editing the upcoming list

    private var upcomingStart: Int { position + 1 }

    /// Removes tracks by their offset within `upcoming`. The current track is never affected.
    mutating func removeUpcoming(at offsets: IndexSet) {
        guard order.indices.contains(position) else { return }
        let positions = Set(offsets.map { upcomingStart + $0 }.filter { order.indices.contains($0) })
        guard !positions.isEmpty else { return }
        let removedTracks = Set(positions.map { order[$0] })
        var remap: [Int: Int] = [:]
        var kept: [Track] = []
        for (index, track) in tracks.enumerated() where !removedTracks.contains(index) {
            remap[index] = kept.count
            kept.append(track)
        }
        order = order.enumerated().compactMap { offset, trackIndex in
            positions.contains(offset) ? nil : remap[trackIndex]
        }
        tracks = kept
        normalizeIfLinear()
    }

    mutating func clearUpcoming() {
        removeUpcoming(at: IndexSet(integersIn: 0..<upcoming.count))
    }

    /// Moves upcoming tracks, using the same offsets as SwiftUI's `onMove`: `destination` is
    /// an insertion point in the list as it was before the move.
    mutating func moveUpcoming(from source: IndexSet, to destination: Int) {
        guard order.indices.contains(position) else { return }
        var items = Array(order[upcomingStart...])
        let valid = source.filter { items.indices.contains($0) }
        guard !valid.isEmpty else { return }
        let moved = valid.map { items[$0] }
        let insertAt = destination - valid.filter { $0 < destination }.count
        for index in valid.sorted(by: >) { items.remove(at: index) }
        items.insert(contentsOf: moved, at: min(max(insertAt, 0), items.count))
        order = Array(order[..<upcomingStart]) + items
        normalizeIfLinear()
    }

    /// Adds `track` to the upcoming list (the end when `offset` is nil). A copy that is already
    /// queued is moved rather than duplicated, and the playing track is left alone.
    mutating func insertUpcoming(_ track: Track, at offset: Int? = nil) {
        guard order.indices.contains(position), current?.id != track.id else { return }
        if let existing = upcoming.firstIndex(where: { $0.id == track.id }) {
            removeUpcoming(at: IndexSet(integer: existing))
        }
        tracks.append(track)
        let target = min(max(offset ?? upcoming.count, 0), upcoming.count)
        order.insert(tracks.count - 1, at: upcomingStart + target)
        normalizeIfLinear()
    }

    /// Makes an upcoming track current. Unlike `jump(to:)` it never goes backwards.
    @discardableResult
    mutating func jumpToUpcoming(id: String) -> Track? {
        guard order.indices.contains(position),
              let offset = upcoming.firstIndex(where: { $0.id == id }) else { return nil }
        position = upcomingStart + offset
        return current
    }

    /// Without shuffle the queue *is* its order, so edits are written back into `tracks`. That
    /// way a manual order survives turning shuffle on and off again.
    private mutating func normalizeIfLinear() {
        guard !shuffleEnabled else { return }
        tracks = order.map { tracks[$0] }
        order = Array(tracks.indices)
    }
}
