import Foundation
import MediaPlayer
import UIKit

/// What the lock screen and Control Center show. A protocol so the coordinator can be tested.
@MainActor
protocol NowPlayingPublishing: AnyObject {
    func publish(track: Track, position: TimeInterval, duration: TimeInterval, isPlaying: Bool, artwork: UIImage?)
    func clear()
}

@MainActor
final class SystemNowPlaying: NowPlayingPublishing {
    func publish(track: Track, position: TimeInterval, duration: TimeInterval, isPlaying: Bool, artwork: UIImage?) {
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: track.displayTitle,
            MPMediaItemPropertyArtist: track.displayArtist,
            MPMediaItemPropertyAlbumTitle: track.displayAlbum,
            MPMediaItemPropertyPlaybackDuration: duration,
            // Elapsed time plus rate lets the system advance the scrubber by itself, so this
            // only needs publishing when something changes, not every tick.
            MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0,
            MPNowPlayingInfoPropertyDefaultPlaybackRate: 1.0
        ]
        if let artwork {
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: artwork.size) { _ in artwork }
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    func clear() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }
}

/// Wires lock-screen, Control Center, headset and Bluetooth buttons to the coordinator.
@MainActor
final class RemoteCommandController {
    init(coordinator: PlaybackCoordinator) {
        let center = MPRemoteCommandCenter.shared()

        center.playCommand.isEnabled = true
        center.playCommand.addTarget { [weak coordinator] _ in
            Task { @MainActor in coordinator?.resume() }
            return .success
        }

        center.pauseCommand.isEnabled = true
        center.pauseCommand.addTarget { [weak coordinator] _ in
            Task { @MainActor in coordinator?.pause() }
            return .success
        }

        center.togglePlayPauseCommand.isEnabled = true
        center.togglePlayPauseCommand.addTarget { [weak coordinator] _ in
            Task { @MainActor in coordinator?.togglePlayPause() }
            return .success
        }

        center.nextTrackCommand.isEnabled = true
        center.nextTrackCommand.addTarget { [weak coordinator] _ in
            Task { @MainActor in coordinator?.next() }
            return .success
        }

        center.previousTrackCommand.isEnabled = true
        center.previousTrackCommand.addTarget { [weak coordinator] _ in
            Task { @MainActor in coordinator?.previous() }
            return .success
        }

        center.changePlaybackPositionCommand.isEnabled = true
        center.changePlaybackPositionCommand.addTarget { [weak coordinator] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            let target = event.positionTime
            Task { @MainActor in coordinator?.seek(to: target) }
            return .success
        }
    }
}
