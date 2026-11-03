import Foundation

struct ImportResult {
    var imported: [Track] = []
    /// One readable line per file that could not be imported.
    var failures: [String] = []
}

/// The Files-import library source.
///
/// iOS gives an app no view of the whole filesystem, so songs the listener picks in the Files
/// picker are copied into LUNA's own storage. That keeps playback working offline and after the
/// original is moved or deleted, and means we never hold on to a security-scoped URL: access is
/// opened, the copy is made, and access is released again before the import returns.
@MainActor
final class FilesLibraryProvider {
    private struct Record: Codable {
        var track: Track
        /// File name inside `audioDirectory`.
        var filename: String
    }

    private let directory: URL
    private var records: [Record]

    private var audioDirectory: URL { directory.appendingPathComponent("Audio", isDirectory: true) }
    private var indexURL: URL { directory.appendingPathComponent("library.json") }

    var tracks: [Track] { records.map(\.track) }

    init(directory: URL) {
        self.directory = directory
        try? FileManager.default.createDirectory(
            at: directory.appendingPathComponent("Audio", isDirectory: true),
            withIntermediateDirectories: true
        )
        self.records = Self.loadIndex(directory: directory)
    }

    /// Library in Application Support, or the temporary directory if that is unavailable.
    static func makeDefault() -> FilesLibraryProvider {
        let base = (try? FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )) ?? FileManager.default.temporaryDirectory
        return FilesLibraryProvider(directory: base.appendingPathComponent("LUNA/Library", isDirectory: true))
    }

    /// Where a track's audio lives, or nil if it is not (or no longer) in the library.
    func url(for track: Track) -> URL? {
        guard let record = records.first(where: { $0.track.id == track.id }) else { return nil }
        return audioDirectory.appendingPathComponent(record.filename)
    }

    func importFiles(from urls: [URL]) async -> ImportResult {
        var result = ImportResult()
        for source in urls {
            do {
                let record = try await importOne(source)
                records.append(record)
                result.imported.append(record.track)
            } catch {
                result.failures.append("\(source.lastPathComponent): \(error.localizedDescription)")
            }
        }
        if !result.imported.isEmpty { saveIndex() }
        return result
    }

    func remove(_ track: Track) {
        guard let index = records.firstIndex(where: { $0.track.id == track.id }) else { return }
        let record = records.remove(at: index)
        try? FileManager.default.removeItem(at: audioDirectory.appendingPathComponent(record.filename))
        saveIndex()
    }

    // MARK: - Internals

    private func importOne(_ source: URL) async throws -> Record {
        // Picker URLs are security-scoped; plain file URLs (tests) report false and are fine as is.
        let scoped = source.startAccessingSecurityScopedResource()
        defer { if scoped { source.stopAccessingSecurityScopedResource() } }

        let ext = source.pathExtension
        let filename = UUID().uuidString + (ext.isEmpty ? "" : ".\(ext)")
        let destination = audioDirectory.appendingPathComponent(filename)
        let audioFolder = audioDirectory

        // Copying a large file would stall the UI, so do it off the main actor.
        try await Task.detached(priority: .utility) {
            try FileManager.default.createDirectory(at: audioFolder, withIntermediateDirectories: true)
            try FileManager.default.copyItem(at: source, to: destination)
        }.value

        do {
            let metadata = try await AudioMetadataReader.read(url: destination)
            let fallbackTitle = source.deletingPathExtension().lastPathComponent
            let track = Track(
                id: UUID().uuidString,
                title: metadata.title ?? fallbackTitle,
                artist: metadata.artist ?? "",
                album: metadata.album ?? "",
                duration: metadata.duration,
                source: .files,
                dateAdded: Date()
            )
            return Record(track: track, filename: filename)
        } catch {
            // Don't keep a copy of something we can't play.
            try? FileManager.default.removeItem(at: destination)
            throw error
        }
    }

    private func saveIndex() {
        guard let data = try? JSONEncoder().encode(records) else { return }
        try? data.write(to: indexURL, options: .atomic)
    }

    private nonisolated static func loadIndex(directory: URL) -> [Record] {
        let indexURL = directory.appendingPathComponent("library.json")
        let audioDirectory = directory.appendingPathComponent("Audio", isDirectory: true)
        guard let data = try? Data(contentsOf: indexURL),
              let decoded = try? JSONDecoder().decode([Record].self, from: data) else { return [] }
        // Forget entries whose file has gone missing instead of listing songs that can't play.
        return decoded.filter {
            FileManager.default.fileExists(atPath: audioDirectory.appendingPathComponent($0.filename).path)
        }
    }
}
