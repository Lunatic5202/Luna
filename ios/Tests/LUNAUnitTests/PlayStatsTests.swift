import UIKit
import XCTest
@testable import LUNA

@MainActor
private final class StatsEngine: PlaybackEngine {
    var onTimeUpdate: ((TimeInterval) -> Void)?
    var onFinish: (() -> Void)?
    var currentTime: TimeInterval = 0
    var duration: TimeInterval = 0
    private(set) var isPlaying = false

    func load(url: URL) { currentTime = 0 }
    func play() { isPlaying = true }
    func pause() { isPlaying = false }
    func seek(to time: TimeInterval) { currentTime = time }

    /// Plays from `from` to `to` in half-second ticks, like AVPlayer's time observer.
    func playThrough(from: TimeInterval, to: TimeInterval) {
        var time = from
        while time < to {
            time += 0.5
            currentTime = time
            onTimeUpdate?(time)
        }
    }

    func tick(to time: TimeInterval) {
        currentTime = time
        onTimeUpdate?(time)
    }
}

@MainActor
final class PlayCountingTests: XCTestCase {
    private var engine: StatsEngine!
    private var counted: [String] = []
    private var tracks: [Track] = []
    private var coordinator: PlaybackCoordinator!

    override func setUp() async throws {
        engine = StatsEngine()
        counted = []
        tracks = [
            Track(id: "a", title: "a", duration: 200, source: .files),
            Track(id: "b", title: "b", duration: 200, source: .files),
            Track(id: "short", title: "short", duration: 10, source: .files)
        ]
        coordinator = PlaybackCoordinator(
            engine: engine,
            resolveURL: { URL(fileURLWithPath: "/tmp/\($0.id).m4a") }
        )
        coordinator.onPlayCounted = { [unowned self] track in counted.append(track.id) }
    }

    func testPlayIsCountedOnceAfterTwentySecondsOfListening() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        engine.playThrough(from: 0, to: 15)
        XCTAssertTrue(counted.isEmpty)
        engine.playThrough(from: 15, to: 60)
        XCTAssertEqual(counted, ["a"])
    }

    func testSeekingAheadDoesNotCountAsListening() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        engine.playThrough(from: 0, to: 5)
        engine.tick(to: 120)
        engine.playThrough(from: 120, to: 130)
        XCTAssertTrue(counted.isEmpty, "only about 15 seconds were actually heard")
    }

    func testSkippingEarlyDoesNotCount() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        engine.playThrough(from: 0, to: 10)
        coordinator.next()
        engine.playThrough(from: 0, to: 10)
        XCTAssertTrue(counted.isEmpty)
    }

    func testEachSongCountsOnItsOwn() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        engine.playThrough(from: 0, to: 25)
        coordinator.next()
        engine.playThrough(from: 0, to: 25)
        XCTAssertEqual(counted, ["a", "b"])
    }

    func testShortSongsCountAtHalfTheirLength() {
        coordinator.play(tracks: tracks, startingAt: tracks[2])
        engine.playThrough(from: 0, to: 4)
        XCTAssertTrue(counted.isEmpty)
        engine.playThrough(from: 4, to: 6)
        XCTAssertEqual(counted, ["short"])
    }

    func testPausedTimeIsNotCounted() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        coordinator.pause()
        engine.playThrough(from: 0, to: 40)
        XCTAssertTrue(counted.isEmpty)
    }

    func testRepeatOneCountsEachLoop() {
        coordinator.cycleRepeat()
        coordinator.cycleRepeat()
        XCTAssertEqual(coordinator.repeatMode, .one)
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        engine.playThrough(from: 0, to: 25)
        engine.onFinish?()
        engine.playThrough(from: 0, to: 25)
        XCTAssertEqual(counted, ["a", "a"])
    }
}

