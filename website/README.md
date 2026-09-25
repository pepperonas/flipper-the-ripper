# Product page — flipper-the-ripper.celox.io

Static page, no build step. `flippertheripper.celox.io` and `ftr.celox.io` answer with a 301 to it.

| File | Purpose |
|------|---------|
| `index.html`, `styles.css`, `app.js`, `i18n.js` | The page. English in the markup; `i18n.js` carries German, Spanish, Italian and French, picked from the browser languages (selector top right, remembered per device). Colours are the app's dark scheme from `ui/theme/Color.kt`. |
| `assets/` | Hero background (`docs/banner.png` as WebP/JPEG), screenshots, icon, OG image. |
| `server/ftr-latest.py` + `.service`/`.timer` | Every 15 min on the VPS: asks GitHub for the latest release and `CHANGELOG.md`, writes `latest.json` and `changelog.md` next to the page (the changelog dialog reads the latter) and `/etc/nginx/ftr-download.conf` (`/download` → 302 to the newest APK). Visitors never call GitHub's API; a failed check keeps the last good state. |
| `server/nginx/` | Vendored vhost (one Let's Encrypt certificate for all three names). |
| `deploy.sh` | `./website/deploy.sh` ships the static files; `./website/deploy.sh server` also installs the timer and the vhost. |

**The newest APK is always offered without touching this folder:** a new GitHub release is picked up
by the timer within 15 minutes — both the button on the page and the stable link
<https://flipper-the-ripper.celox.io/download>. Without JavaScript the button falls back to the
latest-release page on GitHub.

Asset URLs carry `?v=N` and are served `immutable` — bump the number when a file changes.
