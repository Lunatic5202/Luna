import UIKit
import XCTest
@testable import LUNA

@MainActor
private final class FakeEngine: PlaybackEngine {
    var onTimeUpdate: ((TimeInterval) -> Void)?
    var onFinish: (() -> Void)?
    var currentTime: TimeInterval = 0
    var duration: TimeInterval = 0

    private(set) var loadedURLs: [URL] = []
    private(set) var isPlaying = false

    func load(url: URL) {
        loadedURLs.append(url)
        currentTime = 0
    }
    func play() { isPlaying = true }
    func pause() { isPlaying = false }
    func seek(to time: TimeInterval) { currentTime = time }

    /// Simulates the playhead moving, the way AVPlayer's time observer would.
    func tick(to time: TimeInterval) {
        currentTime = time
        onTimeUpdate?(time)
    }
    func finish() { onFinish?() }
}

@MainActor
private final class SpyNowPlaying: NowPlayingPublishing {
    private(set) var published: [(id: String, isPlaying: Bool)] = []
    private(set) var clearCount = 0

    func publish(track: Track, position: TimeInterval, duration: TimeInterval, isPlaying: Bool, artwork: UIImage?) {
        published.append((track.id, isPlaying))
    }
    func clear() { clearCount += 1 }
}

@MainActor
final class PlaybackCoordinatorTests: XCTestCase {
    private var engine: FakeEngine!
    private var nowPlaying: SpyNowPlaying!
    private var missing: Set<String> = []
    private var tracks: [Track] = []
    private var coordinator: PlaybackCoordinator!

    override func setUp() async throws {
        engine = FakeEngine()
        nowPlaying = SpyNowPlaying()
        missing = []
        tracks = ["a", "b", "c"].map { Track(id: $0, title: $0, duration: 200, source: .files) }
        coordinator = PlaybackCoordinator(
            engine: engine,
            resolveURL: { [unowned self] track in
                missing.contains(track.id) ? nil : URL(fileURLWithPath: "/tmp/\(track.id).m4a")
            },
            nowPlaying: nowPlaying
        )
    }

    func testPlayLoadsTheChosenTrackAndStarts() {
        coordinator.play(tracks: tracks, startingAt: tracks[1])

        XCTAssertEqual(coordinator.currentTrack?.id, "b")
        XCTAssertTrue(coordinator.isPlaying)
        XCTAssertTrue(engine.isPlaying)
        XCTAssertEqual(engine.loadedURLs.map(\.lastPathComponent), ["b.m4a"])
        XCTAssertEqual(coordinator.progress.duration, 200)
        XCTAssertEqual(nowPlaying.published.last?.id, "b")
        XCTAssertEqual(nowPlaying.published.last?.isPlaying, true)
    }

    func testPauseAndResume() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        coordinator.togglePlayPause()
        XCTAssertFalse(coordinator.isPlaying)
        XCTAssertFalse(engine.isPlaying)
        XCTAssertEqual(nowPlaying.published.last?.isPlaying, false)

