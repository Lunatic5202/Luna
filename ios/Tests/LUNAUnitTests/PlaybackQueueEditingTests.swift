import XCTest
@testable import LUNA

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

private func track(_ id: String) -> Track { Track(id: id, title: id.uppercased(), source: .files) }
private func tracks(_ ids: [String]) -> [Track] { ids.map(track) }
private func ids(_ list: [Track]) -> [String] { list.map(\.id) }

final class PlaybackQueueEditingTests: XCTestCase {
    /// a, [b], c, d, e
    private func queue(shuffle: Bool = false) -> PlaybackQueue {
        PlaybackQueue(tracks: tracks(["a", "b", "c", "d", "e"]), startIndex: 1)
    }

    func testRemoveUpcomingKeepsCurrent() {
        var q = queue()
        q.removeUpcoming(at: IndexSet(integer: 1))
        XCTAssertEqual(q.current?.id, "b")
        XCTAssertEqual(ids(q.upcoming), ["c", "e"])
    }

    func testRemoveSeveralUpcoming() {
        var q = queue()
        q.removeUpcoming(at: IndexSet([0, 2]))
        XCTAssertEqual(ids(q.upcoming), ["d"])
        XCTAssertEqual(q.count, 3)
    }

    func testRemoveOutOfRangeIsIgnored() {
        var q = queue()
        q.removeUpcoming(at: IndexSet(integer: 9))
        XCTAssertEqual(ids(q.upcoming), ["c", "d", "e"])
    }

    func testClearUpcomingStopsAtTheCurrentTrack() {
        var q = queue()
        q.clearUpcoming()
        XCTAssertEqual(q.current?.id, "b")
        XCTAssertTrue(q.upcoming.isEmpty)
        XCTAssertNil(q.advance(userInitiated: true))
    }

    func testMoveToTheEnd() {
        var q = queue()
        q.moveUpcoming(from: IndexSet(integer: 0), to: 3)
        XCTAssertEqual(ids(q.upcoming), ["d", "e", "c"])
    }

    func testMoveToTheFront() {
        var q = queue()
        q.moveUpcoming(from: IndexSet(integer: 2), to: 0)
        XCTAssertEqual(ids(q.upcoming), ["e", "c", "d"])
    }

    func testMoveSeveralAtOnce() {
        var q = queue()
        q.moveUpcoming(from: IndexSet([0, 1]), to: 3)
        XCTAssertEqual(ids(q.upcoming), ["e", "c", "d"])
    }

    func testPlaybackFollowsTheNewOrder() {
        var q = queue()
        q.moveUpcoming(from: IndexSet(integer: 2), to: 0)
        XCTAssertEqual(q.advance(userInitiated: true)?.id, "e")
        XCTAssertEqual(q.advance(userInitiated: true)?.id, "c")
    }

    func testManualOrderSurvivesShuffleOnAndOff() {
        var q = queue()
        q.moveUpcoming(from: IndexSet(integer: 2), to: 0)
        var generator = SeededGenerator(state: 7)
        q.setShuffle(true, using: &generator)
        q.setShuffle(false, using: &generator)
        XCTAssertEqual(q.current?.id, "b")
        XCTAssertEqual(ids(q.upcoming), ["e", "c", "d"])
    }

    func testPlayNextInsertsFirst() {
        var q = queue()
        q.insertUpcoming(track("x"), at: 0)
        XCTAssertEqual(ids(q.upcoming), ["x", "c", "d", "e"])
    }

    func testAddToQueueAppends() {
        var q = queue()
        q.insertUpcoming(track("x"))
        XCTAssertEqual(ids(q.upcoming), ["c", "d", "e", "x"])
    }

    func testInsertingAnAlreadyQueuedTrackMovesIt() {
        var q = queue()
        q.insertUpcoming(track("e"), at: 0)
        XCTAssertEqual(ids(q.upcoming), ["e", "c", "d"])
        XCTAssertEqual(q.count, 4)
    }

    func testInsertingTheCurrentTrackDoesNothing() {
        var q = queue()
        q.insertUpcoming(track("b"), at: 0)
        XCTAssertEqual(ids(q.upcoming), ["c", "d", "e"])
        XCTAssertEqual(q.count, 5)
    }

    func testEditingWhileShuffledLeavesTheRestAlone() {
        var q = queue()
        var generator = SeededGenerator(state: 42)
        q.setShuffle(true, using: &generator)
        let before = ids(q.upcoming)
        q.removeUpcoming(at: IndexSet(integer: 0))
        XCTAssertEqual(q.current?.id, "b")
        XCTAssertEqual(ids(q.upcoming), Array(before.dropFirst()))
    }

    func testInsertWhileShuffledPlaysNext() {
        var q = queue()
        var generator = SeededGenerator(state: 3)
        q.setShuffle(true, using: &generator)
        q.insertUpcoming(track("x"), at: 0)
        XCTAssertEqual(q.advance(userInitiated: true)?.id, "x")
    }

    func testJumpToUpcomingMovesForwardOnly() {
        var q = queue()
        XCTAssertEqual(q.jumpToUpcoming(id: "d")?.id, "d")
        XCTAssertEqual(ids(q.upcoming), ["e"])
        XCTAssertNil(q.jumpToUpcoming(id: "a"))
        XCTAssertEqual(q.current?.id, "d")
    }

    func testEditingAnEmptyQueueIsSafe() {
        var q = PlaybackQueue(tracks: [])
        q.removeUpcoming(at: IndexSet(integer: 0))
        q.moveUpcoming(from: IndexSet(integer: 0), to: 1)
        q.insertUpcoming(track("x"))
        q.clearUpcoming()
        XCTAssertNil(q.current)
        XCTAssertTrue(q.upcoming.isEmpty)
    }
}
