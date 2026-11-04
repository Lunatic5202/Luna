import SwiftUI

@main
struct LunaApp: App {
    @StateObject private var library: LibraryViewModel
    @StateObject private var player: PlaybackCoordinator
    @StateObject private var theme: PaletteStore
    @StateObject private var combos: ComboStore
    // Held for the life of the app so their notification observers and command targets stay alive.
    private let audioSession: AudioSessionController
    private let remoteCommands: RemoteCommandController

    init() {
        let library = LibraryViewModel(provider: FilesLibraryProvider.makeDefault())
        let session = AudioSessionController()
        let player = PlaybackCoordinator(
            engine: AVPlayerEngine(),
            resolveURL: { library.url(for: $0) },
            nowPlaying: SystemNowPlaying(),
            activateSession: { session.activate() },
            loadArtwork: { track in
                await ArtworkLoader.shared.image(for: track, url: library.url(for: track), maxDimension: 600)
            }
        )

        session.onInterruptionBegan = { [weak player] in player?.interruptionBegan() }
        session.onInterruptionEnded = { [weak player] shouldResume in
            player?.interruptionEnded(shouldResume: shouldResume)
        }
        session.onRouteLost = { [weak player] in player?.routeLost() }

        _library = StateObject(wrappedValue: library)
        _player = StateObject(wrappedValue: player)
        _theme = StateObject(wrappedValue: PaletteStore())
        _combos = StateObject(wrappedValue: ComboStore.makeDefault())
        audioSession = session
        remoteCommands = RemoteCommandController(coordinator: player)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(library)
                .environmentObject(player)
                .environmentObject(theme)
                .environmentObject(combos)
                .preferredColorScheme(.dark)
        }
    }
}
