# LUNA on iPhone: plan

Route: **Kotlin Multiplatform + Compose Multiplatform**. Android stays the
reference app and must keep building and shipping at every step.

## What I found (7,163 lines, ~32 files)

| Area | State |
|---|---|
| Compose UI (screens, theme, motion) | Portable in principle; Material 3 and Compose are multiplatform |
| `haze` 1.6 (blur) | Multiplatform, keep |
| `io.github.kyant0:backdrop` (liquid glass) | **Android-only** (AGSL shader). Needs an iOS fallback (haze blur) |
| Media3 / ExoPlayer / MediaSession | **Android-only.** iOS: AVPlayer + MPNowPlayingInfoCenter + remote commands |
| Equalizer, bass, visualizer (`android.media.audiofx`) | **Android-only.** iOS: AVAudioEngine EQ + audio tap |
| MediaStore library, import, `DownloadManager`, `MediaMetadataRetriever` | **Android-only.** iOS has no shared music folder: library comes from the Files picker / MPMediaLibrary |
| `androidx.palette`, `BitmapFactory` | Android-only. Replace with a small shared colour extractor |
| Google Fonts / splash screen / `MainActivity` | Android-only shell |

## Steps (one PR each, CI-green before the next)

1. **Shared module.** Add `:shared` (commonMain, androidMain, iosMain). Move
   pure Kotlin first: `Track`, `MusicCombo`, `AlbumPalette`, theme, motion.
2. **Interfaces.** `MusicPlayer` already exists; widen it to everything the UI
   calls, with an Android implementation (Media3) behind it.
3. **Move UI** screen by screen into commonMain, replacing Android-only calls
   (`painterResource`, fonts, `BackHandler`, permissions) with expect/actual.
4. **iOS app shell** (Xcode project, SwiftUI host, AVPlayer implementation,
   lock-screen controls, background audio).
5. **CI**: macOS runner builds the iOS framework and an unsigned `.app`/`.ipa`.
6. **Feature parity**: EQ, visualizer, lyrics, library import, sleep timer.

## Decisions / risks

- Nothing can be run on a real iPhone from this environment, only built on CI.
  Every iOS step needs you (or a tester) to try it on a device.
- Distribution needs an Apple Developer account ($99/yr) for TestFlight or the
  App Store; free sideloading expires every 7 days.
- Liquid-glass look, EQ and visualizer will differ on iOS at first.
- Downloads/Discover rely on Android's DownloadManager; iOS version comes last.