        coordinator.togglePlayPause()
        XCTAssertTrue(coordinator.isPlaying)
        XCTAssertTrue(engine.isPlaying)
    }

    func testFinishingATrackAdvancesToTheNext() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        engine.finish()
        XCTAssertEqual(coordinator.currentTrack?.id, "b")
        XCTAssertTrue(coordinator.isPlaying)
    }

    func testFinishingTheLastTrackStopsAndRewinds() {
        coordinator.play(tracks: tracks, startingAt: tracks[2])
        engine.tick(to: 150)
        engine.finish()

        XCTAssertEqual(coordinator.currentTrack?.id, "c")
        XCTAssertFalse(coordinator.isPlaying)
        XCTAssertEqual(coordinator.progress.position, 0)
        XCTAssertEqual(engine.currentTime, 0)
    }

    func testRepeatOneReplaysTheSameTrack() {
        coordinator.cycleRepeat() // all
        coordinator.cycleRepeat() // one
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        engine.tick(to: 120)
        engine.finish()

        XCTAssertEqual(coordinator.currentTrack?.id, "a")
        XCTAssertTrue(coordinator.isPlaying)
        XCTAssertEqual(coordinator.progress.position, 0)
        XCTAssertEqual(engine.loadedURLs.count, 1, "Repeat one rewinds instead of reloading")
    }

    func testPreviousRestartsTheTrackWhenPastTheThreshold() {
        coordinator.play(tracks: tracks, startingAt: tracks[1])
        engine.tick(to: 10)
        coordinator.previous()

        XCTAssertEqual(coordinator.currentTrack?.id, "b")
        XCTAssertEqual(engine.currentTime, 0)
        XCTAssertEqual(coordinator.progress.position, 0)
    }

    func testPreviousGoesBackNearTheStartOfATrack() {
        coordinator.play(tracks: tracks, startingAt: tracks[1])
        engine.tick(to: 1)
        coordinator.previous()
        XCTAssertEqual(coordinator.currentTrack?.id, "a")
    }

    func testPreviousOnTheFirstTrackJustRestartsIt() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        engine.tick(to: 1)
        coordinator.previous()
        XCTAssertEqual(coordinator.currentTrack?.id, "a")
        XCTAssertEqual(engine.currentTime, 0)
    }

    func testNextOnTheLastTrackDoesNothing() {
        coordinator.play(tracks: tracks, startingAt: tracks[2])
        coordinator.next()
        XCTAssertEqual(coordinator.currentTrack?.id, "c")
        XCTAssertEqual(engine.loadedURLs.count, 1)
    }

    func testSeekIsClampedToTheTrackLength() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        coordinator.seek(to: 9_999)
        XCTAssertEqual(coordinator.progress.position, 200)
        coordinator.seek(to: -5)
        XCTAssertEqual(coordinator.progress.position, 0)
    }

    func testInterruptionPausesAndResumesOnlyWhenSystemAllowsIt() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        coordinator.interruptionBegan()
        XCTAssertFalse(coordinator.isPlaying)

        coordinator.interruptionEnded(shouldResume: false)
        XCTAssertFalse(coordinator.isPlaying)

        coordinator.resume()
        coordinator.interruptionBegan()
        coordinator.interruptionEnded(shouldResume: true)
        XCTAssertTrue(coordinator.isPlaying)
    }

    func testInterruptionDoesNotStartMusicThatWasAlreadyPaused() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        coordinator.pause()
        coordinator.interruptionBegan()
        coordinator.interruptionEnded(shouldResume: true)
        XCTAssertFalse(coordinator.isPlaying)
    }

    func testLosingHeadphonesPauses() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        coordinator.routeLost()
        XCTAssertFalse(coordinator.isPlaying)
    }

    func testMissingFilesAreSkipped() {
        missing = ["b"]
        coordinator.play(tracks: tracks, startingAt: tracks[1])
        XCTAssertEqual(coordinator.currentTrack?.id, "c")
        XCTAssertNil(coordinator.errorMessage)
    }

    func testStopsWithAMessageWhenNothingIsPlayable() {
        missing = ["a", "b", "c"]
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        XCTAssertNil(coordinator.currentTrack)
        XCTAssertFalse(coordinator.isPlaying)
        XCTAssertNotNil(coordinator.errorMessage)
        coordinator.clearError()
        XCTAssertNil(coordinator.errorMessage)
    }

    func testRemovingTheCurrentTrackStopsPlayback() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        coordinator.trackWasRemoved("a")
        XCTAssertNil(coordinator.currentTrack)
        XCTAssertEqual(nowPlaying.clearCount, 1)
    }

    func testRemovingAnotherTrackKeepsPlaying() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        coordinator.trackWasRemoved("c")
        XCTAssertEqual(coordinator.currentTrack?.id, "a")
        XCTAssertTrue(coordinator.isPlaying)
    }
}
