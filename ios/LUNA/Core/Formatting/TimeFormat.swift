import Foundation

enum TimeFormat {
    /// "3:24" for 204 seconds. Negative or non-finite input shows as "0:00".
    static func clock(_ seconds: TimeInterval) -> String {
        guard seconds.isFinite, seconds > 0 else { return "0:00" }
        let total = Int(seconds)
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}
