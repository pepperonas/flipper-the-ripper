# Changelog

All notable changes to **Flipper the Ripper** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.2] - 2026-07-26

### Fixed
- **Download engine could not start at all** — the single biggest bug. youtubedl-android ships its
  Python/ffmpeg payloads as `lib*.zip.so` files that it unzips from disk at runtime, but the app was
  built with the modern default `extractNativeLibs=false`, so those files were never written to disk
  and `YoutubeDL.init()` failed with "failed to initialize". Every download therefore failed
  immediately. Now `useLegacyPackaging = true` extracts them, so the engine initialises and
  extraction/metadata (title, thumbnail, duration) works again.
- **App icon** — the launcher glyph was off-centre. Redrawn as a centred download motif on an MD3
  violet gradient, sized to the adaptive-icon safe zone.

### Added
- **Automatic yt-dlp updates** — the bundled extractor is frozen at the library's release, so it is
  now refreshed on launch (throttled to once per 12 h) and the download worker updates + retries once
  when it hits a stale-extractor / merge failure.
- **Progressive (no-ffmpeg) fallback** — if a download fails needing an ffmpeg merge, it retries with
  a single pre-muxed format so it still produces a playable file.
- YouTube now uses the `android_vr` / `tv` player clients, which get past YouTube's `n`-signature
  JavaScript challenge (there is no JS runtime on Android).

### Known limitations
- **Platform anti-bot measures (2026).** Some platforms now gate the actual media download behind
  measures the bundled engine cannot fully satisfy on stock Android: **YouTube** may require a PO
  token / serves DRM on some clients, and **TikTok** may require TLS "impersonation" (`curl_cffi`).
  Extraction (title/thumbnail/formats) works, but such downloads can still fail until the upstream
  yt-dlp + dependencies close the gap. This affects all yt-dlp-based Android apps. Content not behind
  these walls downloads normally; keeping the engine updated (Settings → Update yt-dlp) helps.

## [1.1.1] - 2026-07-26

### Changed
- **APK size cut ~55%** (from ~74 MB to **~33 MB**) with **no loss of features or device support**:
  - **ABI splits** — ship one APK per architecture (`arm64-v8a`, `armeabi-v7a`) instead of a single
    fat APK carrying both architectures' native libs. Each user downloads only their architecture;
    both remain supported. Per-ABI `versionCode` offsetting keeps updates ordered.
  - **Dropped the bundled `aria2c`** downloader (~6 MB/ABI) — it was never invoked (yt-dlp handles
    downloading itself), so it was pure dead weight. No functional change.
- The release workflow now builds and publishes both per-ABI APKs.

## [1.1.0] - 2026-07-26

### Changed
- **Material 3 Expressive redesign.** The whole UI now uses the Expressive design system with a
  spring-based motion physics engine and playful shape motion:
  - `MaterialExpressiveTheme` + `MotionScheme.expressive()` — components animate with the theme's
    spatial/effects springs; `expressiveLightColorScheme()` + a tuned dark scheme; dynamic color kept.
  - Shape-morphing `MaterialShapes` motifs (continuous Cookie ↔ Clover morph) on the Home hero,
    engine banner, empty state and placeholder thumbnails, via `androidx.graphics:graphics-shapes`.
  - The expressive `LoadingIndicator` / `ContainedLoadingIndicator` replace plain spinners; running
    downloads show a `LinearWavyProgressIndicator`.
  - Emphasized typography roles for titles and section headers.
  - A custom spring-sliding **segmented toggle** for Video/Audio and theme (System/Light/Dark).
  - Staggered spring entrances for history cards, spring press-feedback on buttons, and expressive
    fade/scale transitions between destinations.
  - All motion is guarded by a reduced-motion check (system animation scale = 0).

### Added
- Choose Video or Audio for YouTube directly on Home via the segmented toggle (audio saved as `.m4a`).

### Notes
- Compose is now pinned explicitly (no BOM) to the `1.11` set with `material3:1.5.0-alpha`, which
  still targets `compileSdk 35` / AGP 8.7. Release-vital lint is skipped due to an AGP-8.7 lint bug
  against Compose 1.11 metadata; static analysis remains enforced by detekt + Spotless.

## [1.0.0] - 2026-07-26

### Added
- Download publicly accessible videos from **Instagram**, **YouTube** and **TikTok**
  via the bundled **yt-dlp** engine (youtubedl-android: yt-dlp + ffmpeg + aria2c).
- **Android share integration** — pick *Flipper the Ripper* from any app's share sheet
  (`ACTION_SEND`, `text/plain`); the link is imported and (optionally) downloaded automatically.
- **Clipboard detection** — on launch, a supported link on the clipboard is offered as a
  one-tap download.
- **Automatic flow** — analyse URL → detect platform → resolve metadata → download, with minimal taps.
- **Title-based filenames** — files are named after the video title with illegal filesystem
  characters replaced and UTF-8-safe truncation.
- **MediaStore output** — downloads land in the public *Movies* (or *Music* for audio) collection
  and appear immediately in the gallery, Google Photos and file managers. Scoped storage on
  Android 10+, legacy public-dir + media-scan on Android 7–9.
- **Background downloads** via WorkManager + a foreground (data-sync) service — survive
  screen-lock, app-minimise and rotation, with a live progress notification.
- **Material 3 UI** (Jetpack Compose): Home (URL, preview, title, platform, progress),
  History and Settings, with dynamic color and light/dark themes.
- **Typed error handling** — private video, login required, region blocked, rate-limited/stale,
  network, invalid link and cancellation are surfaced distinctly (ported from the
  inspector-rust stderr classifiers).
- **Engine update** — update the bundled yt-dlp at runtime to fix broken extractors.
- Signed release builds, GitHub Actions CI (build, lint, detekt, unit tests, coverage) and an
  automated tag-driven release workflow.

[Unreleased]: https://github.com/pepperonas/flipper-the-ripper/compare/v1.1.2...HEAD
[1.1.2]: https://github.com/pepperonas/flipper-the-ripper/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/pepperonas/flipper-the-ripper/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/pepperonas/flipper-the-ripper/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/pepperonas/flipper-the-ripper/releases/tag/v1.0.0
