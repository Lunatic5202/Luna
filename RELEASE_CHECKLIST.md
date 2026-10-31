# Luna release checklist

## Build and package

- [ ] GitHub Actions completes `:app:lintDebug` and `:app:assembleDebug` successfully.
- [ ] Install the APK on an Android 8.0+ device and launch it from a clean install.
- [ ] Confirm the app label and adaptive icon render correctly in the launcher.

## Library and permissions

- [ ] Grant music access and confirm the library loads; deny it and confirm the request can be retried.
- [ ] Search by song title, artist, and album; clear the query and verify the full library returns.
- [ ] Check title sorting and the Artists and Albums views, including tracks with missing metadata.
- [ ] Add, remove, or change a local audio file while the app is open and confirm the library refreshes.

## Playback and lyrics

- [ ] Play, pause, seek, skip, shuffle, repeat, and reorder the queue.
- [ ] Confirm playback pauses when headphones disconnect and resumes correctly after interruptions.
- [ ] Open lyrics online, reopen them, and verify cached lyrics are shown without another lookup.
- [ ] Try unavailable lyrics and a network failure; verify the unavailable result can expire and failures can be retried.
- [ ] Check the media notification and lock-screen controls while the app is in the background.

## Accessibility and visual review

- [ ] Check screen-reader labels for library rows, search, sorting, lyrics, and player controls.
- [ ] Increase system font size and confirm key controls and track names remain usable.
- [ ] Review contrast with varied album art and confirm the color transitions and motion remain comfortable.
