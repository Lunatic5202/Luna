# LUNA for iOS — Development Plan

> **Status:** iOS 0.2 in progress; first unsigned IPA published with v1.1.0 (install guide: IOS_INSTALL.md). Import and playback, the library (search, sort, Songs/Albums/Artists/Combos, favorites), queue, sleep timer, adaptive palette, lyrics and a first-launch privacy screen are merged and pass simulator CI. Nothing has been run on a physical iPhone. Visualizer, equalizer, folders and a settings screen are not built.  
> **Platform:** iPhone first  
> **Parity target:** full LUNA feature parity, implemented with native Apple frameworks  
> **UI:** SwiftUI · **Language:** Swift  
> **Build environment:** GitHub-hosted macOS CI initially; no local Mac is currently available

This document is the source of truth for the iOS track.

**How to read the checkboxes:** a checked box means the feature is merged and covered by simulator CI (unit tests, plus a compile check for UI-only code). It does not mean it has been verified on a physical iPhone; that is tracked separately under "Physical iPhone test". Notes in brackets say what is and is not done.

## Product goal

Bring the LUNA experience to iPhone without mechanically copying Android layouts. Preserve the product identity and behavior while using platform-native navigation, materials, accessibility, media controls, file access, and interaction conventions.

**One LUNA identity. Two native implementations.**

- Android remains Kotlin + Jetpack Compose + Media3.
- iOS will be Swift + SwiftUI with AVFoundation, AVFAudio, MediaPlayer, and system document APIs.
- Keep playback, library, artwork, lyrics, and settings behind well-defined services so UI code stays testable.
- Share product specifications, design tokens, artwork test fixtures, sample metadata, and acceptance criteria. Do not force Android UI or platform-specific playback code into a cross-platform abstraction.

## Platform constraints and decisions

### Development without a local Mac

- [x] Create a macOS GitHub Actions workflow for iOS builds and tests.
- [x] Choose and pin an Xcode version and macOS runner after confirming the available hosted runner image. (Xcode 16.4 on `macos-15`; the workflow falls back to the image default if 16.4 disappears.)
- [x] Decide on a project generator (XcodeGen or a checked-in Xcode project) and document reproducible project generation. (XcodeGen; the `.xcodeproj` is generated, see the header of `ios/Project.yml`.)
- [x] Run Swift package/unit tests and simulator build/tests in CI.
- [ ] Arrange access to a physical Mac and iPhone before beta/release validation.
- [ ] Confirm the repository's GitHub Actions minutes and macOS runner availability/limits before relying on CI as the only build environment.

A hosted macOS runner can compile and run simulator tests, but it does not provide the same experience as debugging on a personally connected iPhone. Physical-device validation remains a release requirement.

### Supported OS baseline

- [x] Set an initial deployment target of iOS 17 or later, subject to checking the selected Xcode toolchain and the actual audience/device needs.
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

### GitHub-only sideload distribution

LUNA will not use the App Store or TestFlight as its distribution channel. The intended public release format is an **unsigned IPA attached to each GitHub Release**.

- GitHub-hosted macOS CI builds the iOS app with code signing disabled and packages the resulting `.app` as `Payload/LUNA.app` inside an `.ipa` archive.
- An unsigned IPA is a build artifact, not an install-ready app. Each iPhone user must sign it with their own Apple ID using a compatible sideloading tool before installation.
- With a free Apple account (Personal Team provisioning), the installed app generally needs to be refreshed/re-signed every seven days. Free provisioning also has Apple's device/app limits.
- Common sideloading utilities such as Sideloadly/AltStore have their own desktop and device requirements; verify current OS support. Do not promise a universal one-tap install or a phone-only installation path.
- The repository must never contain a shared developer certificate, private key, provisioning profile, Apple ID, or signing credentials. Users sign their own copies locally.
- Attach the unsigned IPA and a SHA-256 checksum to the same GitHub Release as the Android APK. Label the asset clearly as unsigned and include the signing/expiry caveat in release notes.
- Simulator CI is not a substitute for physical-device testing. Validate installation, provisioning, audio background modes, file imports, and playback on a real iPhone before calling an iOS release stable.

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

- [ ] SwiftUI app shell, navigation, app icon, launch experience, and LUNA typography. (Shell, palette tokens, placeholder icon and the first-launch welcome screen are in; a launch animation, final icon and LUNA typography are not.)
- [ ] Permission/onboarding explanations before requesting media-library access. (The welcome/privacy screen is in. Media & Apple Music access is not built, so nothing is requested yet.)
- [ ] Files picker for importing audio and selecting folders. (Importing files is in; folder selection is not.)
- [ ] Clear source labels so users know whether a track came from Files or the synced media library.
- [x] Offline-first behavior and a privacy explanation.

### Library

- [ ] Songs, Albums, Artists, and Folders views where the source supports them. (Songs, Albums, Artists and Combos are in; Folders are not.)
- [x] Search by title, artist, and album.
- [ ] Sorting and recently added/played behavior. (Sort by title, artist and recently added is in; recently played and most played are not.)
- [x] Favorites and user-created playlists/Combos.
- [ ] Duplicate/missing metadata handling and empty/error states. (Empty, no-match and no-favorites states and "Unknown" fallbacks are in; duplicate-import handling has not been checked.)
- [ ] Efficient scanning/indexing without blocking the UI.

