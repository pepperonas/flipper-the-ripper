# Changelog

All notable changes to **Flipper the Ripper** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/pepperonas/flipper-the-ripper/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/pepperonas/flipper-the-ripper/releases/tag/v1.0.0
