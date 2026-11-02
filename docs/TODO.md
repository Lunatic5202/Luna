# LUNA — Active TODO

> **Current release:** 1.0.0  
> **Working toward:** 1.1.0 — The Experience  
> **Status:** planning baseline created 2026-11-02

This is the short, execution-focused task board. The [Product Roadmap](ROADMAP.md) contains the longer-term milestones, acceptance criteria, and release gates.

Only check a task after the work is implemented, reviewed, and verified. For device-dependent behavior, record the device/Android version and test result in the relevant issue or pull request.

## Now — P0: verify and stabilize 1.0.0

- [ ] Reproduce the reported album-art loading delay during library scrolling.
- [ ] Measure mini-player to full-player transition performance.
- [ ] Inspect palette extraction/cache behavior under rapid track changes and missing artwork.
- [ ] Run a physical-device playback smoke test: background playback, lock screen, notification, headset buttons, audio focus, and Bluetooth controls.
- [ ] Verify visualizer permission granted, denied, revoked, and unavailable paths.
- [ ] Test large, empty, and malformed local music libraries.
- [ ] Check layouts and touch targets on compact and large screens.
- [ ] Review current CI status and run the debug and signed release build paths.
- [ ] Capture a known-good 1.0.0 baseline: device, Android version, build identifier, and any known limitations.

## Next — P1: 1.1.0 core work

- [ ] Implement a Folders library view.
- [ ] Implement persistent queue editing and restoration.
- [ ] Implement a sleep timer (duration and end-of-current-track).
- [ ] Add a clear visualizer preference/toggle in Settings.
- [ ] Improve missing/deleted media and empty-library states.
- [ ] Polish artwork/palette transitions without blocking scrolling or playback.
- [ ] Add automated tests for critical playback, queue, palette, and repository behavior.
- [ ] Complete the 1.1.0 release checklist and upgrade-install test.

## Later — planned, not committed to 1.1.0

- [ ] Evaluate gapless playback.
- [ ] Evaluate configurable crossfade.
- [ ] Evaluate playback-speed control.
- [ ] Evaluate Android Auto.
- [ ] Evaluate optional external discovery links/integrations.
- [ ] Explore export/restore for user-created data.
- [ ] Continue VIBE ENGINE refinement after performance and reliability work.

## Working rules

- Prioritize reliability and user-data safety over feature count.
- Keep local playback offline-first; online services must remain optional.
- Never claim a performance fix without reproducing and measuring the behavior.
- CI passing does not replace physical-device testing.
- Keep README feature claims and release notes aligned with what actually ships.

