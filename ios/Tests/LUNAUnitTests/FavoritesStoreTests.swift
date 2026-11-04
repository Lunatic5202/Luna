import XCTest
@testable import LUNA

@MainActor
final class FavoritesStoreTests: XCTestCase {
    private func tempFile() -> URL {
        FileManager.default.temporaryDirectory
            .appendingPathComponent("luna-favorites-\(UUID().uuidString).json")
    }

    func testToggleAddsThenRemoves() {
        let store = FavoritesStore(fileURL: nil)
        store.toggle("a")
        XCTAssertTrue(store.contains("a"))
        store.toggle("a")
        XCTAssertFalse(store.contains("a"))
    }

    func testForgetOnlyAffectsThatTrack() {
        let store = FavoritesStore(fileURL: nil)
        store.toggle("a")
        store.toggle("b")
        store.forget("a")
        store.forget("never-hearted")
        XCTAssertEqual(store.ids, ["b"])
    }

    func testFavoritesSurviveRelaunch() {
        let file = tempFile()
        defer { try? FileManager.default.removeItem(at: file) }
        let first = FavoritesStore(fileURL: file)
        first.toggle("a")
        first.toggle("b")
        first.toggle("a")
        XCTAssertEqual(FavoritesStore(fileURL: file).ids, ["b"])
    }

    func testCorruptFileMeansNoFavorites() throws {
        let file = tempFile()
        defer { try? FileManager.default.removeItem(at: file) }
        try Data("{oops".utf8).write(to: file)
        XCTAssertTrue(FavoritesStore(fileURL: file).ids.isEmpty)
    }

    func testMissingFileMeansNoFavorites() {
        XCTAssertTrue(FavoritesStore(fileURL: tempFile()).ids.isEmpty)
    }
}
