# Security Policy

Thank you for helping keep **Flipper the Ripper** and its users safe.

## Supported Versions

Only the **latest released version** of the app receives security updates. Please make sure you are on the most recent release before reporting an issue, and before relying on any fix.

| Version        | Supported          |
| -------------- | ------------------ |
| Latest release | :white_check_mark: |
| Older releases | :x:                |

Since **1.9.0** releases ship as a single 64-bit ARM build. **1.8.3 is the last release for 32-bit devices**; it receives no further updates.

## Reporting a Vulnerability

**Please do not open a public GitHub issue for security vulnerabilities.** Public disclosure before a fix is available puts users at risk.

Instead, report privately by email to:

**<martin.pfeffer@celox.io>**

Please include as much detail as you can:

- A description of the vulnerability and its potential impact.
- Steps to reproduce, or a proof of concept.
- The app version, Android version, and device/emulator where you observed it.
- Any relevant logs, stack traces, or screenshots.

You may optionally use GitHub's [private security advisory](https://github.com/pepperonas/flipper-the-ripper/security/advisories/new) feature instead of email.

### Response Expectations

- We aim to acknowledge your report **within 5 business days**.
- We will keep you informed as we investigate and work on a fix.
- Once a fix is released, we are happy to credit you in the release notes (unless you prefer to remain anonymous).

Please give us a reasonable amount of time to address the issue before any public disclosure.

## Verifying a Release

The app is distributed only as a sideloaded APK, so the signature is the one thing that ties a file to this project. Every release since 1.0.0 is signed with the same key:

```
Signing certificate SHA-256
1fc3904fd80eb8135b25ee15fe62ec3b74545eba360298c152f712646fd910cb
```

Check a downloaded APK with the Android SDK's `apksigner`:

```bash
apksigner verify --print-certs flipper-the-ripper-v1.9.0.apk | grep SHA-256
```

A different digest means the file was not built by this project's release workflow — do not install it. Each release also carries a `SHA256SUMS.txt` for the file itself, and the workflow refuses to publish an APK whose certificate does not match the digest above.

## Scope & Security Posture

Flipper the Ripper is designed with a conservative, privacy-respecting posture:

- **Public content only.** The app downloads only **publicly accessible** videos. It does **not** bypass DRM, paywalls, or access controls. The one exception is content the *user's own* account can see — see the next point.
- **Passwords are never handled.** The optional Instagram sign-in (*Settings → Instagram*) opens **Instagram's own login page** in a WebView. The app never sees the password. What it keeps is the resulting **session cookie**, in Android's WebView cookie store — exactly what a browser keeps after you sign in — so the hidden extractor WebView can read reels the user's account is allowed to see. *Sign out* in Settings expires those cookies. The cookie never leaves the device: the optional server backend is not signed in to anything and receives only the video URL.
- **No accounts, no telemetry.** The app has no user account of its own, no analytics or crash-reporting SDK, and no advertising. The only outbound requests are to the video platforms, to GitHub (release check, yt-dlp updates) and, if configured, to the user's own server.
- **Minimal permissions.** The app requests only what it needs:
  - `INTERNET` — to fetch publicly available media.
  - `ACCESS_NETWORK_STATE` — to know whether a connection exists before starting a download.
  - `POST_NOTIFICATIONS` — to show download progress and completion (runtime-requested on Android 13+).
  - `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` — to keep downloads running while the app is in the background.
  - Legacy `WRITE_EXTERNAL_STORAGE` — declared with `android:maxSdkVersion="28"` only, for Android 7–9; modern Android versions use scoped storage through MediaStore.
- **Bundled yt-dlp engine.** The app ships a bundled yt-dlp engine (via youtubedl-android). It is **updated at runtime** (throttled, at app start and when a link is shared in) to pick up upstream fixes, including security-relevant ones, without a full app update; a manual update button remains in Settings.
- **Hidden WebView, fenced in.** The extractor WebView loads exactly one page and refuses any navigation that is not `http(s)` — a platform's deep link into its native app (`snssdk1340://…`, `intent://…`) is never followed, so the extractor cannot be used to launch other apps on the user's behalf.

## Out of Scope

The following are generally **not** considered vulnerabilities in this project:

- Behavior of remote video platforms (YouTube, Instagram, TikTok, Facebook, X, Dailymotion) or their changing APIs and rate limits.
- Issues in third-party dependencies that are already publicly known and tracked upstream (please report those to the respective projects; we will update our bundled versions accordingly).
- The ability to download publicly accessible content — this is the app's intended function.
- User misuse of downloaded content (respecting copyright and platform terms of service is the user's responsibility).

Thank you for practicing responsible disclosure.
