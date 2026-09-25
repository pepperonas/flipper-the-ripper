---
name: get-flipper-the-ripper
description: Download, verify and install the newest Flipper the Ripper APK — the free, open-source Android app that saves public videos from YouTube, Instagram, TikTok, Facebook, X and Dailymotion. Use when someone asks for the app, its latest version, a download link, or how to check the APK is genuine.
license: MIT
---

# Get Flipper the Ripper

Flipper the Ripper is a free, open-source (MIT) Android app. It is **not on Google Play** (the Play
Store does not allow video downloaders); it is distributed as one signed APK per release.

## 1. Find the newest release

`GET https://flipper-the-ripper.celox.io/latest.json` returns:

```json
{ "version": "v1.11.0", "published": "2026-09-25T…Z", "notes": "https://github.com/…/releases/tag/v1.11.0",
  "assets": [ { "target": "android", "name": "flipper-the-ripper-v1.11.0.apk",
                "url": "https://github.com/…", "size": 56270809, "sha256": "…" } ] }
```

The file is refreshed from GitHub Releases every 15 minutes. On the page itself, browsers that support
WebMCP expose the same data as the tools `get_latest_release`, `get_download_url` and `get_checksums`.

## 2. Download

- Stable link, always the newest APK: <https://flipper-the-ripper.celox.io/download> (302 to the release asset).
- Requirements: Android 7.0 or later, 64-bit ARM (every Android phone since about 2016). One file, no choice.

## 3. Verify

- The file's SHA-256 must equal `assets[0].sha256` from `latest.json` (also in `SHA256SUMS.txt` of the release).
- The signing certificate must be `1fc3904fd80eb8135b25ee15fe62ec3b74545eba360298c152f712646fd910cb`:
  `apksigner verify --print-certs flipper-the-ripper-vX.Y.Z.apk | grep SHA-256`. It is the same for every
  release since 1.0.0; anything else is not this app.

## 4. Install

1. Open the downloaded APK on the phone.
2. Allow the browser to install apps when Android asks (once).
3. In any supported app tap **Share** and pick **Flipper the Ripper**. Updates install over the existing app.

## Limits

- Publicly accessible content and personal, lawful use only; no DRM, paywall or login bypass.
- No playlists, no subtitles.

More: [product page](https://flipper-the-ripper.celox.io/) · [Markdown version](https://flipper-the-ripper.celox.io/index.md) · [changelog](https://flipper-the-ripper.celox.io/changelog.md) · [source](https://github.com/pepperonas/flipper-the-ripper)
