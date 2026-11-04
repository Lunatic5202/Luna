import Foundation

enum LyricsState: Equatable {
    case loading
    case available(text: String, synced: Bool)
    case notFound
    case failed(String)
}

protocol LyricsFetching {
    /// Never throws: the outcome is `.available`, `.notFound` or `.failed`.
    func fetch(for track: Track) async -> LyricsState
}

/// Looks lyrics up on LRCLIB (lrclib.net), a free, open lyrics database. Only the title, artist,
/// album and length are sent, and only when the user opens the lyrics screen.
struct LRCLibClient: LyricsFetching {
    typealias Loader = (URLRequest) async throws -> (Data, HTTPURLResponse)

    static let maxResponseBytes = 1_000_000
    static let userAgent = "LunaMusic/1.0 (https://github.com/Lunatic5202/Luna)"
    private static let unavailable = "The lyrics service is unavailable. Check your connection and try again."

    private enum LookupError: Error {
        case badResponse
        case server(Int)
    }

    private struct Payload {
        let text: String
        let synced: Bool
        var state: LyricsState { .available(text: text, synced: synced) }
    }

    private let load: Loader

    init(load: @escaping Loader = LRCLibClient.urlSessionLoader) {
        self.load = load
    }

    static func urlSessionLoader(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        return (data, http)
    }

    func fetch(for track: Track) async -> LyricsState {
        let title = track.title.trimmingCharacters(in: .whitespacesAndNewlines)
        let artist = track.artist.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty, !artist.isEmpty,
              artist.caseInsensitiveCompare("Unknown artist") != .orderedSame else { return .notFound }

        // The exact lookup (it also matches on length) is tried first; the broader search covers
        // songs whose metadata is slightly different from the database's.
        var exactFailed = false
        if track.duration > 0 {
            do {
                if let payload = try await exactLookup(title: title, artist: artist, track: track) {
                    return payload.state
                }
            } catch {
                exactFailed = true
            }
        }
        do {
            if let payload = try await search(title: title, artist: artist) { return payload.state }
            return exactFailed ? .failed(Self.unavailable) : .notFound
        } catch {
            return .failed(Self.unavailable)
        }
    }

    // MARK: - Requests

    private func exactLookup(title: String, artist: String, track: Track) async throws -> Payload? {
        var items = [
            ("track_name", title),
            ("artist_name", artist),
            ("duration", String(Int(track.duration.rounded())))
        ]
        let album = track.album.trimmingCharacters(in: .whitespacesAndNewlines)
        if !album.isEmpty { items.append(("album_name", album)) }

        guard let (data, status) = try await send(path: "get", items: items) else { return nil }
        guard (200..<300).contains(status) else { return nil }
        guard let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw LookupError.badResponse
        }
        return Self.payload(from: object)
    }

    private func search(title: String, artist: String) async throws -> Payload? {
        guard let (data, status) = try await send(path: "search", items: [("track_name", title), ("artist_name", artist)]),
              (200..<300).contains(status) else { return nil }
        guard let array = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            throw LookupError.badResponse
        }
        let payloads = array.compactMap(Self.payload(from:))
        return payloads.first(where: \.synced) ?? payloads.first
    }

    /// Returns nil for "not found" (404), the body and status for success, and throws for
    /// anything that means the service could not answer.
    private func send(path: String, items: [(String, String)]) async throws -> (Data, Int)? {
        guard var components = URLComponents(string: "https://lrclib.net") else { return nil }
        components.path = "/api/\(path)"
        components.percentEncodedQuery = items
            .map { "\($0.0)=\(Self.encode($0.1))" }
            .joined(separator: "&")
        guard let url = components.url else { return nil }

        var request = URLRequest(url: url, timeoutInterval: 10)
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")

        let (data, response) = try await load(request)
        if response.statusCode == 404 { return nil }
        guard (200..<300).contains(response.statusCode) else { throw LookupError.server(response.statusCode) }
        guard data.count <= Self.maxResponseBytes else { throw LookupError.badResponse }
        return (data, response.statusCode)
    }

    // MARK: - Parsing

    /// Synced lyrics win over plain ones.
    private static func payload(from object: [String: Any]) -> Payload? {
        if let synced = usableText(object["syncedLyrics"]) { return Payload(text: synced, synced: true) }
        if let plain = usableText(object["plainLyrics"]) { return Payload(text: plain, synced: false) }
        return nil
    }

    private static func usableText(_ value: Any?) -> String? {
        guard let text = (value as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !text.isEmpty, text.caseInsensitiveCompare("null") != .orderedSame else { return nil }
        return text
    }

    /// URLComponents leaves "+" and "&" alone in a query, and servers read "+" as a space, so
    /// everything but unreserved characters is escaped by hand.
    private static func encode(_ value: String) -> String {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }
}
