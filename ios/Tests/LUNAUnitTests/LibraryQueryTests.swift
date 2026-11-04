import XCTest
@testable import LUNA

final class LibraryQueryTests: XCTestCase {
    private func track(
        _ id: String, _ title: String, artist: String = "", album: String = "", added: TimeInterval = 0
    ) -> Track {
        Track(id: id, title: title, artist: artist, album: album, source: .files,
              dateAdded: Date(timeIntervalSince1970: added))
    }

    private var sample: [Track] {
        [
            track("1", "Blinding Lights", artist: "The Weeknd", album: "After Hours", added: 30),
            track("2", "Save Your Tears", artist: "The Weeknd", album: "After Hours", added: 20),
            track("3", "Café del Mar", artist: "Energy 52", album: "Singles", added: 10),
            track("4", "Untitled", artist: "", album: "", added: 40)
        ]
    }

    func testEmptySearchReturnsEverything() {
        XCTAssertEqual(LibraryQuery.filter(sample, search: "  ").count, 4)
    }

    func testSearchIgnoresCaseAndAccents() {
        XCTAssertEqual(LibraryQuery.filter(sample, search: "CAFE").map(\.id), ["3"])
    }

    func testSearchMatchesArtistAndAlbum() {
        XCTAssertEqual(LibraryQuery.filter(sample, search: "weeknd").map(\.id), ["1", "2"])
        XCTAssertEqual(LibraryQuery.filter(sample, search: "singles").map(\.id), ["3"])
    }

    func testEveryWordMustMatch() {
        XCTAssertEqual(LibraryQuery.filter(sample, search: "weeknd tears").map(\.id), ["2"])
        XCTAssertTrue(LibraryQuery.filter(sample, search: "weeknd mar").isEmpty)
    }

    func testSortByTitle() {
        XCTAssertEqual(LibraryQuery.sort(sample, by: .title).map(\.id), ["1", "3", "2", "4"])
    }

    func testSortByRecentlyAddedIsNewestFirst() {
        XCTAssertEqual(LibraryQuery.sort(sample, by: .recentlyAdded).map(\.id), ["4", "1", "2", "3"])
    }

    func testSortByArtistThenTitle() {
        // "Energy 52" < "The Weeknd" < "Unknown artist"
        XCTAssertEqual(LibraryQuery.sort(sample, by: .artist).map(\.id), ["3", "1", "2", "4"])
    }

    func testSortIsDeterministicForEqualTitles() {
        let twins = [track("b", "Same"), track("a", "Same")]
        XCTAssertEqual(LibraryQuery.sort(twins, by: .title).map(\.id), ["a", "b"])
    }

    func testAlbumGroups() {
        let groups = LibraryQuery.groups(from: sample, by: .albums)
        XCTAssertEqual(groups.map(\.title), ["After Hours", "Singles", "Unknown album"])
        XCTAssertEqual(groups.first?.subtitle, "The Weeknd")
        XCTAssertEqual(groups.first?.tracks.map(\.id), ["1", "2"])
    }

    func testAlbumWithSeveralArtistsIsVarious() {
        let tracks = [track("1", "A", artist: "X", album: "Mix"), track("2", "B", artist: "Y", album: "Mix")]
        XCTAssertEqual(LibraryQuery.groups(from: tracks, by: .albums).first?.subtitle, "Various artists")
    }

    func testArtistGroupsCountSongs() {
        let groups = LibraryQuery.groups(from: sample, by: .artists)
        XCTAssertEqual(groups.map(\.title), ["Energy 52", "The Weeknd", "Unknown artist"])
        XCTAssertEqual(groups.map(\.subtitle), ["1 song", "2 songs", "1 song"])
    }

    func testGroupingIsCaseInsensitive() {
        let tracks = [track("1", "A", artist: "Daft Punk"), track("2", "B", artist: "daft punk")]
        XCTAssertEqual(LibraryQuery.groups(from: tracks, by: .artists).count, 1)
    }

    func testSongsHaveNoGroups() {
        XCTAssertTrue(LibraryQuery.groups(from: sample, by: .songs).isEmpty)
    }

    func testCombosAreNotDerivedFromMetadata() {
        XCTAssertTrue(LibraryQuery.groups(from: sample, by: .combos).isEmpty)
    }
}
