import Foundation

struct LyricLine: Equatable {
    /// Seconds from the start of the song.
    let time: TimeInterval
    let text: String
}

/// Reads synced lyrics in LRC format (`[mm:ss.xx] text`), the same way the Android app does.
enum LyricsParser {
    private static let timestamp = try! NSRegularExpression(pattern: #"\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]"#)
    private static let offsetTag = try! NSRegularExpression(pattern: #"\[offset:([+-]?\d+)\]"#, options: .caseInsensitive)
    private static let metaTag = try! NSRegularExpression(pattern: #"\[(?:ar|al|ti|by|offset|re|ve):[^\]]*\]"#, options: .caseInsensitive)

    /// Lines in time order. A line with several timestamps appears once per timestamp; blank
    /// lines, tag lines (`[ar:...]`) and text without a timestamp are dropped.
    static func parse(_ lrc: String) -> [LyricLine] {
        let offsetMs = substring(offsetTag.firstMatch(in: lrc, range: fullRange(lrc)), group: 1, in: lrc)
            .flatMap { Int($0) } ?? 0

        var parsed: [(order: Int, line: LyricLine)] = []
        for rawLine in lrc.components(separatedBy: .newlines) {
            let range = fullRange(rawLine)
            let matches = timestamp.matches(in: rawLine, range: range)
            guard !matches.isEmpty else { continue }

            let text = stripMeta(timestamp.stringByReplacingMatches(in: rawLine, range: range, withTemplate: ""))
                .trimmingCharacters(in: .whitespaces)
            guard !text.isEmpty else { continue }

            for match in matches {
                guard let minutes = substring(match, group: 1, in: rawLine).flatMap({ Int($0) }),
                      let seconds = substring(match, group: 2, in: rawLine).flatMap({ Int($0) }) else { continue }
                let fraction = substring(match, group: 3, in: rawLine) ?? ""
                let fractionMs: Int
                switch fraction.count {
                case 1: fractionMs = (Int(fraction) ?? 0) * 100
                case 2: fractionMs = (Int(fraction) ?? 0) * 10
                case 3: fractionMs = Int(fraction) ?? 0
                default: fractionMs = 0
                }
                let totalMs = max(0, minutes * 60_000 + seconds * 1_000 + fractionMs + offsetMs)
                parsed.append((parsed.count, LyricLine(time: Double(totalMs) / 1_000, text: text)))
            }
        }
        // Sorting is not stable on its own, so ties keep their original order explicitly.
        return parsed
            .sorted { $0.line.time != $1.line.time ? $0.line.time < $1.line.time : $0.order < $1.order }
            .map(\.line)
    }

    /// Index of the line being sung at `position`, or nil before the first line.
    static func currentLineIndex(in lines: [LyricLine], at position: TimeInterval) -> Int? {
        guard let first = lines.first, position >= first.time else { return nil }
        var low = 0
        var high = lines.count
        while low < high {
            let middle = (low + high) / 2
            if lines[middle].time <= position { low = middle + 1 } else { high = middle }
        }
        return low - 1
    }

    /// The words only: timestamps and tags removed, blank lines dropped.
    static func plainText(from lrc: String) -> String {
        lrc.components(separatedBy: .newlines)
            .map { line in
                let range = fullRange(line)
                let withoutStamps = timestamp.stringByReplacingMatches(in: line, range: range, withTemplate: "")
                return stripMeta(withoutStamps).trimmingCharacters(in: .whitespaces)
            }
            .filter { !$0.isEmpty }
            .joined(separator: "\n")
    }

    // MARK: - Helpers

    private static func stripMeta(_ text: String) -> String {
        metaTag.stringByReplacingMatches(in: text, range: fullRange(text), withTemplate: "")
    }

    private static func fullRange(_ text: String) -> NSRange {
        NSRange(text.startIndex..., in: text)
    }

    private static func substring(_ match: NSTextCheckingResult?, group: Int, in text: String) -> String? {
        guard let match, group < match.numberOfRanges else { return nil }
        let nsRange = match.range(at: group)
        guard nsRange.location != NSNotFound, let range = Range(nsRange, in: text) else { return nil }
        return String(text[range])
    }
}
