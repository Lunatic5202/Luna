import XCTest
@testable import LUNA

/// Deterministic generator so shuffle tests don't flake.
private struct SeededGenerator: RandomNumberGenerator {
    var state: UInt64
    mutating func next() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }
}

private func makeTracks(_ ids: [String]) -> [Track] {
    ids.map { Track(id: $0, title: $0.uppercased(), source: .files) }
}

final class PlaybackQueueTests: XCTestCase {
    func testStartsOnRequestedTrack() {
        let queue = PlaybackQueue(tracks: makeTracks(["a", "b", "c"]), startIndex: 1)
        XCTAssertEqual(queue.current?.id, "b")
        XCTAssertEqual(queue.upcoming.map(\.id), ["c"])
    }

    func testOutOfRangeStartFallsBackToFirst() {
        let queue = PlaybackQueue(tracks: makeTracks(["a", "b"]), startIndex: 9)
        XCTAssertEqual(queue.current?.id, "a")
    }

    func testEmptyQueueDoesNothing() {
        var queue = PlaybackQueue(tracks: [])
        XCTAssertNil(queue.current)
        XCTAssertNil(queue.advance(userInitiated: true))
        XCTAssertNil(queue.goBack())
        XCTAssertTrue(queue.upcoming.isEmpty)
    }

    func testAdvanceWalksThenStopsAtTheEnd() {
        var queue = PlaybackQueue(tracks: makeTracks(["a", "b"]))
        XCTAssertEqual(queue.advance(userInitiated: false)?.id, "b")
        XCTAssertNil(queue.advance(userInitiated: false))
        XCTAssertEqual(queue.current?.id, "b", "Reaching the end leaves the last track current")
    }

    func testRepeatAllWrapsAround() {
        var queue = PlaybackQueue(tracks: makeTracks(["a", "b"]), startIndex: 1, repeatMode: .all)
        XCTAssertEqual(queue.advance(userInitiated: false)?.id, "a")
    }

    func testRepeatOneHoldsTheTrackOnNaturalEndButNotOnSkip() {
        var queue = PlaybackQueue(tracks: makeTracks(["a", "b", "c"]), repeatMode: .one)
        XCTAssertEqual(queue.advance(userInitiated: false)?.id, "a")
        XCTAssertEqual(queue.advance(userInitiated: true)?.id, "b")
    }

    func testSkippingPastTheEndUnderRepeatOneWraps() {
        var queue = PlaybackQueue(tracks: makeTracks(["a", "b"]), startIndex: 1, repeatMode: .one)
        XCTAssertEqual(queue.advance(userInitiated: true)?.id, "a")
    }

    func testGoBackMovesBackAndStaysAtTheStart() {
        var queue = PlaybackQueue(tracks: makeTracks(["a", "b", "c"]), startIndex: 2)
        XCTAssertEqual(queue.goBack()?.id, "b")
        XCTAssertEqual(queue.goBack()?.id, "a")
        XCTAssertEqual(queue.goBack()?.id, "a", "At the start with repeat off the queue doesn't move")
    }

    func testGoBackWrapsUnderRepeatAll() {
        var queue = PlaybackQueue(tracks: makeTracks(["a", "b", "c"]), repeatMode: .all)
        XCTAssertEqual(queue.goBack()?.id, "c")
    }

    func testJumpMovesToTheTrackAndRejectsUnknownIds() {
        var queue = PlaybackQueue(tracks: makeTracks(["a", "b", "c"]))
        XCTAssertEqual(queue.jump(to: "c")?.id, "c")
        XCTAssertNil(queue.jump(to: "zzz"))
        XCTAssertEqual(queue.current?.id, "c")
    }

    func testShuffleKeepsCurrentFirstAndUsesEveryTrackOnce() {
        var generator = SeededGenerator(state: 42)
        var queue = PlaybackQueue(tracks: makeTracks(["a", "b", "c", "d", "e", "f"]), startIndex: 2)
        queue.setShuffle(true, using: &generator)

        XCTAssertTrue(queue.shuffleEnabled)
        XCTAssertEqual(queue.current?.id, "c")
        let everything = [queue.current!.id] + queue.upcoming.map(\.id)
        XCTAssertEqual(Set(everything), ["a", "b", "c", "d", "e", "f"])
        XCTAssertEqual(everything.count, 6)
    }

    func testShuffleOffRestoresOriginalOrderOnTheSameTrack() {
        var generator = SeededGenerator(state: 7)
        var queue = PlaybackQueue(tracks: makeTracks(["a", "b", "c", "d"]), startIndex: 1)
        queue.setShuffle(true, using: &generator)
        queue.advance(userInitiated: true)
        let playing = queue.current!.id

        queue.setShuffle(false)

        XCTAssertFalse(queue.shuffleEnabled)
        XCTAssertEqual(queue.current?.id, playing)
        let original = ["a", "b", "c", "d"]
        let index = original.firstIndex(of: playing)!
        XCTAssertEqual(queue.upcoming.map(\.id), Array(original.dropFirst(index + 1)))
    }

    func testRepeatModeCyclesOffAllOne() {
        XCTAssertEqual(RepeatMode.off.next, .all)
        XCTAssertEqual(RepeatMode.all.next, .one)
        XCTAssertEqual(RepeatMode.one.next, .off)
    }
}
