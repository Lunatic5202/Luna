import AVFoundation
import Foundation

enum ImportError: LocalizedError {
    case unsupported

    var errorDescription: String? {
        switch self {
        case .unsupported:
            return "This file can't be played. It may be a protected or unsupported format."
        }
    }
}

/// Reads tags, length and embedded artwork from an audio file.
enum AudioMetadataReader {
    struct Metadata {
        var title: String?
        var artist: String?
        var album: String?
        /// Seconds, 0 when the file doesn't say.
        var duration: TimeInterval
    }

    /// Throws `ImportError.unsupported` when AVFoundation can't play the file.
    static func read(url: URL) async throws -> Metadata {
        let asset = AVURLAsset(url: url)
        guard try await asset.load(.isPlayable) else { throw ImportError.unsupported }

        let seconds = try await asset.load(.duration).seconds
        let items = (try? await asset.load(.commonMetadata)) ?? []

        return Metadata(
            title: await string(.commonIdentifierTitle, in: items),
            artist: await string(.commonIdentifierArtist, in: items),
            album: await string(.commonIdentifierAlbumName, in: items),
            duration: seconds.isFinite ? max(seconds, 0) : 0
        )
    }

    /// Embedded cover art, if the file has any.
    static func artworkData(url: URL) async -> Data? {
        let asset = AVURLAsset(url: url)
        guard let items = try? await asset.load(.commonMetadata),
              let item = items.first(where: { $0.identifier == .commonIdentifierArtwork }) else { return nil }
        return try? await item.load(.dataValue)
    }

    private static func string(_ identifier: AVMetadataIdentifier, in items: [AVMetadataItem]) async -> String? {
        guard let item = items.first(where: { $0.identifier == identifier }),
              let value = try? await item.load(.stringValue) else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
