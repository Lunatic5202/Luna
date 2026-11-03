import AVFoundation
import Foundation

/// Owns the shared AVAudioSession: category, activation, interruptions (calls, Siri, alarms)
/// and route changes (headphones unplugged). It only reports events; the coordinator decides
/// what to do with them.
@MainActor
final class AudioSessionController {
    var onInterruptionBegan: (() -> Void)?
    /// The flag says whether the system suggests resuming playback.
    var onInterruptionEnded: ((Bool) -> Void)?
    /// The previous output (headphones, Bluetooth) went away; playback should pause.
    var onRouteLost: (() -> Void)?

    private var observers: [NSObjectProtocol] = []

    init() {
        let center = NotificationCenter.default

        observers.append(center.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: nil,
            queue: .main
        ) { [weak self] note in
            let typeValue = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt
            let optionValue = note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
            Task { @MainActor in
                self?.handleInterruption(typeValue: typeValue, optionValue: optionValue)
            }
        })

        observers.append(center.addObserver(
            forName: AVAudioSession.routeChangeNotification,
            object: nil,
            queue: .main
        ) { [weak self] note in
            let reasonValue = note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt
            Task { @MainActor in
                self?.handleRouteChange(reasonValue: reasonValue)
            }
        })
    }

    /// Configures the playback category and activates the session. Safe to call repeatedly.
    func activate() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .default)
            try session.setActive(true)
        } catch {
            // Playback can still work in the foreground; background and lock-screen
            // behaviour needs the session, so this is worth logging once we have logging.
        }
    }

    private func handleInterruption(typeValue: UInt?, optionValue: UInt) {
        guard let typeValue, let type = AVAudioSession.InterruptionType(rawValue: typeValue) else { return }
        switch type {
        case .began:
            onInterruptionBegan?()
        case .ended:
            let options = AVAudioSession.InterruptionOptions(rawValue: optionValue)
            onInterruptionEnded?(options.contains(.shouldResume))
        @unknown default:
            break
        }
    }

    private func handleRouteChange(reasonValue: UInt?) {
        guard let reasonValue,
              let reason = AVAudioSession.RouteChangeReason(rawValue: reasonValue),
              reason == .oldDeviceUnavailable else { return }
        onRouteLost?()
    }
}
