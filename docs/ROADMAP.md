# LUNA — Product Roadmap & Living TODO

> **Current release:** 1.0.0 — The First Light  
> **Next milestone:** 1.1.0 — The Experience  
> **Product direction:** a polished, offline-first Android music player with adaptive artwork-driven visuals. Keep the interface restrained, fast, and music-focused.

This is the canonical planning document for LUNA. Check items off only after the implementation is merged and its acceptance criteria have been verified. A successful CI build is necessary, but it does not replace device testing.

## How to read this roadmap

- [x] Implemented in the project according to the current repository documentation.
- [ ] Planned, incomplete, or awaiting verification.
- **P0** Release-quality issue or reliability work.
- **P1** Core next-release feature.
- **P2** Later enhancement; do not let it delay stability work.

If implementation status is uncertain, leave the item unchecked and verify the code/device behavior before changing its status.

---

## 1.0.0 — The First Light

The first public milestone. The following capabilities are documented as implemented:

- [x] Media3 playback service and system media session.
- [x] Background playback, notification and lock-screen controls.
- [x] Local MediaStore library scanning.
- [x] Tracks, Artists, and Albums library views.
- [x] Search and sorting.
- [x] Queue, shuffle, and repeat modes.
- [x] User-created playlist collections ("Combos").
- [x] Full-screen player and mini-player transitions.
- [x] Adaptive album-art palette extraction and caching.
- [x] Audio-reactive visualizer with a non-recording playback-session design.
- [x] Lyrics lookup and on-device caching.
- [x] System equalizer integration and saved profiles.
- [x] Liquid-glass-inspired surfaces and motion.
- [x] Signed release workflow and GitHub release publishing.

The checkmarks above describe documented project capabilities, not a claim that every device-specific behavior has been exhaustively tested.

## 1.1.0 — The Experience

**Goal:** make everyday listening feel fast, dependable, and complete before expanding the feature set.

### P0 — Reliability, performance, and release confidence

- [ ] Reproduce and profile slow album-art loading while scrolling the library.
- [ ] Profile the mini-player ↔ full-screen player transition; remove avoidable decoding, blocking work, and jank.
- [ ] Verify palette extraction/cache behavior when tracks change quickly, artwork is missing, or multiple tracks share artwork.
- [ ] Test playback continuity through Activity recreation, app backgrounding, screen lock, and process recreation where supported.
- [ ] Test audio focus, incoming calls/other audio, headset disconnect, Bluetooth controls, and notification actions.
- [ ] Verify permission-denied and permission-revoked paths, especially the optional visualizer.
- [ ] Test library scanning with empty libraries, large libraries, duplicate metadata, missing tags, unsupported/corrupt files, and files added or removed while LUNA is running.
- [ ] Run a real-device layout pass across compact and large screens, Android versions supported by the app, and system font/display scaling.
- [ ] Add or expand automated tests for repository logic, queue behavior, palette fallback/cache, and critical UI state.
- [ ] Record a repeatable manual smoke-test checklist and attach build/test evidence to release candidates.

**Acceptance criteria:** no known playback-blocking regression; no reproducible severe scrolling or transition jank left untriaged; critical flows pass on at least one physical device; CI is green; permission and failure states are understandable.

### P1 — Complete the everyday library workflow

- [ ] Add a Folders view with clear folder navigation and playable folder contents.
- [ ] Add persistent queue editing: reorder, remove, clear, and restore the queue consistently.
- [ ] Add a sleep timer with stop-after-duration and stop-after-current-track options.
- [ ] Add explicit visualizer preferences, including an easy off switch; keep it tied to LUNA playback, never ambient microphone recording.
- [ ] Review favorites/liked-track behavior and make it consistent across library, queue, and playback surfaces.
- [ ] Improve empty states and recovery paths for missing or deleted media.
- [ ] Review metadata editing/refresh needs; avoid destructive changes to the user's files.

**Acceptance criteria:** users can navigate by folder, manage a queue across app restarts, set a sleep timer, and control the visualizer without hunting through the interface.

### P1 — Finish the visual experience

- [ ] Tune artwork and palette transitions for rapid track changes and missing artwork.
- [ ] Verify the player capsule/ellipse and visualizer on different aspect ratios and display sizes.
- [ ] Check animation settings, reduced-motion behavior, and low-power behavior.
- [ ] Audit typography, spacing, contrast, touch targets, and accessibility labels.
- [ ] Keep the visual identity adaptive and understated: no default purple gradient, unnecessary glow, or explanatory clutter.

**Acceptance criteria:** the interface remains legible and responsive across supported devices, with no clipped controls or distracting transition artifacts.

### 1.1 release gate

- [ ] All P0 items either pass or have documented, consciously accepted limitations.
- [ ] P1 features included in the release are complete and tested; unfinished items are not advertised as shipped.
- [ ] Debug and signed release builds succeed.
- [ ] Release APK installs cleanly and upgrades an existing installation without losing app data.
- [ ] Release notes, version number, artifact, and checksum are checked before publishing.

---

## 1.2.0 — The Sound

**Goal:** deepen playback control without compromising reliability or battery life.

- [ ] Evaluate gapless playback and verify transitions with representative local files.
- [ ] Evaluate crossfade with a user-configurable duration and a true off state.
- [ ] Add playback-speed control if it fits the product's listening use cases.
- [ ] Review equalizer lifecycle, device compatibility, preset persistence, and graceful fallback when an audio effect is unavailable.
- [ ] Evaluate replay-gain/loudness normalization only after checking format support and clipping behavior.
- [ ] Add audio-focused tests and document device-dependent limitations.

These are planned investigations, not promises that every Android device exposes identical audio capabilities.

## 2.0.0 — Beyond Playback

**Goal:** expand LUNA's ecosystem only after the core player is stable.

- [ ] Investigate Android Auto support and its UX/policy requirements.
- [ ] Explore optional external discovery/search integrations that open the provider's own service; do not make core playback depend on an unofficial API.
- [ ] Explore deeper library personalization and configurable navigation.
- [ ] Revisit the VIBE ENGINE: artwork-derived palette, smooth color morphing, and playback-reactive visuals as one coherent system.
- [ ] Evaluate backup/export and restore for user-created playlists, preferences, and queue-related data.
- [ ] Review privacy, permissions, and offline behavior before adding any network-connected feature.

---

## Ongoing engineering checklist

- [ ] Keep the README, release notes, and this roadmap aligned with shipped behavior.
- [ ] Keep dependency/license attribution current when dependencies change.
- [ ] Avoid collecting or uploading local audio; explain any metadata sent to external lyric/discovery providers.
- [ ] Keep long-running scans, artwork decoding, and network lookups off the main thread.
- [ ] Bound caches and release media/audio resources correctly.
- [ ] Prefer graceful fallback over crashes when OEM audio effects or metadata providers behave differently.
- [ ] Do not mark work complete based on a screenshot or CI alone; verify the actual interaction.

## Scope rules

1. Stability and user data safety outrank new features.
2. LUNA remains an offline-first local music player; online integrations are optional.
3. No feature may silently upload audio files or scan outside the user's chosen media scope.
4. Keep the visual language minimal, adaptive, and consistent with the music.
5. Ship smaller, tested releases rather than bundling unfinished features into a milestone.

---

*Last reviewed: 2026-11-02. Update this file as work is implemented, tested, and released.*
