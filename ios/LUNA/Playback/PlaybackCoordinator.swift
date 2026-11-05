import Foundation
import UIKit

/// The playhead, kept apart from the rest of the player state. It changes every half second,
/// so only the views that draw it (the slider, the time labels) observe it; everything else
/// stays still instead of re-rendering with every tick.
@MainActor
final class PlaybackProgress: ObservableObject {
    @Published var position: TimeInterval = 0
    @Published var duration: TimeInterval = 0
}

/// The single UI-facing playback state: current track, queue, shuffle/repeat, transport
/// controls. Audio output lives behind `PlaybackEngine`, lock-screen info behind
/// `NowPlayingPublishing`.
@MainActor
final class PlaybackCoordinator: ObservableObject {
    /// Seconds of real listening before a song counts as played (shorter songs: half their length).
    static let playCountThreshold: TimeInterval = 20

    /// Previous restarts the current track when it is further in than this, like most players.
    static let restartThreshold: TimeInterval = 3

    @Published private(set) var currentTrack: Track?
    @Published private(set) var isPlaying = false
    @Published private(set) var shuffleEnabled = false
    @Published private(set) var repeatMode: RepeatMode = .off
    @Published private(set) var errorMessage: String?
    /// Tracks after the current one, in playback order. Drives the queue sheet.
    @Published private(set) var upcoming: [Track] = []

    let progress = PlaybackProgress()

    /// Called once per play, after the listening threshold. Wired to the play-history store.
    var onPlayCounted: ((Track) -> Void)?
    private var listened: TimeInterval = 0
    private var lastTick: TimeInterval?
    private var countedCurrentPlay = false

    private var queue = PlaybackQueue(tracks: []) {
        didSet { publishUpcoming() }
    }
    private let engine: PlaybackEngine
    private let resolveURL: (Track) -> URL?
    private let nowPlaying: NowPlayingPublishing?
    private let activateSession: () -> Void
    private let loadArtwork: (Track) async -> UIImage?

    /// Stops playback after a countdown or at the end of the current song.
    let sleepTimer: SleepTimer

    private var resumeAfterInterruption = false
    private var nowPlayingArtwork: (trackID: String, image: UIImage)?

    init(
        engine: PlaybackEngine,
        resolveURL: @escaping (Track) -> URL?,
        nowPlaying: NowPlayingPublishing? = nil,
        activateSession: @escaping () -> Void = {},
        loadArtwork: @escaping (Track) async -> UIImage? = { _ in nil },
        sleepTimer: SleepTimer? = nil
    ) {
        self.engine = engine
        self.resolveURL = resolveURL
        self.nowPlaying = nowPlaying
        self.activateSession = activateSession
        self.loadArtwork = loadArtwork
        self.sleepTimer = sleepTimer ?? SleepTimer()

        engine.onTimeUpdate = { [weak self] time in
            self?.handleTimeUpdate(time)
        }
        engine.onFinish = { [weak self] in
            self?.handleFinished()
        }
        self.sleepTimer.onFire = { [weak self] in
            self?.pause()
        }
    }

    // MARK: - Transport

    /// Starts playing `track`, with `tracks` as the queue.
    func play(tracks: [Track], startingAt track: Track) {
        guard let index = tracks.firstIndex(where: { $0.id == track.id }) else { return }
        // A leftover "end of song" from a session that has since stopped must not catch this one.
        if currentTrack == nil { sleepTimer.cancel() }
        queue = PlaybackQueue(tracks: tracks, startIndex: index, shuffle: shuffleEnabled, repeatMode: repeatMode)
        loadCurrent(autoplay: true)
    }

    func resume() {
        guard currentTrack != nil else { return }
        activateSession()
        engine.play()
        isPlaying = true
        publishNowPlaying()
    }

    func pause() {
        guard currentTrack != nil else { return }
        engine.pause()
        isPlaying = false
        publishNowPlaying()
    }

    func togglePlayPause() {
        if isPlaying { pause() } else { resume() }
    }

    func next() {
        guard queue.current != nil else { return }
        if queue.advance(userInitiated: true) != nil {
            loadCurrent(autoplay: true)
        }
    }

    func previous() {
        guard let before = queue.current else { return }
        if progress.position > Self.restartThreshold {
            restartCurrent()
            return
        }
        queue.goBack()
        if queue.current?.id == before.id {
            restartCurrent()
        } else {
            loadCurrent(autoplay: true)
        }
    }

    func seek(to time: TimeInterval) {
        guard currentTrack != nil else { return }
        let upper = progress.duration > 0 ? progress.duration : max(time, 0)
        let target = min(max(time, 0), upper)
        engine.seek(to: target)
        progress.position = target
        publishNowPlaying()
    }

    func toggleShuffle() {
        shuffleEnabled.toggle()
        queue.setShuffle(shuffleEnabled)
    }

    func cycleRepeat() {
        repeatMode = repeatMode.next
        queue.repeatMode = repeatMode
    }

    // MARK: - Queue editing

    func jumpToUpcoming(_ id: String) {
        guard queue.jumpToUpcoming(id: id) != nil else { return }
        loadCurrent(autoplay: true)
    }

