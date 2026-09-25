<!--# block name="none" --><!--# endblock -->
# Flipper the Ripper — Free Android Video Downloader

> Share a link. Keep the video. Free, open-source (MIT) Android app that saves publicly accessible videos from YouTube, Instagram, TikTok, Facebook, X and Dailymotion to the phone's gallery. No account, no ads, no tracking.

This is the Markdown version of https://flipper-the-ripper.celox.io/ for agents and text tools. A short summary with every link lives at https://flipper-the-ripper.celox.io/llms.txt.

## Download

- **Newest APK:** https://flipper-the-ripper.celox.io/download (always redirects to the current release file)
- **Current version:** <!--# include virtual="/ssi/version.txt" stub="none" --> · <!--# include virtual="/ssi/size.txt" stub="none" --> · released <!--# include virtual="/ssi/date.txt" stub="none" -->
- **APK SHA-256:** `<!--# include virtual="/ssi/sha.txt" stub="none" -->`
- **Release data as JSON:** https://flipper-the-ripper.celox.io/latest.json
- **Requirements:** Android 7.0 or later, 64-bit ARM (every phone since about 2016). One file for all of them.

## Features

- **Share, and it starts** — pick the app in any share sheet; the download starts at once and appears at the top of the list. A copied link is offered when the app opens.
- **A queue you can manage** — one download at a time, in the order shown. Pause and resume from the byte it stopped at, drag waiting ones into a new order, swipe finished ones away — with undo.
- **Choose the quality** — Best, 1080p, 720p, 480p or audio only. Tiers a video does not reach are greyed out; a remembered default keeps shared links one tap.
- **Keeps going in the background** — locked screen, minimised app, rotated phone; one notification for the whole batch.
- **Straight into your gallery** — files land in Movies, named after the video title, visible in Google Photos and every file manager.
- **Stays working** — the bundled yt-dlp updates itself; the app tells you when a new release is out.
- **Five languages** — English, German, Spanish, Italian, French.

## Install

1. Open https://flipper-the-ripper.celox.io/download on the phone.
2. Open the downloaded file. Android asks once whether the browser may install apps — allow it.
3. In YouTube, Instagram, TikTok, Facebook, X or Dailymotion tap **Share** and pick **Flipper the Ripper**. Updates install over the existing app.

## Verify

- Compare the APK's SHA-256 with the value above (also in `SHA256SUMS.txt` of each release).
- Signing certificate SHA-256, the same for every release since 1.0.0: `1fc3904fd80eb8135b25ee15fe62ec3b74545eba360298c152f712646fd910cb` — check with `apksigner verify --print-certs <file>.apk`.

## FAQ

**Is Flipper the Ripper free?** Yes. It is free and open source under the MIT licence, with no ads, no account and no tracking.

**Why is it not on Google Play?** The Play Store does not allow video downloaders or apps that update their own download engine. The app is distributed as a signed APK through GitHub Releases, like NewPipe and Seal.

**Which phones does it run on?** Android 7.0 or later on 64-bit ARM, which is every Android phone sold since about 2016. Version 1.8.3 is the last release for 32-bit devices.

**How do I update?** Install the newer APK over the existing app; your downloads and settings stay. The app shows a notice on its Home screen when a new release is out, and every release is signed with the same key.

**Which sites can it download from?** YouTube, Instagram, TikTok, Facebook, X and Dailymotion — publicly accessible videos only. It does not bypass DRM, paywalls or logins you do not have.

**How do I know the APK is genuine?** Compare its SHA-256 with the value above and check the signing certificate with `apksigner verify --print-certs`; the certificate is the same for every release since 1.0.0.

## Limits

- Only for publicly accessible content and personal, lawful use.
- No playlists, no subtitles.

## Links

- Source code: https://github.com/pepperonas/flipper-the-ripper
- Changelog: https://flipper-the-ripper.celox.io/changelog.md
- Agent skill (download, verify, install): https://flipper-the-ripper.celox.io/skills/get-flipper-the-ripper/SKILL.md
- Agent resource catalog: https://flipper-the-ripper.celox.io/.well-known/ai-catalog.json
- Licence (MIT): https://github.com/pepperonas/flipper-the-ripper/blob/main/LICENSE
- Support the project: https://www.paypal.com/donate/?business=martin.pfeffer@celox.io&currency_code=EUR&item_name=Flipper%20the%20Ripper
- Author: Martin Pfeffer, https://celox.io — Imprint https://celox.io/impressum/ · Privacy https://celox.io/datenschutz/
