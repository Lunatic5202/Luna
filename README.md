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
- Haze-backed frosted-glass surfaces with album-tinted blur, translucent fills, noise, and reflective edge highlights
- Compose liquid-glass surfaces for the mini-player and fullscreen controls, with adaptive album tint and a blur fallback
- Blurred album-art ambience behind the Now Playing screen
- Animated library/player screen transitions
- Full-screen Now Playing UI with seek progress, duration, and playback controls
- Animated mini spectrum placeholder
- Session queue sheet with direct track selection
- Shuffle and repeat-off / repeat-all / repeat-one controls
- Device-backed equalizer with presets and per-band adjustments
- Local playlist combos, a swipe-open navigation drawer, and an About page

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
- `LunaGlassSurface` uses the Compose Backdrop liquid-glass effect over a captured screen background; custom dome geometry and older Android releases use the frosted blur fallback.
- Combos are stored locally in app preferences and refer to MediaStore track IDs; deleting a source file can make a saved combo shorter or unplayable.
- The equalizer attaches Android's `Equalizer` audio effect to ExoPlayer's audio session. Presets and support vary by device; LUNA does not record microphone input.
- The mini spectrum is currently an animated playback indicator, not yet connected to actual audio frequencies.
- MediaStore album-art URIs may be missing on some devices; LUNA falls back to a default palette.

## Roadmap
- [x] MediaSessionService and MediaController foundation
- [x] System media-session metadata and playback controls
- [x] Asynchronous local library scan
- [x] Bounded album palette extraction/cache
- [x] Full player screen and artwork ambience
- [x] Seek bar, progress and duration
- [x] Animated library/player transitions
- [ ] Search, album, artist and folder views
- [x] Playback queue, shuffle and repeat modes
- [x] User-created playlist combos
- [ ] Persistent queue editing
- [x] Haze-backed backdrop blur and adaptive glass treatment
- [ ] Refraction-driven glass highlights
- [ ] Audio-reactive visualizer
- [ ] Sleep timer
- [x] System equalizer controls
- [ ] Instrumentation tests, device validation and release polish

## Stack
Kotlin · Jetpack Compose · Material 3 · AndroidX Media3 · AndroidX Palette · Haze

