import Foundation

/// The songs the user hearted. Kept as a set of track ids and saved after every change.
@MainActor
final class FavoritesStore: ObservableObject {
    @Published private(set) var ids: Set<String>
    private let fileURL: URL?

    /// `fileURL` nil keeps everything in memory (tests, previews).
    init(fileURL: URL?) {
        self.fileURL = fileURL
        self.ids = fileURL.map(Self.load) ?? []
    }

    static func makeDefault() -> FavoritesStore {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
        guard let directory = base?.appendingPathComponent("LUNA", isDirectory: true) else {
            return FavoritesStore(fileURL: nil)
        }
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return FavoritesStore(fileURL: directory.appendingPathComponent("favorites.json"))
    }

    func contains(_ trackID: String) -> Bool { ids.contains(trackID) }

    func toggle(_ trackID: String) {
        if ids.contains(trackID) { ids.remove(trackID) } else { ids.insert(trackID) }
        save()
    }

    /// For a track deleted from the library.
    func forget(_ trackID: String) {
        guard ids.remove(trackID) != nil else { return }
        save()
    }

    private func save() {
        guard let fileURL, let data = try? JSONEncoder().encode(ids.sorted()) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }

    /// A missing or unreadable file means no favorites rather than a crash.
    private nonisolated static func load(_ url: URL) -> Set<String> {
        guard let data = try? Data(contentsOf: url),
              let list = try? JSONDecoder().decode([String].self, from: data) else { return [] }
        return Set(list)
    }
}
