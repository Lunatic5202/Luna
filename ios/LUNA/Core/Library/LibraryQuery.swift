import Foundation

enum LibrarySort: String, CaseIterable, Identifiable {
    case title, artist, recentlyAdded, recentlyPlayed, mostPlayed

    var id: String { rawValue }

    var label: String {
        switch self {
        case .title: return "Title"
        case .artist: return "Artist"
        case .recentlyAdded: return "Recently added"
        case .recentlyPlayed: return "Recently played"
        case .mostPlayed: return "Most played"
        }
    }
}

enum LibraryGrouping: String, CaseIterable, Identifiable {
    case songs, albums, artists, combos

    var id: String { rawValue }

    var label: String {
        switch self {
        case .songs: return "Songs"
        case .albums: return "Albums"
        case .artists: return "Artists"
        case .combos: return "Combos"
        }
    }
}

/// An album or an artist with its tracks, in the order the library is currently sorted.
struct TrackGroup: Identifiable, Equatable {
    let id: String
    let title: String
    let subtitle: String
    let tracks: [Track]
}

/// Search, sorting and grouping for the library. Pure functions over `[Track]` so they can be
/// unit-tested without any UI.
enum LibraryQuery {
    /// Case- and accent-insensitive; every word typed must appear in the title, artist or album.
    static func filter(_ tracks: [Track], search: String) -> [Track] {
        let words = fold(search).split(whereSeparator: \.isWhitespace).map(String.init)
        guard !words.isEmpty else { return tracks }
        return tracks.filter { track in
            let haystack = fold([track.title, track.artist, track.album].joined(separator: " "))
            return words.allSatisfy { haystack.contains($0) }
        }
    }

    /// `stats` only matters for the two play-history sorts; songs never played come last, by title.
    static func sort(_ tracks: [Track], by sort: LibrarySort, stats: [String: PlayStat] = [:]) -> [Track] {
        tracks.sorted { a, b in
            switch sort {
            case .title:
                return ordered(a.displayTitle, b.displayTitle, tiebreak: a.id < b.id)
            case .artist:
                if a.displayArtist.localizedStandardCompare(b.displayArtist) != .orderedSame {
                    return a.displayArtist.localizedStandardCompare(b.displayArtist) == .orderedAscending
                }
                return ordered(a.displayTitle, b.displayTitle, tiebreak: a.id < b.id)
            case .recentlyAdded:
                if a.dateAdded != b.dateAdded { return a.dateAdded > b.dateAdded }
                return ordered(a.displayTitle, b.displayTitle, tiebreak: a.id < b.id)
            case .recentlyPlayed:
                switch (stats[a.id]?.lastPlayed, stats[b.id]?.lastPlayed) {
                case let (x?, y?) where x != y: return x > y
                case (.some, .none): return true
                case (.none, .some): return false
                default: return ordered(a.displayTitle, b.displayTitle, tiebreak: a.id < b.id)
                }
            case .mostPlayed:
                let playsA = stats[a.id]?.plays ?? 0
                let playsB = stats[b.id]?.plays ?? 0
                if playsA != playsB { return playsA > playsB }
                return ordered(a.displayTitle, b.displayTitle, tiebreak: a.id < b.id)
            }
        }
    }

    /// Albums or artists, alphabetical. `.songs` and `.combos` have no groups here.
    static func groups(from tracks: [Track], by grouping: LibraryGrouping) -> [TrackGroup] {
        // Combos are the user's own lists and come from `ComboStore`, not from track metadata.
        guard grouping == .albums || grouping == .artists else { return [] }
        var order: [String] = []
        var buckets: [String: [Track]] = [:]
        for track in tracks {
            let name = grouping == .albums ? track.displayAlbum : track.displayArtist
            let key = fold(name)
            if buckets[key] == nil { order.append(key) }
            buckets[key, default: []].append(track)
        }
        return order
            .compactMap { key -> TrackGroup? in
                guard let members = buckets[key], let first = members.first else { return nil }
                switch grouping {
                case .albums:
                    let artists = Set(members.map { fold($0.displayArtist) })
                    return TrackGroup(
                        id: "album:\(key)",
                        title: first.displayAlbum,
                        subtitle: artists.count == 1 ? first.displayArtist : "Various artists",
                        tracks: members
                    )
                default:
                    return TrackGroup(
                        id: "artist:\(key)",
                        title: first.displayArtist,
                        subtitle: members.count == 1 ? "1 song" : "\(members.count) songs",
                        tracks: members
                    )
                }
            }
            .sorted { $0.title.localizedStandardCompare($1.title) == .orderedAscending }
    }

    private static func ordered(_ a: String, _ b: String, tiebreak: Bool) -> Bool {
        let result = a.localizedStandardCompare(b)
        return result == .orderedSame ? tiebreak : result == .orderedAscending
    }

    private static func fold(_ text: String) -> String {
        text.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: nil)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
