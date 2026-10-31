# LUNA 🌙
**Music, in another dimension.**

A native, offline-first Android music player built with Kotlin and Jetpack Compose.

## Included in this starter
- Local audio discovery through MediaStore
- Android audio permission flow
- Playback queue using AndroidX Media3 ExoPlayer
- Album-art URI discovery from MediaStore
- Palette extraction with AndroidX Palette
- In-memory artwork palette cache
- Animated adaptive colour transitions
- Semantic dynamic Material 3 colour scheme
- Translucent layered glass surfaces
- Responsive mini spectrum animation (visual placeholder)

## Open in Android Studio
1. Extract this archive.
2. Open Android Studio and select the `LUNA` folder.
3. Install Android SDK 36 and use JDK 17.
4. Let Gradle sync and run the `app` configuration on a device/emulator.
5. Grant audio permission when prompted.

First build requires internet for Gradle dependencies. Music playback itself is local.

## Important implementation notes
- The current glass component uses translucent layers and edge highlights. The Haze dependency is included for a subsequent true backdrop-blur pass; its API should be validated against the exact version during that integration.
- The mini spectrum is animated UI, not yet connected to actual audio frequencies.
- Playback is currently Activity-owned. A MediaSessionService is the next required step for reliable background playback, notification and lock-screen controls.
- Album art is resolved using MediaStore album-art URIs. Devices with missing or unusual metadata use the fallback palette.
- This is an initial development foundation, not a finished release APK. Build and test on a physical device before distribution.

## Roadmap
- [ ] MediaSessionService, notification and lock-screen controls
- [ ] Real audio-reactive visualizer using audio session effects
- [ ] True backdrop blur/refraction glass treatment
- [ ] Full player screen and shared-element artwork transitions
- [ ] Seek bar, progress and duration
- [ ] Search, album, artist and folder views
- [ ] Playlists, queue, shuffle and repeat
- [ ] Sleep timer and equalizer
- [ ] Persistent palette cache, tests and release polish

## Stack
Kotlin · Jetpack Compose · Material 3 · Media3 · AndroidX Palette · Haze