@MainActor
final class PlayStatsStoreTests: XCTestCase {
    private func tempFile() -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("luna-stats-\(UUID().uuidString).json")
    }

    func testRecordingCountsAndTimestamps() {
        let store = PlayStatsStore(fileURL: nil)
        let first = Date(timeIntervalSince1970: 100)
        let second = Date(timeIntervalSince1970: 200)
        store.recordPlay("a", at: first)
        store.recordPlay("a", at: second)
        store.recordPlay("b", at: first)
        XCTAssertEqual(store.stats["a"], PlayStat(plays: 2, lastPlayed: second))
        XCTAssertEqual(store.stats["b"]?.plays, 1)
    }

    func testForgetRemovesOnlyThatTrack() {
        let store = PlayStatsStore(fileURL: nil)
        store.recordPlay("a")
        store.recordPlay("b")
        store.forget("a")
        store.forget("never-played")
        XCTAssertEqual(Set(store.stats.keys), ["b"])
    }

    func testHistorySurvivesRelaunch() {
        let file = tempFile()
        defer { try? FileManager.default.removeItem(at: file) }
        let first = PlayStatsStore(fileURL: file)
        first.recordPlay("a", at: Date(timeIntervalSince1970: 100))
        XCTAssertEqual(PlayStatsStore(fileURL: file).stats, first.stats)
    }

    func testCorruptFileMeansNoHistory() throws {
        let file = tempFile()
        defer { try? FileManager.default.removeItem(at: file) }
        try Data("nope".utf8).write(to: file)
        XCTAssertTrue(PlayStatsStore(fileURL: file).stats.isEmpty)
    }
}

final class PlayHistorySortTests: XCTestCase {
    private func track(_ id: String) -> Track { Track(id: id, title: id, source: .files) }
    private let tracks = ["a", "b", "c", "d"].map { Track(id: $0, title: $0, source: .files) }

    func testRecentlyPlayedPutsTheNewestFirstAndUnplayedLast() {
        let stats = [
            "b": PlayStat(plays: 1, lastPlayed: Date(timeIntervalSince1970: 100)),
            "c": PlayStat(plays: 1, lastPlayed: Date(timeIntervalSince1970: 200))
        ]
        let sorted = LibraryQuery.sort(tracks, by: .recentlyPlayed, stats: stats)
        XCTAssertEqual(sorted.map(\.id), ["c", "b", "a", "d"])
    }

    func testMostPlayedOrdersByCountThenTitle() {
        let stats = [
            "a": PlayStat(plays: 2, lastPlayed: Date(timeIntervalSince1970: 1)),
            "c": PlayStat(plays: 5, lastPlayed: Date(timeIntervalSince1970: 1)),
            "d": PlayStat(plays: 2, lastPlayed: Date(timeIntervalSince1970: 1))
        ]
        let sorted = LibraryQuery.sort(tracks, by: .mostPlayed, stats: stats)
        XCTAssertEqual(sorted.map(\.id), ["c", "a", "d", "b"])
    }

    func testWithNoHistoryTheOrderFallsBackToTitle() {
        XCTAssertEqual(LibraryQuery.sort(tracks.reversed(), by: .recentlyPlayed).map(\.id), ["a", "b", "c", "d"])
        XCTAssertEqual(LibraryQuery.sort(tracks.reversed(), by: .mostPlayed).map(\.id), ["a", "b", "c", "d"])
    }
}

@MainActor
final class SettingsSupportTests: XCTestCase {
    private func tempFile() -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("luna-settings-\(UUID().uuidString).json")
    }

    func testClearingLyricsEmptiesTheCacheAndTheFile() {
        let file = tempFile()
        defer { try? FileManager.default.removeItem(at: file) }
        let cache = LyricsCache(fileURL: file)
        cache.storeAvailable("k", text: "words", synced: false)
        cache.clear()
        XCTAssertTrue(cache.entries.isEmpty)
        XCTAssertTrue(LyricsCache(fileURL: file).entries.isEmpty)
    }

    func testClearingPlayHistoryPersists() {
        let file = tempFile()
        defer { try? FileManager.default.removeItem(at: file) }
        let store = PlayStatsStore(fileURL: file)
        store.recordPlay("a")
        store.clearAll()
        XCTAssertTrue(store.stats.isEmpty)
        XCTAssertTrue(PlayStatsStore(fileURL: file).stats.isEmpty)
    }

    func testVersionStringFormats() {
        XCTAssertEqual(AppInfo.versionString(short: "0.1.0", build: "7"), "0.1.0 (7)")
        XCTAssertEqual(AppInfo.versionString(short: "0.1.0", build: nil), "0.1.0")
        XCTAssertEqual(AppInfo.versionString(short: "0.1.0", build: " "), "0.1.0")
        XCTAssertEqual(AppInfo.versionString(short: nil, build: "7"), "unknown (7)")
    }
}
