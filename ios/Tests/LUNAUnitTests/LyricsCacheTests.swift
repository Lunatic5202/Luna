import XCTest
@testable import LUNA

private final class TestClock {
    var date = Date(timeIntervalSince1970: 1_000_000)
    func advance(days: Double) { date = date.addingTimeInterval(days * 24 * 3600) }
}

private func song(_ title: String, artist: String = "Band", album: String = "") -> Track {
    Track(id: title, title: title, artist: artist, album: album, source: .files)
}

private func tempFile() -> URL {
    FileManager.default.temporaryDirectory.appendingPathComponent("luna-lyrics-\(UUID().uuidString).json")
}

final class LyricsCacheTests: XCTestCase {
    func testKeyIgnoresCaseAccentsAndPunctuation() {
        XCTAssertEqual(
            LyricsCache.key(for: song("Hello, World!", artist: "A.B")),
            LyricsCache.key(for: song("hello world", artist: "a b"))
        )
        XCTAssertEqual(LyricsCache.key(for: song("Café")), LyricsCache.key(for: song("cafe")))
        XCTAssertNotEqual(LyricsCache.key(for: song("One")), LyricsCache.key(for: song("Two")))
    }

    func testStoredLyricsAreFound() {
        let cache = LyricsCache(fileURL: nil)
        cache.storeAvailable("k", text: "words", synced: true)
        XCTAssertEqual(cache.lookup("k"), .available(text: "words", synced: true))
        XCTAssertNil(cache.lookup("other"))
    }

    func testFoundLyricsExpireAfterAMonth() {
        let clock = TestClock()
        let cache = LyricsCache(fileURL: nil, now: { clock.date })
        cache.storeAvailable("k", text: "words", synced: false)
        clock.advance(days: 29)
        XCTAssertNotNil(cache.lookup("k"))
        clock.advance(days: 2)
        XCTAssertNil(cache.lookup("k"))
    }

    func testNotFoundIsRememberedForAFewDaysOnly() {
        let clock = TestClock()
        let cache = LyricsCache(fileURL: nil, now: { clock.date })
        cache.storeNotFound("k")
        XCTAssertEqual(cache.lookup("k"), .notFound)
        clock.advance(days: 4)
        XCTAssertNil(cache.lookup("k"))
    }

    func testAClockSetBackwardsDoesNotServeStaleEntries() {
        let clock = TestClock()
        let cache = LyricsCache(fileURL: nil, now: { clock.date })
        cache.storeAvailable("k", text: "words", synced: false)
        clock.advance(days: -1)
        XCTAssertNil(cache.lookup("k"))
    }

    func testOldestEntriesAreDroppedPastTheLimit() {
        let clock = TestClock()
        let cache = LyricsCache(fileURL: nil, now: { clock.date })
        for index in 0..<(LyricsCache.maxEntries + 5) {
            clock.date = clock.date.addingTimeInterval(1)
            cache.storeAvailable("k\(index)", text: "t", synced: false)
        }
        XCTAssertEqual(cache.entries.count, LyricsCache.maxEntries)
        XCTAssertNil(cache.entries["k0"])
        XCTAssertNotNil(cache.entries["k\(LyricsCache.maxEntries + 4)"])
    }

    func testHugeLyricsAreNotStored() {
        let cache = LyricsCache(fileURL: nil)
        cache.storeAvailable("k", text: String(repeating: "a", count: LyricsCache.maxTextLength + 1), synced: false)
        XCTAssertNil(cache.lookup("k"))
    }

    func testCacheSurvivesRelaunch() {
        let file = tempFile()
        defer { try? FileManager.default.removeItem(at: file) }
        LyricsCache(fileURL: file).storeAvailable("k", text: "words", synced: true)
        XCTAssertEqual(LyricsCache(fileURL: file).lookup("k"), .available(text: "words", synced: true))
    }

    func testCorruptFileMeansAnEmptyCache() throws {
        let file = tempFile()
        defer { try? FileManager.default.removeItem(at: file) }
        try Data("nope".utf8).write(to: file)
        XCTAssertTrue(LyricsCache(fileURL: file).entries.isEmpty)
    }
}

private final class FakeFetcher: LyricsFetching {
    var result: LyricsState
    private(set) var calls = 0
    init(_ result: LyricsState) { self.result = result }
    func fetch(for track: Track) async -> LyricsState {
        calls += 1
        return result
    }
}

@MainActor
final class LyricsStoreTests: XCTestCase {
    func testSecondLoadComesFromTheCache() async {
        let fetcher = FakeFetcher(.available(text: "words", synced: false))
        let store = LyricsStore(fetcher: fetcher, cache: LyricsCache(fileURL: nil))
        await store.load(for: song("A"))
        await store.load(for: song("A"))
        XCTAssertEqual(fetcher.calls, 1)
        XCTAssertEqual(store.state, .available(text: "words", synced: false))
    }

    func testNotFoundIsCachedToo() async {
        let fetcher = FakeFetcher(.notFound)
        let store = LyricsStore(fetcher: fetcher, cache: LyricsCache(fileURL: nil))
        await store.load(for: song("A"))
        await store.load(for: song("A"))
        XCTAssertEqual(fetcher.calls, 1)
        XCTAssertEqual(store.state, .notFound)
    }

    func testFailuresAreNotCachedSoTryAgainWorks() async {
        let fetcher = FakeFetcher(.failed("offline"))
        let store = LyricsStore(fetcher: fetcher, cache: LyricsCache(fileURL: nil))
        await store.load(for: song("A"))
        XCTAssertEqual(store.state, .failed("offline"))
        fetcher.result = .available(text: "back online", synced: false)
        await store.load(for: song("A"))
        XCTAssertEqual(fetcher.calls, 2)
        XCTAssertEqual(store.state, .available(text: "back online", synced: false))
    }

    func testNoTrackMeansNoLyrics() async {
        let fetcher = FakeFetcher(.notFound)
        let store = LyricsStore(fetcher: fetcher, cache: LyricsCache(fileURL: nil))
        await store.load(for: nil)
        XCTAssertEqual(store.state, .notFound)
        XCTAssertEqual(fetcher.calls, 0)
    }
}