### Playback

- [x] Reliable local playback and queue management.
- [x] Mini-player and full-screen Now Playing.
- [x] Seek, shuffle, repeat, previous/next, and queue reorder/remove/clear.
- [x] Background playback, lock-screen and Control Center metadata/controls.
- [x] Headphone/Bluetooth remote controls, audio interruptions, and route changes.
- [x] Sleep timer. (An in-app timer: it runs while audio plays in the background but cannot fire if iOS suspends the app.)
- [ ] Supported-format handling and clear unsupported/protected-file errors. (Unplayable files are rejected at import with a message; protected-file detection is not done.)

### LUNA visual identity

- [x] Adaptive album-art palette extraction and neutral fallback.
- [x] Bounded artwork and palette caches.
- [ ] Smooth artwork and color transitions. (The palette cross-fades; artwork transitions are not done.)
- [ ] Native-material/glass-inspired controls, restrained highlights, and accessible contrast.
- [ ] Artwork-driven Now Playing layout designed for iPhone safe areas and Dynamic Island/device variations.
- [ ] Motion settings and reduced-motion support.

### Audio and lyrics

- [ ] Playback-signal visualizer without microphone recording.
- [ ] Equalizer/DSP feasibility spike and source compatibility matrix.
- [ ] Equalizer UI and presets for sources where LUNA controls the audio render path.
- [x] Lyrics lookup and local caching with metadata-only network requests. (LRCLIB; tested against scripted responses, not the live service.)
- [ ] Evaluate gapless playback, crossfade, and playback speed against supported sources.

### Quality and release

- [x] Unit tests for queue, persistence, library indexing, palette fallback/cache, and lyrics caching. (The palette store's own cache is not covered.)
- [ ] UI tests for onboarding, library, playback, queue, and settings.
- [ ] Simulator test matrix across supported iPhone sizes and iOS versions.
- [ ] Physical iPhone test for audio routes, lock screen, background playback, imports, and interruptions.
- [ ] VoiceOver, Dynamic Type, contrast, and reduced-motion audit.
- [ ] Privacy manifest/declaration and public privacy documentation review.
- [x] Unsigned IPA packaged and attached to a GitHub Release with checksum and clear sideloading instructions. [v1.1.0 published; guide in docs/IOS_INSTALL.md; not yet installed on a device]
- [ ] Physical-device installation and playback validation with a user-owned free Apple ID.

## Milestones

### iOS 0.1 — Foundation

- [x] Repository/project scaffold and macOS CI.
- [x] SwiftUI app shell and design tokens.
- [ ] Files import and synced-library authorization spike. (Files import is in; the synced-library spike is not.)
- [ ] Playback-engine feasibility spike and architecture decision record. (AVPlayer is in use; no written decision record yet.)
- [x] Basic local file playback and Now Playing metadata.
- [x] Unit-test baseline.

**Exit gate:** repeatable CI build; a user can import a supported audio file and play it in the foreground; source-access lifecycle is safe.

### iOS 0.2 — Core Player

- [ ] Library indexing, songs/albums/artists/folders. (Songs, albums and artists are in; folders are not.)
- [x] Playback queue, shuffle/repeat, mini-player, and full-screen player.
- [x] Background playback, lock-screen controls, and route/interruption handling.
- [x] Persistence for favorites, playlists, and settings.
- [x] Artwork loading and adaptive palette.

**Exit gate:** the main local-listening workflow works in simulator and on a physical iPhone.

### iOS 0.3 — LUNA Experience

- [x] Lyrics and caching.
- [ ] Playback visualizer.
- [ ] Equalizer/DSP where source path supports it.
- [ ] Sleep timer and complete settings. (The sleep timer and a basic settings screen are in: library counts, clear lyrics cache and play history, version, privacy. Playback and equalizer settings are not, since those features are not built.)
- [ ] Refined transitions, glass-inspired surfaces, accessibility, and performance.

**Exit gate:** feature parity gaps are documented; all advertised features work on supported source types.

### iOS 1.0 — GitHub release

- [ ] Full regression and device matrix.
- [ ] Physical-device sideload test using a personal free Apple ID.
- [ ] Privacy, signing instructions, screenshots, release notes, and support materials.
- [x] Publish the unsigned IPA and checksum alongside the Android APK on GitHub Releases. [v1.1.0]

**Exit gate:** no known critical playback/data-loss issue; sideload installation and refresh instructions verified; release assets and feature claims match the tested build.

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


## GitHub IPA packaging workflow

The release workflow builds the iOS target on a GitHub-hosted macOS runner with `CODE_SIGNING_ALLOWED=NO`, wraps the resulting app bundle in the standard `Payload/` IPA structure, computes a SHA-256 checksum, and uploads both files to the matching GitHub Release. This does not create a signed or universally installable IPA. Every recipient signs their own copy with their own Apple ID.
