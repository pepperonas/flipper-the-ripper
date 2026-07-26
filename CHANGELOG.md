# Changelog

All notable changes to **Flipper the Ripper** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/pepperonas/flipper-the-ripper/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/pepperonas/flipper-the-ripper/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/pepperonas/flipper-the-ripper/releases/tag/v1.0.0
