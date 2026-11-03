import XCTest
@testable import LUNA

final class TrackTests: XCTestCase {
    func testBlankMetadataFallsBackToReadableLabels() {
        let track = Track(id: "1", title: "  ", artist: "", album: "\n", source: .files)
        XCTAssertEqual(track.displayTitle, "Unknown track")
        XCTAssertEqual(track.displayArtist, "Unknown artist")
        XCTAssertEqual(track.displayAlbum, "Unknown album")
    }

    func testPresentMetadataIsKept() {
        let track = Track(id: "2", title: "Believer", artist: "Imagine Dragons", album: "Evolve", source: .mediaLibrary)
        XCTAssertEqual(track.displayTitle, "Believer")
        XCTAssertEqual(track.displayArtist, "Imagine Dragons")
        XCTAssertEqual(track.displayAlbum, "Evolve")
    }

    func testCodableRoundTripKeepsIdentityAndSource() throws {
        let original = Track(
            id: "abc",
            title: "Song",
            artist: "Artist",
            duration: 204,
            source: .files,
            dateAdded: Date(timeIntervalSince1970: 1_700_000_000)
        )
        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(Track.self, from: data)
        XCTAssertEqual(decoded, original)
        XCTAssertEqual(decoded.source, .files)
    }
}
