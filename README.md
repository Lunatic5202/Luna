# LUNA 🌙
**Music, in another dimension.**

A native, offline-first Android music player built with Kotlin and Jetpack Compose.

## Current foundation
- Local audio discovery through MediaStore (scanned off the UI thread)
- Android audio permission flow
- Media3 ExoPlayer owned by a dedicated `MediaSessionService`
- MediaController connection from the Compose Activity
- System media session integration for playback notification, lock-screen and headset controls
- Track metadata and artwork URI supplied to the media session
- Album-art palette extraction with bounded thumbnail decoding
- Bounded LRU palette cache with synchronized extraction
- Animated adaptive colour transitions and semantic Material 3 colours
- Translucent layered glass surfaces
- Animated mini spectrum placeholder

## Build
- Android Studio with Android SDK 36
- JDK 17
- Gradle 8.13 or compatible
- Open the repository as a Gradle project, sync dependencies, then run the `app` configuration on a device or emulator.
- A GitHub Actions workflow runs `gradle :app:assembleDebug` on pushes and pull requests to `main`.

First build requires internet for Gradle dependencies. Music playback itself is local.

## Architecture notes
- `MainActivity` owns UI state and connects to `LunaPlaybackService` through a Media3 `MediaController`. It does not own or release the player.
- `LunaPlaybackService` owns the ExoPlayer and MediaSession independently of the Activity lifecycle. Media3 supplies the system media notification and session controls while playback is active.
- `MusicRepository` queries MediaStore on `Dispatchers.IO`; the Activity collects the result asynchronously.
- `LunaThemeEngine` extracts and caches album-art palettes. A request token prevents an older artwork decode from replacing the palette for a newer track.
- The current glass component is translucent layered styling. The Haze dependency is present, but true backdrop blur/refraction is not implemented yet.
- The mini spectrum is animated UI, not yet connected to actual audio frequencies.
- MediaStore album-art URIs may be missing on some devices; LUNA falls back to a default palette.

## Roadmap
- [x] MediaSessionService and MediaController foundation
- [x] System media-session metadata and playback controls
- [x] Asynchronous local library scan
- [x] Bounded album palette extraction/cache
- [ ] Full player screen and artwork transitions
- [ ] Seek bar, progress and duration
- [ ] Search, album, artist and folder views
- [ ] Playlists, queue, shuffle and repeat
- [ ] True backdrop blur/refraction glass treatment
- [ ] Audio-reactive visualizer
- [ ] Sleep timer and equalizer
- [ ] Instrumentation tests, device validation and release polish

## Stack
Kotlin · Jetpack Compose · Material 3 · AndroidX Media3 · AndroidX Palette · Haze
