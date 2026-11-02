# LUNA for iOS — Development Plan

> **Status:** planned; implementation has not started  
> **Platform:** iPhone first  
> **Parity target:** full LUNA feature parity, implemented with native Apple frameworks  
> **UI:** SwiftUI · **Language:** Swift  
> **Build environment:** GitHub-hosted macOS CI initially; no local Mac is currently available

This document is the source of truth for the iOS track. It is a plan, not a statement that an iOS app, Xcode project, or iOS build already exists.

## Product goal

Bring the LUNA experience to iPhone without mechanically copying Android layouts. Preserve the product identity and behavior while using platform-native navigation, materials, accessibility, media controls, file access, and interaction conventions.

**One LUNA identity. Two native implementations.**

- Android remains Kotlin + Jetpack Compose + Media3.
- iOS will be Swift + SwiftUI with AVFoundation, AVFAudio, MediaPlayer, and system document APIs.
- Keep playback, library, artwork, lyrics, and settings behind well-defined services so UI code stays testable.
- Share product specifications, design tokens, artwork test fixtures, sample metadata, and acceptance criteria. Do not force Android UI or platform-specific playback code into a cross-platform abstraction.

## Platform constraints and decisions

### Development without a local Mac

- [ ] Create a macOS GitHub Actions workflow for iOS builds and tests.
- [ ] Choose and pin an Xcode version and macOS runner after confirming the available hosted runner image.
- [ ] Decide on a project generator (XcodeGen or a checked-in Xcode project) and document reproducible project generation.
- [ ] Run Swift package/unit tests and simulator build/tests in CI.
- [ ] Arrange access to a physical Mac and iPhone before beta/release validation.
- [ ] Confirm the repository's GitHub Actions minutes and macOS runner availability/limits before relying on CI as the only build environment.

A hosted macOS runner can compile and run simulator tests, but it does not provide the same experience as debugging on a personally connected iPhone. Physical-device validation remains a release requirement.

### Supported OS baseline

- [ ] Set an initial deployment target of iOS 17 or later, subject to checking the selected Xcode toolchain and the actual audience/device needs.
- [ ] Document the minimum supported iOS version in the app and README once the project is created.
- [ ] Keep availability checks around APIs newer than the minimum target.

### Local music access is different on iPhone

LUNA must not assume it can scan the entire iPhone filesystem.

Provide two explicit, user-controlled library sources:

1. **Files import / folder access:** use the iOS document picker and folder selection APIs for user-selected audio. Handle security-scoped URLs correctly, persist bookmarks where appropriate, and release access when finished. Consider copying imported files into LUNA's own storage as an explicit, transparent import option for reliable offline playback.
2. **Synced media library:** optionally request Media & Apple Music library authorization and query eligible synced media items through MediaPlayer. Explain exactly what access is requested and handle denied/restricted states.

Do not promise access to every Apple Music catalog track or DRM-protected item. Apple Music subscription/catalog playback is not the same as playing user-owned local files and is not part of the initial local-player scope.

### Playback and audio constraints

- Use AVFoundation/AVFAudio for app-owned file playback and configure AVAudioSession for the playback use case.
- Enable the appropriate background audio capability and test lock-screen/background behavior, interruptions, route changes, Bluetooth, AirPlay, and remote commands.
- Integrate Now Playing metadata and remote controls with MediaPlayer.
- Before committing to one playback engine, spike support for both imported Files audio and eligible synced library items. The selected architecture must support reliable queue behavior and the audio features we actually promise.
- Investigate AVAudioEngine / AVAudioUnitEQ for DSP and playback visualization on audio sources LUNA itself renders. Do not assume a system-managed or protected source can be tapped, equalized, or processed identically.
- The visualizer must react to LUNA's own playback signal. It must not request microphone access merely to visualize music.
- Treat gapless playback, crossfade, and equalizer behavior as source- and device-dependent until tested.

### Signing and distribution

