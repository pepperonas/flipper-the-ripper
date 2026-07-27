# Changelog

All notable changes to **Flipper the Ripper** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.2.2] - 2026-07-27

### Fixed
- **The app animated continuously while idle.** The expressive shape motif looped unconditionally, so
  with nothing happening the Home hero, the empty history state and — once per list item — every
  placeholder thumbnail and platform badge kept animating forever. Looping motion is now tied to real
  work: it runs only while a download is queued or running, and settles into a resting shape when the
  last one finishes. One-shot effects (card entrance, screen transition, press feedback) are unchanged,
  since they end by themselves.
- **The two ways of reaching a tab behaved differently.** The automatic jump to History after starting a
  download called `navigate()` with no options at all, while the bottom bar used
  `popUpTo(start){saveState}` + `launchSingleTop` + `restoreState`. The same destination could therefore
  be pushed twice onto the back stack, and the jump bypassed tab state save/restore — letting a stale
  saved state later be restored over the live one. Both paths now go through one `navigateToTab()`.
- **Screen transitions overlapped and appeared to zoom.** Timings were asymmetric (400 ms in against
  200 ms out, with a scale only on the way back), so both screens were visible at once. Replaced with a
  proper Material 3 fade-through — the outgoing screen leaves in 90 ms and the incoming one follows —
  and reduced motion now disables the transition entirely.

### Added
- Project banner in the README.

## [1.2.1] - 2026-07-27

### Fixed
- **The app crashed immediately on every launch (release builds).** R8 renamed Apache Commons
  Compress's `ZipExtraField` implementations and made them abstract, because nothing constructs them
  directly — but its `ExtraFieldUtils` static initialiser instantiates them *reflectively*. The
  registry therefore threw `class ...zip.a is not a concrete class`, `YoutubeDL.init()` died with
  `ExceptionInInitializerError`, and the app was stuck in an unrecoverable launch-crash loop. Added the
  keep rules that keep those classes concrete, named and default-constructible. Debug builds were
  unaffected (no shrinking), which is why it only ever showed up in shipped APKs.
- **Downloads never finished, even when they visibly progressed.** The yt-dlp options ended with the
  `--` end-of-options guard, but youtubedl-android appends its *own* flags after ours — so
  `--ffmpeg-location <path>` was demoted to a positional argument and yt-dlp tried to download the
  ffmpeg binary path as a URL. Every run fetched the real video and then failed with
  `ERROR: [generic] '…/libffmpeg.so' is not a valid URL`, so nothing ever reached the gallery. The
  guard now sits only where the app owns argument order; the engine validates the URL scheme instead.
- **YouTube downloads failed at the media step.** The player-client list included clients that cannot
  work on a stock device: `ios`/`tv_simply` require a **GVS PO Token** ("…require a GVS PO Token which
  was not provided"), and the `web*` clients require the **n-signature JavaScript challenge** that
  Android has no runtime for. The app now uses `android_vr` — the one client needing neither — and the
  metadata and download steps use the *same* list, so a preview can no longer resolve via a client
  whose formats the download cannot fetch.
- **Filenames fell back to the raw URL.** A download started from a shared link carries the URL as its
  title until metadata resolves; if that lookup failed, files were saved as
  `https www.youtube.com watch v=….mp4`. yt-dlp already writes the real title into the file it
  produces, so that is now used before falling back to the URL, and the history entry is updated to
  match.
- Engine start-up can no longer take the app down: initialisation failures (including `Error`s) become
  a typed `EngineNotReady` result, a malformed server address is reported instead of thrown (it used to
  brick the app on launch with no way to reach Settings), and the application scope has an exception
  handler.

### Changed
- Backend: YouTube player clients aligned with the app's, `/api/resolve` now uses the same extractor
  args as the download, and the YouTube check is hostname-based rather than a substring match on the
  whole URL.

### Added
- Regression tests for all of the above (111 unit tests), including a guard asserting the R8 keep rules
  exist — the crash was a build-configuration bug that no ordinary unit test could have caught.

### Known limitations
- **Platform IP blocking is outside the app's control.** YouTube now answers the server backend's
  datacenter IP with "Sign in to confirm you're not a bot", and TikTok with "Your IP address is
  blocked" — for *any* player client, and a PO-token provider does not change it (it attests the
  stream, not the IP). **On-device downloads use your own connection and are therefore the more
  reliable route for YouTube.** Instagram serves an empty media response without login. Facebook works
  through the backend.

## [1.2.0] - 2026-07-26

### Added
- **Server backend (recommended) — reliably downloads YouTube & TikTok.** A new optional backend
  (`backend/`, FastAPI) runs the full yt-dlp toolchain on a server — a `deno` JS runtime for
  YouTube's n-sig challenge, `curl_cffi` impersonation for TikTok, ffmpeg for merging — then streams
  the finished file to the app. This solves the platform anti-bot walls that stock Android can't
  (v1.1.2's Known limitations). **Verified end-to-end:** YouTube downloads at full 4K quality and
  TikTok downloads via the deployed backend.
  - **Settings → Download source** toggles **On device** ⇄ **Server** (with URL + API-key fields).
    The app ships pointing at a server when one is baked in (git-ignored `backend.properties`),
    otherwise stays on-device; the user can configure a server at any time.
  - New `RemoteYtDlpEngine` (OkHttp) + `RoutingYtDlpEngine` pick the source live, so switching in
    Settings takes effect with no restart. Cancellation, progress and the typed error taxonomy are
    preserved across both paths.
  - Backend: `POST /api/resolve`, `POST /api/jobs`, `GET /api/jobs/{id}`, `GET /api/jobs/{id}/file`;
    `X-API-Key` auth; loopback + nginx TLS; nightly yt-dlp auto-update. Deploy docs in `backend/README.md`.

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

[Unreleased]: https://github.com/pepperonas/flipper-the-ripper/compare/v1.2.2...HEAD
[1.2.2]: https://github.com/pepperonas/flipper-the-ripper/compare/v1.2.1...v1.2.2
[1.2.1]: https://github.com/pepperonas/flipper-the-ripper/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/pepperonas/flipper-the-ripper/compare/v1.1.2...v1.2.0
[1.1.2]: https://github.com/pepperonas/flipper-the-ripper/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/pepperonas/flipper-the-ripper/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/pepperonas/flipper-the-ripper/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/pepperonas/flipper-the-ripper/releases/tag/v1.0.0
