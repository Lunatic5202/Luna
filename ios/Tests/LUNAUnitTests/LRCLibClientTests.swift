import XCTest
@testable import LUNA

/// Hands out scripted responses in order and records the requests. Status -1 means "network error".
private final class ScriptedLoader {
    private(set) var requests: [URLRequest] = []
    private var responses: [(Int, String)]

    init(_ responses: [(Int, String)]) { self.responses = responses }

    var loader: LRCLibClient.Loader {
        { [self] request in
            requests.append(request)
            let (status, body) = responses.removeFirst()
            if status < 0 { throw URLError(.notConnectedToInternet) }
            let http = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!
            return (Data(body.utf8), http)
        }
    }
}

final class LRCLibClientTests: XCTestCase {
    private func song(
        title: String = "Song", artist: String = "Band", album: String = "Rec", duration: TimeInterval = 200.4
    ) -> Track {
        Track(id: "1", title: title, artist: artist, album: album, duration: duration, source: .files)
    }

    private func fetch(_ track: Track, _ script: [(Int, String)]) async -> (LyricsState, ScriptedLoader) {
        let scripted = ScriptedLoader(script)
        let state = await LRCLibClient(load: scripted.loader).fetch(for: track)
        return (state, scripted)
    }

    private func query(_ request: URLRequest) -> [String: String] {
        let items = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems ?? []
        return Dictionary(uniqueKeysWithValues: items.map { ($0.name, $0.value ?? "") })
    }

    func testExactLookupPrefersSyncedLyrics() async {
        let body = #"{"syncedLyrics":"[00:01.00] a","plainLyrics":"a"}"#
        let (state, scripted) = await fetch(song(), [(200, body)])
        XCTAssertEqual(state, .available(text: "[00:01.00] a", synced: true))
        XCTAssertEqual(scripted.requests.count, 1)
        let request = scripted.requests[0]
        XCTAssertEqual(request.url?.path, "/api/get")
        XCTAssertEqual(query(request), ["track_name": "Song", "artist_name": "Band", "album_name": "Rec", "duration": "200"])
        XCTAssertEqual(request.value(forHTTPHeaderField: "User-Agent"), LRCLibClient.userAgent)
    }

    func testPlainLyricsWhenThereAreNoSyncedOnes() async {
        let (state, _) = await fetch(song(), [(200, #"{"syncedLyrics":null,"plainLyrics":"la la"}"#)])
        XCTAssertEqual(state, .available(text: "la la", synced: false))
    }

    func testFallsBackToSearchAndPrefersSyncedResult() async {
        let results = #"[{"plainLyrics":"x"},{"syncedLyrics":"[00:01.00] y"}]"#
        let (state, scripted) = await fetch(song(), [(404, ""), (200, results)])
        XCTAssertEqual(state, .available(text: "[00:01.00] y", synced: true))
        XCTAssertEqual(scripted.requests.map { $0.url?.path }, ["/api/get", "/api/search"])
    }

    func testNotFoundWhenNothingMatches() async {
        let (state, _) = await fetch(song(), [(404, ""), (404, "")])
        XCTAssertEqual(state, .notFound)
    }

    func testEmptyLyricsFieldsAreNotFound() async {
        let (state, _) = await fetch(song(), [(200, #"{"syncedLyrics":"","plainLyrics":"null"}"#), (200, "[]")])
        XCTAssertEqual(state, .notFound)
    }

    func testServerErrorsOnBothLookupsFail() async {
        let (state, _) = await fetch(song(), [(500, ""), (500, "")])
        guard case .failed = state else { return XCTFail("expected failure, got \(state)") }
    }

    func testNetworkErrorThenEmptySearchIsAFailureNotNotFound() async {
        let (state, _) = await fetch(song(), [(-1, ""), (404, "")])
        guard case .failed = state else { return XCTFail("expected failure, got \(state)") }
    }

    func testFailedExactLookupStillUsesSearch() async {
        let (state, _) = await fetch(song(), [(500, ""), (200, #"[{"plainLyrics":"found"}]"#)])
        XCTAssertEqual(state, .available(text: "found", synced: false))
    }

    func testUnreadableAnswerCountsAsAFailure() async {
        let (state, _) = await fetch(song(), [(200, "not json"), (404, "")])
        guard case .failed = state else { return XCTFail("expected failure, got \(state)") }
    }

    func testSongsWithoutAUsableArtistMakeNoRequest() async {
        for artist in ["", "  ", "Unknown artist", "unknown ARTIST"] {
            let (state, scripted) = await fetch(song(artist: artist), [])
            XCTAssertEqual(state, .notFound, artist)
            XCTAssertTrue(scripted.requests.isEmpty, artist)
        }
    }

    func testUnknownLengthSkipsTheExactLookup() async {
        let (state, scripted) = await fetch(song(duration: 0), [(404, "")])
        XCTAssertEqual(state, .notFound)
        XCTAssertEqual(scripted.requests.map { $0.url?.path }, ["/api/search"])
    }

    func testBlankAlbumIsLeftOut() async {
        let (_, scripted) = await fetch(song(album: "  "), [(404, ""), (404, "")])
        XCTAssertNil(query(scripted.requests[0])["album_name"])
    }

    func testSpecialCharactersAreEscapedSoPlusIsNotASpace() async {
        let (_, scripted) = await fetch(song(title: "AC+DC & Co"), [(404, ""), (404, "")])
        let url = scripted.requests[0].url?.absoluteString ?? ""
        XCTAssertTrue(url.contains("track_name=AC%2BDC%20%26%20Co"), url)
    }
}
