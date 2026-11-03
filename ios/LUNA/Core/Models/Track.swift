import Foundation

/// A playable track, independent of where it came from.
///
/// Identifiers are LUNA's own and stay stable across launches. File URLs and security-scoped
/// bookmarks are deliberately kept out of this type; the library providers own them.
struct Track: Identifiable, Hashable, Codable {
    enum Source: String, Codable {
        /// Imported through the Files picker or a chosen folder.
        case files
        /// A synced item from the Media & Apple Music library.
        case mediaLibrary
    }

    let id: String
    var title: String
    var artist: String
    var album: String
    /// Length in seconds, 0 when unknown.
    var duration: TimeInterval
    var source: Source
    var dateAdded: Date

    init(
        id: String,
        title: String,
        artist: String = "",
        album: String = "",
        duration: TimeInterval = 0,
        source: Source,
        dateAdded: Date = Date(timeIntervalSince1970: 0)
    ) {
        self.id = id
        self.title = title
        self.artist = artist
        self.album = album
        self.duration = duration
        self.source = source
        self.dateAdded = dateAdded
    }

    var displayTitle: String { title.isBlank ? "Unknown track" : title }
    var displayArtist: String { artist.isBlank ? "Unknown artist" : artist }
    var displayAlbum: String { album.isBlank ? "Unknown album" : album }
}

private extension String {
    var isBlank: Bool { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
}
