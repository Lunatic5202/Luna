import Foundation

/// Runs a closure after a delay and returns a function that cancels it. Injected so tests can
/// fire the timer by hand instead of waiting.
typealias SleepScheduler = (_ delay: TimeInterval, _ action: @escaping @MainActor () -> Void) -> () -> Void

func systemSleepScheduler(_ delay: TimeInterval, _ action: @escaping @MainActor () -> Void) -> () -> Void {
    let task = Task { @MainActor in
        try? await Task.sleep(nanoseconds: UInt64(max(delay, 0) * 1_000_000_000))
        if !Task.isCancelled { action() }
    }
    return { task.cancel() }
}

/// Stops playback after a set time, or when the current song ends. Playback itself is not in
/// here: `onFire` (for the countdown) and `consumeEndOfTrack()` (for "end of song") tell the
/// coordinator when to stop.
@MainActor
final class SleepTimer: ObservableObject {
    enum Option: Hashable, Identifiable {
        case minutes(Int)
        case endOfTrack

        static let presets: [Option] = [.minutes(15), .minutes(30), .minutes(45), .minutes(60), .endOfTrack]

        var id: String {
            switch self {
            case .minutes(let value): return "minutes-\(value)"
            case .endOfTrack: return "end-of-track"
            }
        }

        var label: String {
            switch self {
            case .minutes(let value): return "\(value) minutes"
            case .endOfTrack: return "End of this song"
            }
        }
    }

    /// The running timer, nil when off.
    @Published private(set) var option: Option?
    /// When a countdown ends; nil for "end of song" and when off.
    @Published private(set) var endDate: Date?

    /// Called when a countdown reaches zero.
    var onFire: (() -> Void)?

    private let now: () -> Date
    private let schedule: SleepScheduler
    private var cancelScheduled: (() -> Void)?

    init(now: @escaping () -> Date = Date.init, schedule: @escaping SleepScheduler = systemSleepScheduler) {
        self.now = now
        self.schedule = schedule
    }

    /// Starting a timer replaces any running one.
    func start(_ option: Option) {
        cancel()
        self.option = option
        switch option {
        case .minutes(let value):
            let seconds = TimeInterval(max(value, 1) * 60)
            endDate = now().addingTimeInterval(seconds)
            cancelScheduled = schedule(seconds) { [weak self] in self?.fire() }
        case .endOfTrack:
            endDate = nil
        }
    }

    func cancel() {
        cancelScheduled?()
        cancelScheduled = nil
        option = nil
        endDate = nil
    }

    /// True once, when a track finishes while "end of song" is armed. The timer turns itself off.
    func consumeEndOfTrack() -> Bool {
        guard option == .endOfTrack else { return false }
        cancel()
        return true
    }

    private func fire() {
        guard case .minutes = option else { return }
        cancel()
        onFire?()
    }
}
