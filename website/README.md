# Product page — flipper-the-ripper.celox.io

The app's website: <https://flipper-the-ripper.celox.io>. `flippertheripper.celox.io` and
`ftr.celox.io` answer with a 301 to it (path and query kept).

A static page with no build step, served by nginx on the VPS. Two things keep it current without
anyone touching this folder: a **server-side timer** that follows GitHub Releases and the
changelog, and **nginx SSI**, which puts those facts into the HTML at serve time. The page is written
for three readers — people, search engines, and **AI agents** — and each gets the same facts.

- [What is where](#what-is-where)
- [How "always the newest APK" works](#how-always-the-newest-apk-works)
- [For agents and tools](#for-agents-and-tools)
- [Languages](#languages)
- [Design notes](#design-notes)
- [Privacy and security](#privacy-and-security)
- [Deploying](#deploying)
- [Setting it up from scratch](#setting-it-up-from-scratch)
- [Checking it](#checking-it)
- [Traps we have already stepped in](#traps-we-have-already-stepped-in)

## What is where

| File | Purpose |
|------|---------|
| `index.html` | The page. English in the markup (what crawlers and agents read without JavaScript). Contains the SSI includes for the release facts and the JSON-LD. |
| `styles.css` | All styling. Colours are the app's dark scheme from `app/src/main/kotlin/…/ui/theme/Color.kt` as tokens on `:root`. |
| `app.js` | Language switching and menu, the download button and meta line from `latest.json`, licence dialog, sticky bar, reveal animation. |
| `i18n.js` | German, Spanish, Italian and French strings, keyed like the `data-i18n` attributes. A missing key falls back to the English in the markup. |
| `changelog.js` | Changelog dialog: loads `changelog.md` (same origin) and renders it with a small escaping Markdown renderer. |
| `index.md` | The page as Markdown for agents, with the same SSI includes. |
| `llms.txt` | One-screen summary for LLMs and agents in the [llms.txt](https://llmstxt.org) format: H1, summary quote, sections of Markdown links (`- [Name](url): note`). |
| `webmcp.js` | [WebMCP](https://developer.chrome.com/docs/ai/webmcp) tools for AI agents in the browser (see *For agents and tools*). |
| `.well-known/ai-catalog.json` | [Agent Resource Discovery](https://agenticresourcediscovery.org/spec/) catalog; also served as `/.well-known/ard.json`. |
| `skills/get-flipper-the-ripper/SKILL.md` | Agent skill (listed in the catalog): download, verify and install the newest APK. |
| `robots.txt`, `sitemap.xml` | Allow everything; comments point at `llms.txt`, `index.md`, `latest.json` and the catalog; the sitemap lists the page, `llms.txt`, `index.md` and the skill. |
| `assets/` | Hero art (`hero-1400.webp`, `hero-2400.webp`, `hero.jpg` fallback), `og.jpg` (1200 × 630), `screens.webp/.jpg`, `mark.svg` icon, `apple-touch-icon.png`. |
| `server/ftr-latest.py` + `.service` / `.timer` | The timer (see below). Installed to `/usr/local/sbin/` and `/etc/systemd/system/`. |
| `server/nginx/flipper-the-ripper.celox.io` | Vendored vhost — the live file is `/etc/nginx/sites-available/flipper-the-ripper.celox.io`. |
| `deploy.sh` | Ships the page; `deploy.sh server` also installs timer and vhost. |

Written **on the server** by the timer, never in this folder (and excluded from `deploy.sh`'s
`--delete`): `latest.json`, `changelog.md`, `ssi/`, `/etc/nginx/ftr-download.conf`.

## How "always the newest APK" works

`ftr-latest.timer` runs `ftr-latest.py` every 15 minutes (and 2 minutes after boot). It asks the
GitHub API for the latest release and requires **exactly one** asset matching
`flipper-the-ripper-v*.apk` — the name the release workflow gives it. Then it writes, each file only
when its content changed:

| Output | Used by |
|--------|---------|
| `latest.json` — `version`, `name`, `url`, `size` (bytes), `sha256` (from the API's `digest`), `published`, `notes` | The page's JavaScript (button link, localised meta line, SHA-256 in *Verify*) and agents |
| `ssi/version.txt`, `size.txt`, `date.txt`, `sha.txt`, `meta.html` | nginx SSI in `index.html` (meta line, SHA-256, JSON-LD `softwareVersion`/`fileSize`/`dateModified`) and `index.md` |
| `changelog.md` — `CHANGELOG.md` from `main` via `raw.githubusercontent.com` | The changelog dialog and agents |
| `/etc/nginx/ftr-download.conf` — `location = /download { return 302 <asset URL>; }` | The download button in the HTML and every link that should survive releases |

A failed GitHub call, a release without exactly one APK or a changelog that does not start with
`# Changelog` changes **nothing** — the last good state stays online. nginx is reloaded only when the
redirect changed, only after `nginx -t` passed (a failing test restores the previous include), and
**never while certbot is running**.

So after a release nothing needs doing here. To make the site show it at once instead of within
15 minutes:

```bash
ssh root@69.62.121.168 'systemctl start ftr-latest.service; journalctl -u ftr-latest -n 3 -o cat'
# ftr-latest: v1.11.0 json=new ssi=new changelog=new nginx=reloaded
```

Give nginx a few seconds after the reload before checking `/download` — right after it, one request
can still see the old target.

## For agents and tools

Everything a person sees is available without running JavaScript:

| What | Where | Notes |
|------|-------|-------|
| Newest APK | `https://flipper-the-ripper.celox.io/download` | 302 to the current GitHub release asset. The download button's `href` in the HTML is this URL; JavaScript only swaps in the direct asset URL. |
| Release facts | HTML (meta line, *Verify*), JSON-LD, `index.md` | Filled in by SSI at serve time — version, size, date, SHA-256. |
| Release as JSON | `/latest.json` | Fields as above. `Cache-Control: no-cache`. |
| Page as Markdown | `/index.md`, or `/` with `Accept: text/markdown` | Content negotiation; responses carry `Vary: Accept`. |
| Summary | `/llms.txt` | The [llms.txt](https://llmstxt.org) convention: purpose, links, install, verify, features, limits. |
| Changelog | `/changelog.md` | `text/markdown; charset=utf-8`, mirrored from GitHub. |
| Discovery | `<link rel="alternate">` / `<link rel="ai-catalog">` in `<head>` and an HTTP `Link` header on `/` | Point at `llms.txt`, `index.md`, `latest.json` and the ARD catalog. |
| Resource catalog | `/.well-known/ai-catalog.json` (= `/.well-known/ard.json`) | ARD 1.0: one entry, the agent skill below, with representative queries. CORS open. |
| Agent skill | `/skills/get-flipper-the-ripper/SKILL.md` | YAML front matter (`name`, `description`) + steps: latest release, download, verify (checksum and certificate), install, limits. |
| WebMCP tools | `webmcp.js`, registered via `navigator.modelContext` / `document.modelContext` where the browser offers it | `get_app_facts`, `get_latest_release`, `get_download_url`, `get_checksums`, `get_changelog` (read-only, same data as the page) and `set_page_language`. |
| Structured data | JSON-LD in `<head>` | `MobileApplication` (features, languages, licence, price 0, download URL, version, size), `HowTo` (install steps), `FAQPage` (the six FAQ answers). |

Try it:

```bash
curl -sI https://flipper-the-ripper.celox.io/ | grep -i -E '^link|^vary'
curl -s  -H 'Accept: text/markdown' https://flipper-the-ripper.celox.io/ | head -20
curl -s  https://flipper-the-ripper.celox.io/latest.json
```

**Lighthouse (13.5, 2026-09-25):** 100 in Performance, Accessibility, Best Practices and SEO, and
**Agentic Browsing 4/4**, mobile and desktop. The three WebMCP audits only apply when the browser has
WebMCP; with it enabled (`--chrome-flags="--headless=new --enable-experimental-web-platform-features --enable-features=WebMCP,WebMCPTesting"`)
all six tools are listed and their schemas pass. Re-run after changes:

```bash
npx -y lighthouse@latest https://flipper-the-ripper.celox.io/ --output=json --output-path=lh.json --chrome-flags="--headless=new"
```

Rules that keep it that way: every fact on the page must also be in `index.md` and, briefly, in
`llms.txt`; the FAQ text exists three times (HTML, JSON-LD `FAQPage`, `index.md`) and changes in all
three; controls are real `<button>`/`<a>` elements with readable names; one `<h1>`.

## Languages

- **English** is in `index.html`. `app.js` captures it on the first switch, so it is written once.
- `i18n.js` holds **de, es, it, fr** as `key → HTML`. Every element with `data-i18n="key"` has its
  `innerHTML` replaced; `data-i18n-alt` and `data-i18n-aria` do the same for `alt` and `aria-label`.
- The language comes from `navigator.languages` (first of en/de/es/it/fr), is remembered in
  `localStorage` (`ftr-lang`) and switched with the flag menu (keyboard: arrows, Home/End, Enter,
  Escape). Flags are CSS gradients (the Union Jack a data-URI SVG) — no image requests.
- Size and date in the meta line use `toLocaleString` / `toLocaleDateString` of the chosen language.
- French uses a narrow no-break space (U+202F) before `: ? ! ;` and inside `« »`.
- **Adding a text:** add `data-i18n="new.key"` in the HTML with the English text, then the key to all
  four objects in `i18n.js`. Check with
  `node -e "global.window={};require('./i18n.js');for(const k in window.FTR_I18N)console.log(k,Object.keys(window.FTR_I18N[k]).length)"` —
  all four counts must match.
- **Adding a language:** a new object in `i18n.js`, an entry in `LANGS`/`LANG_NAMES` in `app.js`, a
  menu item and a `.flag-xx` rule, an `og:locale:alternate`, and the JSON-LD `inLanguage`.

## Design notes

- Hero: the banner fills the section as a background (`object-fit: cover`), the phone and dolphin
  on the left stay bright, a gradient sinks the banner's own wordmark under the text on the right.
  Served as WebP in 1400 and 2400 px through `srcset` — never scaled up, which is what made the first
  version look soft. On phones the whole image sits above the text.
- Material 3 Expressive in the app's colours: pill buttons, 28 px radii, spring easings. Everything
  that moves is off under `prefers-reduced-motion`.
- System fonts only (`system-ui`, monospace `ui-monospace, "SF Mono", …`).
- Dialogs are `<dialog>` + `showModal()`; the licence text is re-flowed from `LICENSE` (the file's
  hard 78-column breaks look broken in a narrower box); focus starts on the content, not the close button.
- Asset URLs carry `?v=N` and are served `immutable` for a year — **a changed file needs a new
  number**, or returning visitors keep the old one. All references share one number; bump it
  everywhere (`sed -i '' 's/?v=14"/?v=15"/g; s/?v=14 /?v=15 /g' index.html`).

## Privacy and security

- **No request to a third party** from the visitor's browser: no CDN, no web fonts, no analytics, no
  GitHub API call (the timer does that on the server). The only outside traffic is the APK download
  a visitor starts.
- CSP `default-src 'self'` with `img-src 'self' data:` — so no inline scripts and no `style="…"`
  attributes (JSON-LD is fine; it is not executed).
- HSTS, `nosniff`, `Referrer-Policy`, `Permissions-Policy`. nginx drops inherited `add_header`s in any
  `location` that sets its own — every such block repeats the headers it needs.
- External links open in a new tab with `rel="noopener noreferrer"`; the download does not.
- `/ssi/` is `internal` — only reachable as an SSI subrequest (a direct request is 404).

## Deploying

```bash
./website/deploy.sh          # page and assets (rsync --delete, keeps the timer's files)
./website/deploy.sh server   # also: ftr-latest.py, its units, the vhost, nginx reload
```

`deploy.sh server` refuses to run while certbot is active. Both end with a run of the timer.

## Setting it up from scratch

Only needed on a new server or after losing the VPS.

1. **DNS:** A records `flipper-the-ripper`, `flippertheripper`, `ftr` → `69.62.121.168` in the
   `celox.io` zone, via the Hostinger API from raspi5 (token in `/root/.acme.sh/account.conf`, quoted —
   strip with `tr -d "\047\042"`). Check with `dig @1.1.1.1`, not the local resolver (Pi-hole and macOS
   cache a negative answer).
2. **Certificate:** put an HTTP-only vhost in place that serves `/.well-known/acme-challenge/` from
   `/var/www/html`, reload, then
   `certbot certonly --webroot -w /var/www/html -d flipper-the-ripper.celox.io -d flippertheripper.celox.io -d ftr.celox.io --deploy-hook "systemctl reload nginx"`.
3. `mkdir -p /var/www/flipper-the-ripper.celox.io`, then `./website/deploy.sh server`.

The vhost defines `map $http_accept $ftr_wants_markdown` at `http` level (files in `sites-enabled/`
are included inside `http {}`) and needs nginx ≥ 1.24 syntax `listen 443 ssl http2;`.

## Checking it

```bash
B=https://flipper-the-ripper.celox.io
curl -s  -o /dev/null -w '%{http_code}\n' $B/                          # 200
curl -sI $B/download | grep -i ^location                                # current APK
curl -s  $B/ | grep -c '<!--#'                                          # 0 — no unprocessed SSI
curl -s  $B/ | python3 -c "import sys,re,json;json.loads(re.search(r'ld\+json\">(.*?)</script>',sys.stdin.read(),re.S).group(1));print('JSON-LD ok')"
curl -s  -H 'Accept: text/markdown' $B/ | grep 'Current version'       # real version, not a directive
curl -sI $B/changelog.md | grep -i content-type                          # text/markdown; charset=utf-8
curl -sI https://ftr.celox.io/x?y=1 | grep -i ^location                   # 301 to the main name, path kept
echo | openssl s_client -connect flipper-the-ripper.celox.io:443 2>/dev/null | openssl x509 -noout -ext subjectAltName -enddate
ssh root@69.62.121.168 'systemctl list-timers ftr-latest.timer --no-pager'
```

In a browser (Playwright): 1440 × 900 and 390 × 844, every language once, the language menu by
keyboard, both dialogs, zero console messages, zero horizontal overflow.

## Traps we have already stepped in

- **`llms.txt` needs Markdown links.** Lighthouse's audit requires an H1 and at least one
  `[text](url)`; bare URLs fail it ("File does not appear to contain any links").
- **Every link needs a name at every width.** The GitHub link in the bar hides its text on phones;
  without `aria-label` it failed both Accessibility and the agent accessibility-tree audit (Lighthouse
  tests as a phone).
- **No `Agentmap:` in `robots.txt`.** ARD suggests it, but Lighthouse's SEO audit rejects it as an
  unknown directive (SEO dropped to 92). The catalog is found via the well-known path, `<link rel="ai-catalog">`
  and the `Link` header instead.
- **ARD validation:** Lighthouse ships the validator — `node --input-type=module -e "import {ConformanceTester}
  from '<lighthouse>/third-party/ard/ard.js'; …"` — use it before deploying a catalog change.

- **SSI blocks must be defined before their first use.** The fallbacks (`<!--# block … -->`) sit at
  the top of `<head>`; when they were in `<body>`, the JSON-LD in `<head>` rendered
  `[an error occurred while processing the directive]`.
- **SSI attributes inside JSON use single quotes** (`include virtual='/ssi/version.txt'`). Double
  quotes get escaped to `\"` in JSON, which nginx does not parse.
- **`ssi_types` compares the bare MIME type.** `default_type "text/markdown; charset=utf-8"` made SSI
  skip `index.md`; the charset now comes from `charset_types`.
- **Test without JavaScript.** All three faults above were invisible in a browser — the JavaScript
  paints over them. `curl` showed them.
- **Negative DNS cache:** looking a new name up before its record exists makes Pi-hole and macOS
  remember "does not exist" for minutes.
- **`deploy.sh` uses `rsync --delete`** — anything the timer writes into the webroot must be listed
  in its excludes, or a deploy deletes it until the next timer run.
- **Hero images:** a 1024 px banner stretched over a 1440 px screen looks soft. Deliver at least the
  display width, in two sizes.