- Local development and simulator builds can begin through macOS CI without publishing anything.
- TestFlight and App Store distribution require Apple signing/provisioning and the appropriate Apple Developer Program access. Do not purchase membership until the team is ready to use those distribution capabilities.
- Never commit signing certificates, private keys, provisioning profiles, API keys, or App Store Connect credentials.

## Proposed iOS architecture

    ios/
    ├── Project.yml                 # if XcodeGen is selected
    ├── LUNA.xcodeproj/             # generated/checked-in project as decided
    ├── LUNA/
    │   ├── App/
    │   ├── DesignSystem/
    │   ├── Features/
    │   │   ├── Library/
    │   │   ├── NowPlaying/
    │   │   ├── Playlists/
    │   │   ├── Queue/
    │   │   ├── Lyrics/
    │   │   ├── Equalizer/
    │   │   └── Settings/
    │   ├── Core/
    │   │   ├── Models/
    │   │   ├── Persistence/
    │   │   └── Services/
    │   ├── Playback/
    │   │   ├── PlaybackCoordinator.swift
    │   │   ├── PlaybackSource.swift
    │   │   └── AudioSessionController.swift
    │   ├── Library/
    │   │   ├── FilesLibraryProvider.swift
    │   │   └── MediaLibraryProvider.swift
    │   ├── Artwork/
    │   ├── Visualizer/
    │   └── Resources/
    └── Tests/
        ├── LUNAUnitTests/
        └── LUNAUITests/

This is a proposed layout, not an existing directory tree. Adjust it to the project generator and actual code once scaffolding begins.

### Key components

- **PlaybackCoordinator:** single UI-facing playback state, queue, shuffle/repeat, seek, next/previous, and source switching.
- **Playback adapters:** isolate AVFoundation playback and MediaPlayer library playback; avoid leaking framework objects into SwiftUI views.
- **Library providers:** Files/folder imports and authorized synced media library, with stable internal track identifiers.
- **ArtworkPipeline:** asynchronous artwork decode, bounded cache, embedded-art fallback, and palette extraction off the main actor.
- **LunaPalette:** neutral fallback plus artwork-derived colors, with smooth transitions and contrast-safe text/control colors.
- **LyricsService:** provider abstraction, metadata-only lookup, local cache, expiry, and clear unavailable/offline states.
- **Persistence:** playlists, favorites, preferences, and queue state stored locally; use migration-safe versioned models.
- **DesignSystem:** shared LUNA visual rules translated to native SwiftUI components, not a pixel-for-pixel Android clone.

## Feature parity checklist

### Foundation and onboarding

- [ ] SwiftUI app shell, navigation, app icon, launch experience, and LUNA typography.
- [ ] Permission/onboarding explanations before requesting media-library access.
- [ ] Files picker for importing audio and selecting folders.
- [ ] Clear source labels so users know whether a track came from Files or the synced media library.
- [ ] Offline-first behavior and a privacy explanation.

### Library

- [ ] Songs, Albums, Artists, and Folders views where the source supports them.
- [ ] Search by title, artist, and album.
- [ ] Sorting and recently added/played behavior.
- [ ] Favorites and user-created playlists/Combos.
- [ ] Duplicate/missing metadata handling and empty/error states.
- [ ] Efficient scanning/indexing without blocking the UI.

### Playback

- [ ] Reliable local playback and queue management.
- [ ] Mini-player and full-screen Now Playing.
- [ ] Seek, shuffle, repeat, previous/next, and queue reorder/remove/clear.
- [ ] Background playback, lock-screen and Control Center metadata/controls.
- [ ] Headphone/Bluetooth remote controls, audio interruptions, and route changes.
- [ ] Sleep timer.
- [ ] Supported-format handling and clear unsupported/protected-file errors.

### LUNA visual identity

- [ ] Adaptive album-art palette extraction and neutral fallback.
- [ ] Bounded artwork and palette caches.
- [ ] Smooth artwork and color transitions.
- [ ] Native-material/glass-inspired controls, restrained highlights, and accessible contrast.
- [ ] Artwork-driven Now Playing layout designed for iPhone safe areas and Dynamic Island/device variations.
- [ ] Motion settings and reduced-motion support.

### Audio and lyrics

