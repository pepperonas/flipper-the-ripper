# Security Policy

Thank you for helping keep **Flipper the Ripper** and its users safe.

## Supported Versions

Only the **latest released version** of the app receives security updates. Please make sure you are on the most recent release before reporting an issue, and before relying on any fix.

| Version        | Supported          |
| -------------- | ------------------ |
| Latest release | :white_check_mark: |
| Older releases | :x:                |

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

## Scope & Security Posture

Flipper the Ripper is designed with a conservative, privacy-respecting posture:

- **Public content only.** The app downloads only **publicly accessible** videos. It does **not** bypass DRM, paywalls, private accounts, or other access controls.
- **No credentials.** The app does **not** collect, store, or transmit account credentials or authentication tokens.
- **Minimal permissions.** The app requests only what it needs:
  - `INTERNET` — to fetch publicly available media.
  - Notifications (`POST_NOTIFICATIONS`) — to show download progress and completion.
  - Foreground service of type **data sync** — to keep downloads running reliably.
  - Legacy `WRITE_EXTERNAL_STORAGE` — declared with `android:maxSdkVersion="28"` only, for compatibility with older devices; modern Android versions use scoped storage.
- **Bundled yt-dlp engine.** The app ships a bundled yt-dlp engine (via youtubedl-android). This engine **can be updated at runtime** to pick up upstream fixes, including security-relevant ones, without requiring a full app update.

## Out of Scope

The following are generally **not** considered vulnerabilities in this project:

- Behavior of remote video platforms (Instagram, YouTube, TikTok, Facebook) or their changing APIs and rate limits.
- Issues in third-party dependencies that are already publicly known and tracked upstream (please report those to the respective projects; we will update our bundled versions accordingly).
- The ability to download publicly accessible content — this is the app's intended function.
- User misuse of downloaded content (respecting copyright and platform terms of service is the user's responsibility).

Thank you for practicing responsible disclosure.
