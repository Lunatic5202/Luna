import UIKit
import XCTest
@testable import LUNA

@MainActor
private final class FakeSleepEngine: PlaybackEngine {
    var onTimeUpdate: ((TimeInterval) -> Void)?
    var onFinish: (() -> Void)?
    var currentTime: TimeInterval = 0
    var duration: TimeInterval = 0
    private(set) var isPlaying = false

    func load(url: URL) { currentTime = 0 }
    func play() { isPlaying = true }
    func pause() { isPlaying = false }
    func seek(to time: TimeInterval) { currentTime = time }
    func finish() { onFinish?() }
}

/// Lets a test fire the timer by hand and see what was scheduled or cancelled.
@MainActor
private final class ManualScheduler {
    private(set) var delays: [TimeInterval] = []
    private(set) var cancelled = 0
    private var actions: [@MainActor () -> Void] = []

    var scheduler: SleepScheduler {
        { [unowned self] delay, action in
            self.delays.append(delay)
            self.actions.append(action)
            return { [unowned self] in self.cancelled += 1 }
        }
    }

    func fireLast() { actions.last?() }
}

@MainActor
final class SleepTimerTests: XCTestCase {
    private var manual: ManualScheduler!
    private var timer: SleepTimer!
    private let start = Date(timeIntervalSince1970: 1_000)

    override func setUp() async throws {
        manual = ManualScheduler()
        let fixedNow = start
        timer = SleepTimer(now: { fixedNow }, schedule: manual.scheduler)
    }

    func testStartsOff() {
        XCTAssertNil(timer.option)
        XCTAssertNil(timer.endDate)
    }

    func testCountdownSchedulesAndExposesTheEndDate() {
        timer.start(.minutes(15))
        XCTAssertEqual(timer.option, .minutes(15))
        XCTAssertEqual(manual.delays, [900])
        XCTAssertEqual(timer.endDate, start.addingTimeInterval(900))
    }

    func testFiringCallsBackAndTurnsTheTimerOff() {
        var fired = 0
        timer.onFire = { fired += 1 }
        timer.start(.minutes(30))
        manual.fireLast()
        XCTAssertEqual(fired, 1)
        XCTAssertNil(timer.option)
        XCTAssertNil(timer.endDate)
    }

    func testCancelStopsTheScheduledAction() {
        timer.start(.minutes(30))
        timer.cancel()
        XCTAssertEqual(manual.cancelled, 1)
        XCTAssertNil(timer.option)
    }

    func testStartingAgainReplacesTheRunningTimer() {
        timer.start(.minutes(15))
        timer.start(.minutes(60))
        XCTAssertEqual(manual.cancelled, 1)
        XCTAssertEqual(manual.delays, [900, 3600])
        XCTAssertEqual(timer.option, .minutes(60))
    }

    func testEndOfTrackSchedulesNothingAndIsConsumedOnce() {
        timer.start(.endOfTrack)
        XCTAssertTrue(manual.delays.isEmpty)
        XCTAssertNil(timer.endDate)
        XCTAssertTrue(timer.consumeEndOfTrack())
        XCTAssertNil(timer.option)
        XCTAssertFalse(timer.consumeEndOfTrack())
    }

    func testCountdownIsNotConsumedByATrackEnding() {
        timer.start(.minutes(15))
        XCTAssertFalse(timer.consumeEndOfTrack())
        XCTAssertEqual(timer.option, .minutes(15))
    }

    func testPresetsCoverTheCommonChoices() {
        XCTAssertEqual(SleepTimer.Option.presets.last, .endOfTrack)
        XCTAssertEqual(Set(SleepTimer.Option.presets.map(\.id)).count, SleepTimer.Option.presets.count)
    }
}

@MainActor
final class SleepTimerPlaybackTests: XCTestCase {
    private var engine: FakeSleepEngine!
    private var manual: ManualScheduler!
    private var timer: SleepTimer!
    private var tracks: [Track] = []
    private var coordinator: PlaybackCoordinator!

    override func setUp() async throws {
        engine = FakeSleepEngine()
        manual = ManualScheduler()
        timer = SleepTimer(schedule: manual.scheduler)
        tracks = ["a", "b", "c"].map { Track(id: $0, title: $0, duration: 200, source: .files) }
        coordinator = PlaybackCoordinator(
            engine: engine,
            resolveURL: { URL(fileURLWithPath: "/tmp/\($0.id).m4a") },
            sleepTimer: timer
        )
    }

    func testCountdownPausesPlayback() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        timer.start(.minutes(15))
        manual.fireLast()
        XCTAssertFalse(coordinator.isPlaying)
        XCTAssertFalse(engine.isPlaying)
        XCTAssertEqual(coordinator.currentTrack?.id, "a")
    }

    func testEndOfSongStopsOnTheNextTrackInsteadOfPlayingOn() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        timer.start(.endOfTrack)
        engine.finish()
        XCTAssertFalse(coordinator.isPlaying)
        XCTAssertFalse(engine.isPlaying)
        XCTAssertEqual(coordinator.currentTrack?.id, "b")
        XCTAssertNil(timer.option)
    }

    func testEndOfSongOnTheLastTrackStaysPutAtTheStart() {
        coordinator.play(tracks: tracks, startingAt: tracks[2])
        timer.start(.endOfTrack)
        engine.finish()
        XCTAssertFalse(coordinator.isPlaying)
        XCTAssertEqual(coordinator.currentTrack?.id, "c")
        XCTAssertEqual(coordinator.progress.position, 0)
    }

    func testPlaybackContinuesNormallyWithoutATimer() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        engine.finish()
        XCTAssertTrue(coordinator.isPlaying)
        XCTAssertEqual(coordinator.currentTrack?.id, "b")
    }

    func testLeftoverEndOfSongDoesNotCatchANewSession() {
        coordinator.play(tracks: tracks, startingAt: tracks[0])
        timer.start(.endOfTrack)
        coordinator.stop()
        coordinator.play(tracks: tracks, startingAt: tracks[1])
        XCTAssertNil(timer.option)
        engine.finish()
        XCTAssertTrue(coordinator.isPlaying)
        XCTAssertEqual(coordinator.currentTrack?.id, "c")
    }
}