- [ ] Playback-signal visualizer without microphone recording.
- [ ] Equalizer/DSP feasibility spike and source compatibility matrix.
- [ ] Equalizer UI and presets for sources where LUNA controls the audio render path.
- [ ] Lyrics lookup and local caching with metadata-only network requests.
- [ ] Evaluate gapless playback, crossfade, and playback speed against supported sources.

### Quality and release

- [ ] Unit tests for queue, persistence, library indexing, palette fallback/cache, and lyrics caching.
- [ ] UI tests for onboarding, library, playback, queue, and settings.
- [ ] Simulator test matrix across supported iPhone sizes and iOS versions.
- [ ] Physical iPhone test for audio routes, lock screen, background playback, imports, and interruptions.
- [ ] VoiceOver, Dynamic Type, contrast, and reduced-motion audit.
- [ ] Privacy manifest/declaration and App Store metadata review where applicable.
- [ ] Signed archive, TestFlight beta, crash/feedback review, and App Store submission.

## Milestones

### iOS 0.1 — Foundation

- [ ] Repository/project scaffold and macOS CI.
- [ ] SwiftUI app shell and design tokens.
- [ ] Files import and synced-library authorization spike.
- [ ] Playback-engine feasibility spike and architecture decision record.
- [ ] Basic local file playback and Now Playing metadata.
- [ ] Unit-test baseline.

**Exit gate:** repeatable CI build; a user can import a supported audio file and play it in the foreground; source-access lifecycle is safe.

### iOS 0.2 — Core Player

- [ ] Library indexing, songs/albums/artists/folders.
- [ ] Playback queue, shuffle/repeat, mini-player, and full-screen player.
- [ ] Background playback, lock-screen controls, and route/interruption handling.
- [ ] Persistence for favorites, playlists, and settings.
- [ ] Artwork loading and adaptive palette.

**Exit gate:** the main local-listening workflow works in simulator and on a physical iPhone.

### iOS 0.3 — LUNA Experience

- [ ] Lyrics and caching.
- [ ] Playback visualizer.
- [ ] Equalizer/DSP where source path supports it.
- [ ] Sleep timer and complete settings.
- [ ] Refined transitions, glass-inspired surfaces, accessibility, and performance.

**Exit gate:** feature parity gaps are documented; all advertised features work on supported source types.

### iOS 1.0 — iPhone release

- [ ] Full regression and device matrix.
- [ ] TestFlight beta and feedback cycle.
- [ ] Privacy, signing, screenshots, App Store metadata, and support materials.
- [ ] Final App Review submission and release.

**Exit gate:** no known critical playback/data-loss issue; signed build and TestFlight validation complete; store claims match verified behavior.

## Definition of full parity

Full parity means equivalent user outcomes and workflows, not identical implementation details. Where iOS imposes a real platform or source restriction, document the difference and provide the closest honest native alternative. Never label a feature as supported on all sources if it only works for Files-imported, non-protected audio.

## Reference documentation

- [Xcode system requirements](https://developer.apple.com/xcode/system-requirements/)
- [Xcode overview](https://developer.apple.com/documentation/xcode)
- [AVFoundation media playback configuration](https://developer.apple.com/documentation/avfoundation/configuring-your-app-for-media-playback)
- [AVAudioSession](https://developer.apple.com/documentation/avfaudio/avaudiosession)
- [Audio Engine](https://developer.apple.com/documentation/avfaudio/audio-engine)
- [Media Player framework](https://developer.apple.com/documentation/mediaplayer/)
- [Media library authorization](https://developer.apple.com/documentation/mediaplayer/mpmedialibraryauthorizationstatus)
- [Providing access to directories](https://developer.apple.com/documentation/uikit/providing-access-to-directories)
- [UIDocumentPickerViewController](https://developer.apple.com/documentation/uikit/uidocumentpickerviewcontroller)
- [Apple distribution overview](https://help.apple.com/xcode/mac/current/en.lproj/devac02c5ab8.html)

---

*Last reviewed: 2026-11-02. Update when the project scaffold and playback feasibility spikes are complete.*
