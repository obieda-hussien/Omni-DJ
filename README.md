# Omni DJ

A standalone, offline Android music player with automatic DJ transitions and optional hands-on controls. Kotlin, Jetpack Compose, Media3. Arabic resources plus a complete English translation. Android 8.0+; tested build targets Android 16 / API 36.

## What this version does

- Reads accessible local audio through MediaStore, including phone storage and indexed SD-card audio. Search by song, artist or album; album grouping, favorites, title/artist/recent sorting. Large libraries use lazy rows and artwork caching.
- A single player surface animates between the mini player and expanded player. Tap or drag to expand/collapse; back collapses the player. Waveforms come from decoded audio; no invented waveform or sample catalog.
- Automatic mixing is enabled by default. The planner selects **smooth blend, beat blend, bass swap, filter sweep, echo out, or quick cut**. Users can choose a style, transition duration, tempo-change limit and session mood.
- Smart order evaluates the next twelve queued candidates using tempo compatibility and energy. Explicit **Play next** pins a song ahead of this selection. Songs leave the queue after playing; no automatic repeat.
- Two ExoPlayer decks prebuffer the next track. PCM processing applies three-band sound shaping, a low-pass filter and echo. Incoming tempo can change independently of pitch. Overlap gains reserve headroom; per-deck peak guards prevent PCM overflow.
- Pro controls expose a crossfader, speed, sound shaping and 4/8/16-beat loops. Manual crossfade suspends automatic fade progress until committed or reset. Loops suppress automatic transition scheduling.
- Cached local analysis estimates BPM, beat phase, confidence, RMS, energy, conservative low-energy intro/outro markers and a waveform. One decoder works at a time and pauses during playback. Cache keys include media ID, modification date and size; changed files are analyzed again.
- Background playback through a Media3 media session, with lock-screen/notification transport, audio-focus handling and headset disconnection handling. Playback errors surface in the UI.
- No account, network permission, cloud upload or paid audio SDK is required.

## Accuracy and current boundaries

This is a working first implementation, not a claim of professional hardware timing or a trained neural DJ. Selection is a deterministic hybrid of signal analysis and musical rules. BPM and energy are estimates. Low-confidence tracks fall back to an unstretched smooth transition. The intro/outro estimates do **not** recognize vocals, verses, choruses, maqam or musical downbeats. The shorter-overlap option is a duration cap, not vocal separation.

Two independent Media3 audio outputs and a 40 ms automation loop do not provide a shared sample-accurate clock. Beat alignment is approximate and must be evaluated on physical devices, particularly with Bluetooth. File-seek loops can have short gaps. The EQ uses lightweight first-order crossover filters rather than a calibrated mastering equalizer. Gain balancing only attenuates loud files; it is not EBU R128 loudness normalization.

Stem separation, harmonic/maqam analysis, learned preferences, offline mix export/recording, shared-clock native mixing, automatic loop/flanger transitions and streaming-service integrations remain future work. They are not represented by fake controls or bundled unlicensed models. The original Demucs repository is archived; evaluate maintained models and weights separately before adding separation. Libraries such as Essentia and Rubber Band require a licensing review for a proprietary distribution.

## Build

Install JDK 17 and Android SDK platform 36. Create a local `local.properties` with `sdk.dir`, or set `ANDROID_HOME`.

```sh
./gradlew :mix-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python3 scripts/check-strings.py
```

The pinned toolchain is AGP 8.13.2, Gradle 8.13, Kotlin 2.2.21, Compose BOM 2025.10.00 and Media3 1.8.0. `mix-core` is pure Kotlin/JVM and holds signal analysis and transition policy. `app` owns storage, decoding, playback and UI.

GitHub Actions builds an installable **development APK**, an **unsigned release APK**, and verification reports. Release builds are deliberately unsigned until an owner-managed signing configuration is supplied. Do not distribute development keys as production keys.

## Localization

All user-facing static text and accessibility labels live in `app/src/main/res/values/strings.xml` (Arabic) and `values-en/strings.xml` (English). Device locale chooses the translation. Resource keys and positional formatting are checked in CI. Song metadata is displayed as provided by MediaStore.

Professional left/right audio controls and waveform timelines use a left-to-right coordinate system even when the application language is Arabic. Text elsewhere follows the device direction.

## Device acceptance checklist

1. Grant/deny/revoke music permission on Android 11 and Android 13+, refresh an empty/large library, and test files on an SD card.
2. Play through app backgrounding, screen lock, rotation, task removal, notification controls, incoming calls and headset disconnection. Pause halfway through a fade and verify both decks resume together.
3. Compare pairs with reliable beats, live/variable tempos, silence, different speeds and corrupt/deleted files. Confirm fallback and that queue pins override smart order.
4. Hear every transition and manual effect through wired speakers/headphones and Bluetooth. Test speed changes for artifacts and inspect loop gaps; do not assume DSP quality from a build passing.
5. Expand/drag/collapse the player in portrait/landscape and Arabic/English. Check large fonts, screen-reader labels and sliders.
6. Let analysis finish while idle, restart, modify a song and check cache invalidation. Check memory, CPU and audio underruns on the Infinix X689 before increasing live processing.

## Project status

The repository was initialized from a README-only baseline. Core tests and localization checks are included; CI is the repeatable Android build gate. See pull-request validation for the checks actually run. No physical-device audio or UI result should be inferred from compilation alone.
