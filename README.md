<div align="center">

# 🎬 Flipper the Ripper

**A modern, open-source Android app to download publicly accessible videos from Instagram, YouTube and TikTok.**

[![CI](https://img.shields.io/github/actions/workflow/status/pepperonas/flipper-the-ripper/ci.yml?branch=main&label=build&logo=github)](https://github.com/pepperonas/flipper-the-ripper/actions/workflows/ci.yml)
[![Tests](https://img.shields.io/github/actions/workflow/status/pepperonas/flipper-the-ripper/ci.yml?branch=main&label=tests&logo=junit5)](https://github.com/pepperonas/flipper-the-ripper/actions/workflows/ci.yml)
[![Coverage](https://img.shields.io/badge/coverage-%E2%89%A580%25-brightgreen?logo=kotlin)](https://github.com/pepperonas/flipper-the-ripper/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-Jetpack%20Compose-3DDC84?logo=android&logoColor=white)](https://developer.android.com/jetpack/compose)
[![API](https://img.shields.io/badge/API-24%2B-blue?logo=android)](https://developer.android.com/tools/releases/platforms)
[![Release](https://img.shields.io/github/v/release/pepperonas/flipper-the-ripper?logo=github)](https://github.com/pepperonas/flipper-the-ripper/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/pepperonas/flipper-the-ripper/total?logo=github)](https://github.com/pepperonas/flipper-the-ripper/releases)
[![Release workflow](https://img.shields.io/github/actions/workflow/status/pepperonas/flipper-the-ripper/release.yml?label=release&logo=githubactions)](https://github.com/pepperonas/flipper-the-ripper/actions/workflows/release.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

> [!IMPORTANT]
> **Only download content you have the right to save.** Flipper the Ripper is intended for
> **publicly accessible** content and for personal, lawful use. It does **not** bypass DRM,
> paywalls, or access restrictions. Please respect the terms of service of each platform and
> applicable copyright law. See [Legal & responsible use](#-legal--responsible-use).

## 📸 Screenshots

Material 3 **Expressive** UI with spring physics, shape-morphing motifs and dynamic color — in dark and light.

| Home (dark) | Settings (dark) | Settings (light) | History |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/home.png" width="200"/> | <img src="docs/screenshots/settings.png" width="200"/> | <img src="docs/screenshots/light.png" width="200"/> | <img src="docs/screenshots/history.png" width="200"/> |

## ✨ Features

- **Share integration** — tap *Share* in Instagram / TikTok / YouTube and pick **Flipper the Ripper**; the link is imported automatically.
- **Clipboard detection** — copied a link instead? On launch the app offers to download a supported URL found on the clipboard.
- **One-tap flow** — analyse → detect platform → resolve metadata → download, with as few taps as possible (auto-download on share is configurable).
- **Title-based filenames** — files are named after the video title (`Wie man Android Apps entwickelt.mp4`) with illegal characters sanitised.
- **Shows up everywhere** — saved via **MediaStore** into the public *Movies* folder; instantly visible in Gallery, Google Photos and file managers.
- **True background downloads** — keep going while the screen is locked, the app is minimised, or the device is rotated (WorkManager + foreground service).
- **Audio-only mode** for YouTube (`.m4a`).
- **Robust error messages** — private video, login required, region blocked, rate-limited, network error, invalid link, cancelled.
- **Material 3 Expressive** — spring-based motion physics (`MotionScheme.expressive()`), shape-morphing `MaterialShapes` motifs, the expressive `LoadingIndicator`, emphasized typography, a spring-sliding segmented toggle, staggered list entrances, and expressive screen transitions. Dynamic color + light/dark, all guarded by `prefers-reduced-motion`.
- **Update the engine** in-app to fix broken extractors.

## 📥 Download

Grab the latest signed APK from the [**Releases**](https://github.com/pepperonas/flipper-the-ripper/releases/latest) page and sideload it.

> Not on Google Play by design — the Play Store prohibits video-downloader apps and runtime binary
> updates. Distribution is via GitHub Releases / F-Droid-style sideloading (like NewPipe and Seal).

## 🧠 The download engine — and why

Flipper the Ripper reuses the **policy and stability logic** of the desktop project
[`inspector-rust`](https://github.com/pepperonas) and runs it on Android.

`inspector-rust` is a Tauri desktop app that shells out to the external **`yt-dlp`** + **`ffmpeg`**
CLIs — there is no in-app extraction engine, and **stock Android cannot execute a `yt-dlp` binary
or run Python**. A literal 1:1 port is therefore impossible. What *is* portable is the thin policy
layer, which this app reimplements faithfully in Kotlin:

- **Platform detection** (host-substring match) → [`UrlParser`](app/src/main/kotlin/io/celox/flipperripper/domain/util/UrlParser.kt)
- **yt-dlp argument strategy** — prefer H.264 + m4a → mp4 for universal playback, audio = m4a q0, `--no-playlist`/`--no-mtime`, YouTube SABR workaround `player_client=default,ios,web_safari`, the `--` flag-injection guard, `%(title).100B [%(id)s]` naming → [`YtDlpArgsBuilder`](app/src/main/kotlin/io/celox/flipperripper/data/engine/YtDlpArgsBuilder.kt)
- **Error taxonomy** — `is_bot_block` / `looks_stale_or_rate_limited` plus private/region/unavailable/network buckets → [`ErrorClassifier`](app/src/main/kotlin/io/celox/flipperripper/data/engine/ErrorClassifier.kt)

The engine underneath is [**youtubedl-android**](https://github.com/JunkFood02/youtubedl-android),
which bundles the **real yt-dlp + ffmpeg + aria2c** as native libraries — the same engine that
powers apps like Seal. So the ported policy layer drives the *identical* extractor, giving the
same reliability across all three platforms.

### Decision: reuse vs. JNI vs. re-port

| Option | Verdict |
|--------|---------|
| **Port the Rust as a native lib (JNI)** | ❌ Not useful — `inspector-rust`'s core is an `rlib` (no `cdylib`) and contains **no extraction code**, only a ~50-line subprocess wrapper around external CLIs. Nothing to port. |
| **Subprocess yt-dlp (as on desktop)** | ❌ Impossible on non-rooted Android (no Python, no arbitrary `exec`). |
| **Bundle yt-dlp via youtubedl-android** ✅ | **Chosen.** Ships the real yt-dlp as a native payload; reuses the desktop policy layer verbatim; self-updates at runtime. Trade-off: larger APK (~70 MB) and ARM-only. |
| Pure-JVM extractor (NewPipeExtractor) | ❌ Strong for YouTube only; Instagram unsupported, TikTok fragile — would not meet the requirement. |

**Cookie fallback deviation:** the desktop `--cookies-from-browser` retry has no Android equivalent
(there are no desktop browser profiles). Login walls are surfaced as a typed `LoginRequired` error
instead; user-supplied cookie files are on the [roadmap](#-roadmap).

## 🏛️ Architecture

Clean Architecture + MVVM, single Gradle module with strictly layered packages (the `domain` layer
has **zero** Android dependencies and is 100%-unit-testable).

```mermaid
flowchart TD
    subgraph UI["ui · Jetpack Compose + ViewModels"]
        Home[HomeScreen] --> HVM[HomeViewModel]
        Hist[HistoryScreen] --> HiVM[HistoryViewModel]
        Set[SettingsScreen] --> SVM[SettingsViewModel]
    end
    subgraph DOMAIN["domain · pure Kotlin"]
        UC[Use cases] --> RIF[Repository interfaces]
        MODELS[Models · EngineResult · DownloadError]
    end
    subgraph DATA["data"]
        REPO[Repository impls]
        ENGINE["engine · YtDlpEngine\nArgsBuilder · ErrorClassifier · FilenameSanitizer"]
        WORK["work · DownloadWorker\n(foreground service)"]
        ROOM[(Room · history)]
        DS[(DataStore · settings)]
        MEDIA[MediaStoreWriter]
    end
    YTDLP[["youtubedl-android\n(yt-dlp + ffmpeg + aria2c)"]]

    HVM --> UC
    HiVM --> UC
    SVM --> UC
    UC --> RIF
    REPO -. implements .-> RIF
    REPO --> ENGINE
    REPO --> ROOM
    REPO --> WORK
    WORK --> ENGINE
    WORK --> MEDIA
    ENGINE --> YTDLP
    REPO --> DS
```

**Why WorkManager + a foreground service?** Downloads can take minutes and must survive process
death, minimisation and rotation. A bare service wouldn't give persistence, constraints, retry or
observable progress; WorkManager provides all of that and runs a foreground (`dataSync`) service
under the hood for the long-running case.

### Tech stack

Kotlin 2.0 · Jetpack Compose + **Material 3 Expressive** (material3 1.5.0-alpha, `graphics-shapes`) ·
MVVM + Clean Architecture · Coroutines + Flow · Hilt · Navigation Compose · Room · DataStore ·
WorkManager · Kotlin Serialization · Coil · youtubedl-android · **no XML layouts**.

> The Expressive component + motion APIs (`MotionScheme`, `MaterialShapes`, `LoadingIndicator`) are
> currently in the `material3:1.5.0-alpha` line, pinned explicitly (no Compose BOM) to the
> Compose 1.11 set that still targets `compileSdk 35` / AGP 8.7.

## 🛠️ Build

**Requirements:** JDK 17, Android SDK (compile/target **35**), Android Studio Ladybug+ (optional).

```bash
git clone https://github.com/pepperonas/flipper-the-ripper.git
cd flipper-the-ripper

./gradlew assembleDebug          # debug APK → app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests
./gradlew koverVerifyDebug       # coverage gate (≥ 80% line coverage)
./gradlew spotlessCheck detekt   # formatting + static analysis
./gradlew connectedDebugAndroidTest   # instrumentation tests (device/emulator, ARM image)
```

> The emulator must use an **ARM system image** — the bundled yt-dlp native libraries ship for
> `arm64-v8a` / `armeabi-v7a` only (no x86/x86_64).

### Signed release builds

Signing is wired via `keystore.properties` (local, git-ignored) with an environment-variable
fallback for CI. See [Signing](#-signing).

```bash
./gradlew assembleRelease        # signed APK → app/build/outputs/apk/release/
```

## 🔏 Signing

Signing secrets are **never** committed (`*.jks`, `keystore.properties` are git-ignored).

- **Locally:** place `release.jks` + `keystore.properties` (`storeFile`, `storePassword`,
  `keyAlias`, `keyPassword`) in the project root.
- **CI / GitHub Actions:** the release workflow decodes the keystore from the `KEYSTORE_BASE64`
  secret and reads `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. The build's signing config
  falls back to these environment variables when `keystore.properties` is absent.

## 🚀 Releases

Releases are automated. Pushing a `v*` tag triggers the
[release workflow](.github/workflows/release.yml), which builds a signed APK and publishes a
GitHub Release with notes.

```bash
# bump versionCode/versionName in app/build.gradle.kts, commit, then:
git tag v1.0.1 && git push origin v1.0.1
```

Versioning follows [Semantic Versioning](https://semver.org/).

## 🗺️ Roadmap

- [ ] User-supplied cookie file for login-gated public content
- [ ] Playlist / multi-item downloads
- [ ] Quality / format picker before download
- [ ] Subtitle download
- [ ] Download queue management (pause/resume, reorder)
- [ ] F-Droid distribution
- [ ] Additional platforms supported by yt-dlp (opt-in)

## ❓ FAQ

**Is this on Google Play?** No — Play policy prohibits these apps. Sideload the signed APK from Releases.

**Why is the APK ~70 MB?** It bundles the real yt-dlp + ffmpeg + Python runtime as native libraries so extraction works fully offline of any server.

**A download fails with "rate-limited or out of date".** The extractor changed upstream. Open **Settings → Update yt-dlp** to fetch the latest engine, then retry.

**Instagram/YouTube says login required.** The content is behind an auth/anti-bot wall. Only publicly accessible content is supported; cookie import is on the roadmap.

**Where do files go?** The public *Movies/FlipperTheRipper* folder (audio → *Music/FlipperTheRipper*), visible in Gallery/Photos/file managers.

**Does it work on an x86 emulator?** No — use an ARM system image or a physical device.

## 🧯 Troubleshooting

| Symptom | Fix |
|---------|-----|
| "The download engine is still initialising" | First launch unpacks the native payload; wait a few seconds and retry. |
| Downloads don't start in the background | Allow notifications and disable battery optimisation for the app. |
| Repeated failures on one platform | **Settings → Update yt-dlp**. |
| Nothing saved to the gallery | Check storage; on Android 7–9 grant the storage permission when prompted. |
| Build fails on `kspDebugKotlin` | Ensure `ksp.useKSP2=false` (set in `gradle.properties`). |

## 🤝 Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) and the
[Code of Conduct](CODE_OF_CONDUCT.md). All CI checks (build, lint, detekt, unit tests, ≥ 80% coverage)
must pass. Security issues: see [SECURITY.md](SECURITY.md).

## ⚖️ Legal & responsible use

Flipper the Ripper is a tool for downloading **publicly accessible** content for **personal, lawful**
use. By design it:

- does **not** circumvent DRM, encryption, paywalls, or access controls;
- does **not** store platform credentials and requests only minimal permissions;
- surfaces (rather than works around) login/region walls.

You are responsible for complying with the **terms of service** of each platform and with
**copyright** and other applicable laws in your jurisdiction. Downloading copyrighted material
without permission may be unlawful. The authors accept no liability for misuse. If you are a
rights holder with a concern, please open an issue or contact the maintainer.

## 📄 License

MIT © 2026 **Martin Pfeffer**. See [LICENSE](LICENSE).

Built on the excellent [yt-dlp](https://github.com/yt-dlp/yt-dlp) and
[youtubedl-android](https://github.com/JunkFood02/youtubedl-android) projects.
