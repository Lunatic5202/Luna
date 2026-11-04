import XCTest
@testable import LUNA

@MainActor
final class ComboStoreTests: XCTestCase {
    private func track(_ id: String) -> Track { Track(id: id, title: id, source: .files) }

    private func tempFile() -> URL {
        FileManager.default.temporaryDirectory
            .appendingPathComponent("luna-combos-\(UUID().uuidString).json")
    }

    func testCreateTrimsTheNameAndDropsDuplicateTracks() {
        let store = ComboStore(fileURL: nil)
        let combo = store.create(name: "  Gym  ", trackIDs: ["a", "b", "a"])
        XCTAssertEqual(combo?.name, "Gym")
        XCTAssertEqual(combo?.trackIDs, ["a", "b"])
        XCTAssertEqual(store.combos.count, 1)
    }

    func testBlankNameIsRejected() {
        let store = ComboStore(fileURL: nil)
        XCTAssertNil(store.create(name: "   ", trackIDs: ["a"]))
        XCTAssertTrue(store.combos.isEmpty)
    }

    func testAddDoesNotDuplicateAndRemoveWorks() throws {
        let store = ComboStore(fileURL: nil)
        let id = try XCTUnwrap(store.create(name: "Mix")).id
        store.add("a", to: id)
        store.add("a", to: id)
        store.add("b", to: id)
        XCTAssertEqual(store.combos[0].trackIDs, ["a", "b"])
        store.remove("a", from: id)
        XCTAssertEqual(store.combos[0].trackIDs, ["b"])
    }

    func testAddToUnknownComboIsIgnored() {
        let store = ComboStore(fileURL: nil)
        store.add("a", to: "missing")
        XCTAssertTrue(store.combos.isEmpty)
    }

    func testRenameAndDelete() throws {
        let store = ComboStore(fileURL: nil)
        let id = try XCTUnwrap(store.create(name: "Old")).id
        store.rename(id, to: " New ")
        XCTAssertEqual(store.combos[0].name, "New")
        store.rename(id, to: "  ")
        XCTAssertEqual(store.combos[0].name, "New")
        store.delete(id)
        XCTAssertTrue(store.combos.isEmpty)
    }

    func testForgetRemovesATrackFromEveryCombo() {
        let store = ComboStore(fileURL: nil)
        store.create(name: "One", trackIDs: ["a", "b"])
        store.create(name: "Two", trackIDs: ["a", "c"])
        store.forget("a")
        XCTAssertEqual(store.combos.map(\.trackIDs), [["b"], ["c"]])
    }

    func testTracksKeepTheComboOrderAndSkipMissingOnes() throws {
        let store = ComboStore(fileURL: nil)
        let combo = try XCTUnwrap(store.create(name: "Mix", trackIDs: ["c", "gone", "a"]))
        let resolved = store.tracks(in: combo, from: [track("a"), track("b"), track("c")])
        XCTAssertEqual(resolved.map(\.id), ["c", "a"])
    }

    func testCombosSurviveRelaunch() throws {
        let file = tempFile()
        defer { try? FileManager.default.removeItem(at: file) }
        let first = ComboStore(fileURL: file)
        let id = try XCTUnwrap(first.create(name: "Road trip", trackIDs: ["a", "b"])).id
        first.add("c", to: id)

        let second = ComboStore(fileURL: file)
        XCTAssertEqual(second.combos, first.combos)
        XCTAssertEqual(second.combos.first?.trackIDs, ["a", "b", "c"])
    }

    func testCorruptFileMeansNoCombos() throws {
        let file = tempFile()
        defer { try? FileManager.default.removeItem(at: file) }
        try Data("not json".utf8).write(to: file)
        XCTAssertTrue(ComboStore(fileURL: file).combos.isEmpty)
    }

    func testMissingFileMeansNoCombos() {
        XCTAssertTrue(ComboStore(fileURL: tempFile()).combos.isEmpty)
    }
}