    func removeUpcoming(at offsets: IndexSet) { queue.removeUpcoming(at: offsets) }
    func moveUpcoming(from source: IndexSet, to destination: Int) { queue.moveUpcoming(from: source, to: destination) }
    func clearUpcoming() { queue.clearUpcoming() }
    func playNext(_ track: Track) { enqueue(track, at: 0) }
    func addToQueue(_ track: Track) { enqueue(track, at: nil) }

    private func enqueue(_ track: Track, at offset: Int?) {
        guard currentTrack != nil else {
            play(tracks: [track], startingAt: track)
            return
        }
        queue.insertUpcoming(track, at: offset)
    }

    private func publishUpcoming() {
        let next = queue.upcoming
        if next != upcoming { upcoming = next }
    }

    func stop() {
        engine.pause()
        isPlaying = false
        currentTrack = nil
        queue = PlaybackQueue(tracks: [], shuffle: false, repeatMode: repeatMode)
        progress.position = 0
        progress.duration = 0
        nowPlayingArtwork = nil
        nowPlaying?.clear()
    }

    /// Call after a track is deleted from the library. If it is the one playing, stop; if it is
    /// only queued, it is skipped when its turn comes because its file no longer resolves.
    func clearError() { errorMessage = nil }

    func trackWasRemoved(_ id: String) {
        if currentTrack?.id == id { stop() }
    }

    // MARK: - System events

    func interruptionBegan() {
        resumeAfterInterruption = isPlaying
        if isPlaying { pause() }
    }

    func interruptionEnded(shouldResume: Bool) {
        defer { resumeAfterInterruption = false }
        if shouldResume && resumeAfterInterruption { resume() }
    }

    func routeLost() {
        if isPlaying { pause() }
    }

    // MARK: - Internals

    private func loadCurrent(autoplay: Bool) {
        var skipped = 0
        while let track = queue.current {
            if let url = resolveURL(track) {
                errorMessage = nil
                activateSession()
                currentTrack = track
                resetPlayCounting()
                engine.load(url: url)
                progress.position = 0
                progress.duration = track.duration
                if autoplay { engine.play() }
                isPlaying = autoplay
                requestArtwork(for: track)
                publishNowPlaying()
                return
            }
            // The file is gone (deleted, or storage was cleared): move on to the next one.
            skipped += 1
            guard skipped < queue.count, queue.advance(userInitiated: true) != nil else { break }
        }
        stop()
        errorMessage = "That song is no longer available."
    }

    /// "End of this song": settle on the next track, paused, instead of playing on.
    private func stopAfterSleepTimer() {
        engine.pause()
        isPlaying = false
        if queue.advance(userInitiated: false) != nil {
            loadCurrent(autoplay: false)
        } else {
            engine.seek(to: 0)
            progress.position = 0
            publishNowPlaying()
        }
    }

    private func restartCurrent() {
        lastTick = nil
        engine.seek(to: 0)
        progress.position = 0
        publishNowPlaying()
    }

    private func resetPlayCounting() {
        listened = 0
        lastTick = nil
        countedCurrentPlay = false
    }

    /// Counts only continuous listening: a jump (seeking ahead) is not progress.
    private func countListening(upTo time: TimeInterval) {
        defer { lastTick = time }
        guard !countedCurrentPlay, isPlaying, let track = currentTrack,
              let last = lastTick, time > last, time - last <= 1.5 else { return }
        listened += time - last
        let threshold = track.duration > 0
            ? min(Self.playCountThreshold, track.duration / 2)
            : Self.playCountThreshold
        if listened >= threshold {
            countedCurrentPlay = true
            onPlayCounted?(track)
        }
    }

    private func handleTimeUpdate(_ time: TimeInterval) {
        progress.position = time
        countListening(upTo: time)
        if progress.duration <= 0 {
            let known = engine.duration
            if known > 0 {
                progress.duration = known
                publishNowPlaying()
            }
        }
    }

    private func handleFinished() {
        if sleepTimer.consumeEndOfTrack() {
            stopAfterSleepTimer()
            return
        }
        if repeatMode == .one {
            resetPlayCounting()
            engine.seek(to: 0)
            engine.play()
            progress.position = 0
            isPlaying = true
            publishNowPlaying()
            return
        }
        if queue.advance(userInitiated: false) != nil {
            loadCurrent(autoplay: true)
        } else {
            engine.pause()
            engine.seek(to: 0)
            isPlaying = false
            progress.position = 0
            publishNowPlaying()
        }
    }

    private func requestArtwork(for track: Track) {
        let loader = loadArtwork
        Task { [weak self] in
            guard let image = await loader(track) else { return }
            guard let self, self.currentTrack?.id == track.id else { return }
            self.nowPlayingArtwork = (track.id, image)
            self.publishNowPlaying()
        }
    }

    private func publishNowPlaying() {
        guard let track = currentTrack else { return }
        let artwork = nowPlayingArtwork?.trackID == track.id ? nowPlayingArtwork?.image : nil
        nowPlaying?.publish(
            track: track,
            position: progress.position,
            duration: progress.duration,
            isPlaying: isPlaying,
            artwork: artwork
        )
    }
}
