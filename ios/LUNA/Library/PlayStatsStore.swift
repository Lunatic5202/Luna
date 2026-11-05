import Foundation

struct PlayStat: Codable, Equatable {
    var plays: Int
    var lastPlayed: Date
}

/// How often and how recently each song was played, saved as JSON after every change. A play is
/// counted once a song has really been listened to (see `PlaybackCoordinator`), not on a tap.
@MainActor
final class PlayStatsStore: ObservableObject {
    @Published private(set) var stats: [String: PlayStat]
    private let fileURL: URL?

    /// `fileURL` nil keeps everything in memory (tests, previews).
    init(fileURL: URL?) {
        self.fileURL = fileURL
        self.stats = Self.load(fileURL)
    }

    static func makeDefault() -> PlayStatsStore {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
        guard let directory = base?.appendingPathComponent("LUNA", isDirectory: true) else {
            return PlayStatsStore(fileURL: nil)
        }
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return PlayStatsStore(fileURL: directory.appendingPathComponent("play-stats.json"))
    }

    func recordPlay(_ trackID: String, at date: Date = Date()) {
        var stat = stats[trackID] ?? PlayStat(plays: 0, lastPlayed: date)
        stat.plays += 1
        stat.lastPlayed = date
        stats[trackID] = stat
        save()
    }

    /// For a track deleted from the library.
    func forget(_ trackID: String) {
        guard stats.removeValue(forKey: trackID) != nil else { return }
        save()
    }

    private func save() {
        guard let fileURL, let data = try? JSONEncoder().encode(stats) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }

    /// A missing or unreadable file means no history rather than a crash.
    private nonisolated static func load(_ url: URL?) -> [String: PlayStat] {
        guard let url, let data = try? Data(contentsOf: url),
              let stats = try? JSONDecoder().decode([String: PlayStat].self, from: data) else { return [:] }
        return stats
    }
}
