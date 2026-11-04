import CryptoKit
import Foundation

/// Lyrics kept on the phone so a song is looked up once, not every time it is opened. Found
/// lyrics are kept for a month; "not found" is remembered for a few days so a missing song does
/// not hit the network on every visit. Failures are never cached.
final class LyricsCache {
    struct Entry: Codable, Equatable {
        var text: String?
        var synced: Bool
        var notFound: Bool
        var savedAt: Date
    }

    static let positiveTTL: TimeInterval = 30 * 24 * 3600
    static let negativeTTL: TimeInterval = 3 * 24 * 3600
    static let maxEntries = 200
    static let maxTextLength = 200_000

    private(set) var entries: [String: Entry]
    private let fileURL: URL?
    private let now: () -> Date

    /// `fileURL` nil keeps everything in memory (tests).
    init(fileURL: URL?, now: @escaping () -> Date = Date.init) {
        self.fileURL = fileURL
        self.now = now
        self.entries = Self.load(fileURL)
    }

    static func makeDefault() -> LyricsCache {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
        guard let directory = base?.appendingPathComponent("LUNA", isDirectory: true) else {
            return LyricsCache(fileURL: nil)
        }
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return LyricsCache(fileURL: directory.appendingPathComponent("lyrics-cache.json"))
    }

    // MARK: - Keys

    /// Same song, same key, whatever the capitalisation, accents or punctuation in its tags.
    static func key(for track: Track) -> String {
        let identity = [track.title, track.artist, track.album].map(normalize).joined(separator: "\u{0}")
        return SHA256.hash(data: Data(identity.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    static func normalize(_ text: String) -> String {
        let folded = text.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: nil)
        var result = ""
        var lastWasSpace = true
        for scalar in folded.unicodeScalars {
            if CharacterSet.alphanumerics.contains(scalar) {
                result.unicodeScalars.append(scalar)
                lastWasSpace = false
            } else if !lastWasSpace {
                result.append(" ")
                lastWasSpace = true
            }
        }
        return result.trimmingCharacters(in: .whitespaces)
    }

    // MARK: - Reading and writing

    /// `.available` or `.notFound` when a fresh entry exists, otherwise nil.
    func lookup(_ key: String) -> LyricsState? {
        guard let entry = entries[key] else { return nil }
        let age = now().timeIntervalSince(entry.savedAt)
        let limit = entry.notFound ? Self.negativeTTL : Self.positiveTTL
        guard age >= 0, age <= limit else { return nil }
        if entry.notFound { return .notFound }
        guard let text = entry.text else { return nil }
        return .available(text: text, synced: entry.synced)
    }

    func storeAvailable(_ key: String, text: String, synced: Bool) {
        guard text.count <= Self.maxTextLength else { return }
        entries[key] = Entry(text: text, synced: synced, notFound: false, savedAt: now())
        trimAndSave()
    }

    func storeNotFound(_ key: String) {
        entries[key] = Entry(text: nil, synced: false, notFound: true, savedAt: now())
        trimAndSave()
    }

    // MARK: - Persistence

    private func trimAndSave() {
        if entries.count > Self.maxEntries {
            let oldest = entries.sorted { $0.value.savedAt < $1.value.savedAt }
                .prefix(entries.count - Self.maxEntries)
            for (key, _) in oldest { entries.removeValue(forKey: key) }
        }
        guard let fileURL, let data = try? JSONEncoder().encode(entries) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }

    /// A missing or unreadable file means an empty cache rather than a crash.
    private static func load(_ url: URL?) -> [String: Entry] {
        guard let url, let data = try? Data(contentsOf: url),
              let entries = try? JSONDecoder().decode([String: Entry].self, from: data) else { return [:] }
        return entries
    }
}

/// What the lyrics screen watches: the cache first, then the network.
@MainActor
final class LyricsStore: ObservableObject {
    @Published private(set) var state: LyricsState = .loading

    private let fetcher: LyricsFetching
    private let cache: LyricsCache

    init(fetcher: LyricsFetching, cache: LyricsCache) {
        self.fetcher = fetcher
        self.cache = cache
    }

    static func makeDefault() -> LyricsStore {
        LyricsStore(fetcher: LRCLibClient(), cache: .makeDefault())
    }

    func load(for track: Track?) async {
        guard let track else {
            state = .notFound
            return
        }
        let key = LyricsCache.key(for: track)
        if let cached = cache.lookup(key) {
            state = cached
            return
        }

        state = .loading
        let result = await fetcher.fetch(for: track)
        // The screen moved on to another song while this one was loading.
        if Task.isCancelled { return }

        switch result {
        case .available(let text, let synced): cache.storeAvailable(key, text: text, synced: synced)
        case .notFound: cache.storeNotFound(key)
        case .loading, .failed: break
        }
        state = result
    }
}
