import XCTest
@testable import LUNA

final class LyricsParserTests: XCTestCase {
    func testParsesTimestampsAndText() {
        let lines = LyricsParser.parse("[00:12.50] Hello\n[01:02.5] World")
        XCTAssertEqual(lines, [LyricLine(time: 12.5, text: "Hello"), LyricLine(time: 62.5, text: "World")])
    }

    func testFractionLengthsAndColonSeparator() {
        let lines = LyricsParser.parse("[00:01.250] A\n[00:02:30] B\n[00:05] C")
        XCTAssertEqual(lines.map(\.time), [1.25, 2.3, 5])
    }

    func testSeveralTimestampsOnOneLine() {
        let lines = LyricsParser.parse("[00:10.00][00:20.00] Chorus")
        XCTAssertEqual(lines, [LyricLine(time: 10, text: "Chorus"), LyricLine(time: 20, text: "Chorus")])
    }

    func testOffsetShiftsEveryLineAndNeverGoesNegative() {
        XCTAssertEqual(LyricsParser.parse("[offset:500]\n[00:01.00] A").first?.time, 1.5)
        XCTAssertEqual(LyricsParser.parse("[offset:-5000]\n[00:01.00] A").first?.time, 0)
    }

    func testTagLinesAndBlankTimedLinesAreDropped() {
        let lrc = "[ar:Artist]\n[ti:Song]\n[00:01.00]\n[00:02.00] Real line"
        XCTAssertEqual(LyricsParser.parse(lrc), [LyricLine(time: 2, text: "Real line")])
    }

    func testLinesComeBackInTimeOrder() {
        let lines = LyricsParser.parse("[00:20.00] Late\n[00:05.00] Early")
        XCTAssertEqual(lines.map(\.text), ["Early", "Late"])
    }

    func testTextWithoutTimestampsGivesNoLines() {
        XCTAssertTrue(LyricsParser.parse("just some words\nand more").isEmpty)
    }

    func testCurrentLineIndex() {
        let lines = [LyricLine(time: 5, text: "a"), LyricLine(time: 10, text: "b"), LyricLine(time: 15, text: "c")]
        XCTAssertNil(LyricsParser.currentLineIndex(in: lines, at: 0))
        XCTAssertEqual(LyricsParser.currentLineIndex(in: lines, at: 5), 0)
        XCTAssertEqual(LyricsParser.currentLineIndex(in: lines, at: 9.9), 0)
        XCTAssertEqual(LyricsParser.currentLineIndex(in: lines, at: 10), 1)
        XCTAssertEqual(LyricsParser.currentLineIndex(in: lines, at: 100), 2)
        XCTAssertNil(LyricsParser.currentLineIndex(in: [], at: 3))
    }

    func testPlainTextStripsTimestampsAndTags() {
        let lrc = "[ar:Artist]\n[00:01.00] First\n\n[00:02.00][00:03.00] Second"
        XCTAssertEqual(LyricsParser.plainText(from: lrc), "First\nSecond")
    }
}
