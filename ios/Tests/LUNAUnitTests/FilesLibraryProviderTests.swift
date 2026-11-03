import XCTest
@testable import LUNA

/// Writes a real, playable WAV so the import path (copy, AVFoundation metadata) runs for real.
private func writeSilentWAV(to url: URL, seconds: Double = 1, sampleRate: Int = 8_000) throws {
    let frames = Int(seconds * Double(sampleRate))
    var data = Data()
    func append32(_ value: UInt32) { data.append(contentsOf: withUnsafeBytes(of: value.littleEndian) { Array($0) }) }
    func append16(_ value: UInt16) { data.append(contentsOf: withUnsafeBytes(of: value.littleEndian) { Array($0) }) }

    data.append(contentsOf: Array("RIFF".utf8))
    append32(UInt32(36 + frames * 2))
    data.append(contentsOf: Array("WAVE".utf8))
    data.append(contentsOf: Array("fmt ".utf8))
    append32(16)                       // PCM header size
    append16(1)                        // PCM
    append16(1)                        // mono
    append32(UInt32(sampleRate))
    append32(UInt32(sampleRate * 2))   // byte rate
    append16(2)                        // block align
    append16(16)                       // bits per sample
    data.append(contentsOf: Array("data".utf8))
    append32(UInt32(frames * 2))
    data.append(Data(count: frames * 2))
    try data.write(to: url)
}

@MainActor
final class FilesLibraryProviderTests: XCTestCase {
    private var root: URL!
    private var libraryDirectory: URL!
    private var sourceDirectory: URL!

    override func setUpWithError() throws {
        root = FileManager.default.temporaryDirectory.appendingPathComponent("luna-tests-\(UUID().uuidString)")
        libraryDirectory = root.appendingPathComponent("Library")
        sourceDirectory = root.appendingPathComponent("Source")
        try FileManager.default.createDirectory(at: sourceDirectory, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: root)
    }

    private func audioFiles() -> [String] {
        let folder = libraryDirectory.appendingPathComponent("Audio")
        return (try? FileManager.default.contentsOfDirectory(atPath: folder.path)) ?? []
    }

    func testImportCopiesTheFileAndReadsItsDetails() async throws {
        let source = sourceDirectory.appendingPathComponent("My Song.wav")
        try writeSilentWAV(to: source, seconds: 1)
        let provider = FilesLibraryProvider(directory: libraryDirectory)

        let result = await provider.importFiles(from: [source])

        XCTAssertTrue(result.failures.isEmpty, "\(result.failures)")
        XCTAssertEqual(result.imported.count, 1)
        let track = try XCTUnwrap(result.imported.first)
        XCTAssertEqual(track.title, "My Song", "Files without tags fall back to the file name")
        XCTAssertEqual(track.source, .files)
        XCTAssertEqual(track.duration, 1, accuracy: 0.1)

        let stored = try XCTUnwrap(provider.url(for: track))
        XCTAssertTrue(FileManager.default.fileExists(atPath: stored.path))
        XCTAssertNotEqual(stored, source, "Playback uses LUNA's own copy, not the original")
        XCTAssertTrue(FileManager.default.fileExists(atPath: source.path), "The original is left alone")
    }

    func testLibrarySurvivesARelaunch() async throws {
        let source = sourceDirectory.appendingPathComponent("Persisted.wav")
        try writeSilentWAV(to: source)
        let first = FilesLibraryProvider(directory: libraryDirectory)
        let imported = await first.importFiles(from: [source]).imported

        let second = FilesLibraryProvider(directory: libraryDirectory)

        XCTAssertEqual(second.tracks, imported)
        XCTAssertNotNil(second.url(for: try XCTUnwrap(imported.first)))
    }

    func testUnplayableFilesAreRejectedAndLeaveNothingBehind() async throws {
        let source = sourceDirectory.appendingPathComponent("not-audio.mp3")
        try Data("this is just text".utf8).write(to: source)
        let provider = FilesLibraryProvider(directory: libraryDirectory)

        let result = await provider.importFiles(from: [source])

        XCTAssertTrue(result.imported.isEmpty)
        XCTAssertEqual(result.failures.count, 1)
        XCTAssertTrue(result.failures[0].hasPrefix("not-audio.mp3"))
        XCTAssertTrue(provider.tracks.isEmpty)
        XCTAssertTrue(audioFiles().isEmpty, "A rejected import must not keep a copy")
    }

    func testOneBadFileDoesNotStopTheRest() async throws {
        let bad = sourceDirectory.appendingPathComponent("bad.mp3")
        try Data("nope".utf8).write(to: bad)
        let good = sourceDirectory.appendingPathComponent("good.wav")
        try writeSilentWAV(to: good)
        let provider = FilesLibraryProvider(directory: libraryDirectory)

        let result = await provider.importFiles(from: [bad, good])

        XCTAssertEqual(result.imported.map(\.title), ["good"])
        XCTAssertEqual(result.failures.count, 1)
    }

    func testRemoveDeletesTheCopyAndTheEntry() async throws {
        let source = sourceDirectory.appendingPathComponent("Remove me.wav")
        try writeSilentWAV(to: source)
        let provider = FilesLibraryProvider(directory: libraryDirectory)
        let result = await provider.importFiles(from: [source])
        let track = try XCTUnwrap(result.imported.first)

        provider.remove(track)

        XCTAssertTrue(provider.tracks.isEmpty)
        XCTAssertNil(provider.url(for: track))
        XCTAssertTrue(audioFiles().isEmpty)
        XCTAssertTrue(FilesLibraryProvider(directory: libraryDirectory).tracks.isEmpty)
    }

    func testEntriesWhoseFileWentMissingAreDroppedOnLoad() async throws {
        let source = sourceDirectory.appendingPathComponent("Vanishing.wav")
        try writeSilentWAV(to: source)
        let provider = FilesLibraryProvider(directory: libraryDirectory)
        _ = await provider.importFiles(from: [source])
        for name in audioFiles() {
            try FileManager.default.removeItem(at: libraryDirectory.appendingPathComponent("Audio/\(name)"))
        }

        XCTAssertTrue(FilesLibraryProvider(directory: libraryDirectory).tracks.isEmpty)
    }
}
