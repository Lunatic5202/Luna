import AVFoundation
import Foundation

/// The audio output behind the coordinator. A protocol so queue and coordinator logic can be
/// tested with a fake, and so a second engine (for DSP, or synced-library items) can slot in later.
@MainActor
protocol PlaybackEngine: AnyObject {
    /// Called with the playhead position, roughly twice a second while playing.
    var onTimeUpdate: ((TimeInterval) -> Void)? { get set }
    /// Called when the loaded item plays to its end.
    var onFinish: (() -> Void)? { get set }
    var currentTime: TimeInterval { get }
    /// Length of the loaded item, 0 while unknown.
    var duration: TimeInterval { get }

    func load(url: URL)
    func play()
    func pause()
    func seek(to time: TimeInterval)
}

/// AVPlayer-backed engine for app-owned files.
@MainActor
final class AVPlayerEngine: PlaybackEngine {
    var onTimeUpdate: ((TimeInterval) -> Void)?
    var onFinish: (() -> Void)?

    private let player = AVPlayer()
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?

    init() {
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            let seconds = time.seconds
            Task { @MainActor in
                self?.onTimeUpdate?(seconds.isFinite ? seconds : 0)
            }
        }
    }

    var currentTime: TimeInterval {
        let seconds = player.currentTime().seconds
        return seconds.isFinite ? seconds : 0
    }

    var duration: TimeInterval {
        let seconds = player.currentItem?.duration.seconds ?? 0
        return seconds.isFinite ? seconds : 0
    }

    func load(url: URL) {
        if let endObserver {
            NotificationCenter.default.removeObserver(endObserver)
        }
        let item = AVPlayerItem(url: url)
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.onFinish?()
            }
        }
        player.replaceCurrentItem(with: item)
    }

    func play() { player.play() }

    func pause() { player.pause() }

    func seek(to time: TimeInterval) {
        player.seek(
            to: CMTime(seconds: max(time, 0), preferredTimescale: 600),
            toleranceBefore: .zero,
            toleranceAfter: .zero
        )
    }
}
