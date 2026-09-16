# Download queue, quality picker, subtitles, playlists — design

**Status:** planned, not started. Work begins the weekend of **2026-09-19/20**.
**Ships as:** **one release, 1.10.0** (owner's decision — the four features were proposed as four
releases; they are built and verified in the order below but tagged once).
**Decisions taken (2026-09-16):** reorder via the `reorderable` library · subtitles embedded in the
MP4 (a spike decides feasibility first) · a shared playlist opens the playlist sheet instead of
auto-downloading · one release.

---

## 1. Principles — what "maximally intuitive" means here

1. **The one-tap path stays untouched.** Share → download runs → History shows it. Every new
   capability is an *offer beside* the default path, never a question *before* it. The single
   exception is a shared playlist (§5): two hundred videos must not start silently.
2. **One vocabulary for choosing.** One options sheet (`ModalBottomSheet`), options as connected
   `ToggleButton` groups, and the Download button becomes an M3 Expressive **SplitButton** — the
   leading half downloads with the current defaults, the trailing chevron opens the options. All
   components are present in the pinned material3 1.5.0-alpha18 (verified in the artifact: 
   `SplitButtonDefaults`, `ButtonGroupKt`, `ToggleButtonKt`, `ModalBottomSheetKt`,
   `LoadingIndicatorKt`, `SwipeToDismissBoxKt`). No new Compose dependency.
3. **The last choice is the new default.** No "always ask" switch, no dialog on share. A summary
   line under the button says what will happen: `1080p · MP4 · DE subtitles`.
4. **What a platform cannot do is not offered.** TikTok has no format choice and no subtitle
   tracks; the sheet shows "Original, as published" and one sentence why — never an empty group.
5. **Motion from one source.** Sheets, reordering and section changes take their springs from the
   existing `MotionScheme` / `ui/motion/ScreenTransitions.kt`. No bespoke curves.

## 2. Order of work inside the release

| Step | Feature | Why in this order |
|---|---|---|
| A | Queue: pause / resume / reorder | foundation — playlists need an order that actually *runs*; pause needs the runner |
| B | Quality / format picker | brings the options sheet and the formats data model |
| C | Subtitles | a section in the same sheet, from the same JSON |
| D | Playlists | uses the queue and the remembered defaults; the release ends with new mockups |

Each step is verified on the emulator with real downloads before the next begins, and each step's
pins are mutation-probed as it lands. The version bump, CHANGELOG section and tag happen once, after D.

## 3. A · Queue

**User-facing.** History gains two sections: **Active** — the running download on top, then the
queued ones in order, then paused — and **Done** (saved / failed / cancelled, newest first). Running
card: *Pause · Cancel*. Paused card: *Resume · Cancel*. Queued cards carry a **drag handle** (2×3
dots, 44 dp hit area) at the trailing edge; dragging reorders, cards glide with `animateItem()` on
the spatial spring. Finished cards: **swipe to delete** (`SwipeToDismissBox`) — cleanup without a
menu. One foreground notification instead of one per download: "Downloading 2 of 5 · title · 43 %".

**Technical.**
- Today every download is its own WorkManager work and they run **in parallel** — there is no
  order to manage. Replacement: **one** `DownloadQueueWorker` (unique work, foreground/dataSync)
  that drains the table in `queueOrder`, one at a time. Order becomes true instead of claimed.
- DB v2: `queueOrder INTEGER`, status **PAUSED**. Pause = `engine.cancel(processId)`, keep the
  working directory with its `.part`, status PAUSED. Resume = back to QUEUED at its old position;
  yt-dlp continues the `.part` (`--continue` is its default — it only must not be disabled). The
  WebView path fetches over HTTP itself and needs a `Range` start at the existing size (the header
  is already in use there). Cancel = as today, directory removed.
- Upgrade path: cancel the old per-item work by tag; rows stuck in RUNNING go back to QUEUED and
  the runner picks them up. A test covers exactly this transition.
- Concurrency stays **1**; a "2/3 at once" setting is speculation now and is listed under Later.
- Reorder UI: `sh.calvin.reorderable:reorderable` (MIT). Drag physics with edge auto-scroll is the
  class of bug paid for twice by hand elsewhere; the library is the cheaper correctness.

**Tests.** `QueueOrdering` as a pure rule (next candidate, reorder, resume keeps position, clamping
at the edges); repository against a fake WorkManager; ViewModel. Instrumented on the emulator:
drag the handle (`performTouchInput`), and **pause/resume of a real download, measured**: `.part`
bytes at pause ≤ the byte the resume starts from — never a restart from zero.

## 4. B · Quality and format

**User-facing.** Under the link field: `[Load info] [Download ▾]`. The chevron opens the **options
sheet**: section "Quality" as a connected ToggleButton group — **Best · 1080p · 720p · 480p · Audio
only**. After *Load info*, tiers this video does not have are disabled (with the actual maximum as
the hint); before that the group shows the remembered default. The current Video/Audio toggle
**merges into it** — "Audio only" is a quality tier, not a second control. The summary line under
the button is tappable and opens the same sheet. Settings mirrors it as "Default quality". Share
with auto-download uses the default and never asks.

**Technical.**
- `VideoInfo.formats` from the youtubedl-android mapper (`getFormats()` — present in 0.18.1).
  Pure rule `FormatSelection`: `QualityChoice → yt-dlp arguments`
  (`-S "vcodec:h264,res:720,acodec:m4a"` — yt-dlp sorts ≤ 720p first; H.264/m4a remains today's
  playability reason) and `formats → available tiers`. Unknown ⇒ Best. The progressive fallback stays.
- WebView platforms (Instagram/TikTok/Facebook): one file, no choice — the sheet says "Original".
  *Audio only* stays off there (no extraction path without an ffmpeg pass; named honestly rather
  than half built).
- `DownloadRecord.quality` (for *Retry* and the card: "720p"), DB v3.
- Server backend: `quality` is passed through (small change in `backend/`); otherwise the server
  would silently ignore the choice.

**Tests.** `FormatSelectionTest` (arguments per tier; availability from real format fixtures for
YouTube, X, Dailymotion; unknown ⇒ Best); ViewModel remembers the choice; share uses the default.
Instrumented: chevron opens the sheet, the choice shows in the summary line, the leading half
downloads without a sheet.

## 5. C · Subtitles

**User-facing.** In the same sheet, section "Subtitles": **Off · Deutsch · English · English
(auto)** — languages come from the video, automatic tracks are marked. Default Off; a chosen
language is kept as "German, when available". Summary line shows `· DE subtitles`; the History
card shows a small **CC**. Under *Audio only* the section is off.

**Technical.** yt-dlp `--write-subs` / `--write-auto-subs --sub-langs de --embed-subs`: the track
is **embedded into the MP4** as `mov_text` by the bundled ffmpeg — one file, plays in VLC, MX,
Google Photos. A sidecar `.srt` is deliberately second choice: under scoped storage a non-media
file may not go to *Movies*; it would land in *Downloads* and the user would have two places for
one thing. Available languages are in the `-J` JSON (`subtitles` / `automatic_captions`); the
mapper does not expose them, so `fetchInfo` moves to an own `InfoJson` parser
(kotlinx.serialization, already a dependency) — the same parser delivers playlist entries in D.

**Spike first** (half an hour, throwaway): whether youtubedl-android's ffmpeg build carries the
`mov_text` encoder. Yes ⇒ as above. No ⇒ `.srt` in *Downloads/FlipperTheRipper* with a note on
the card. Both outcomes are named here so neither is a surprise.

**Tests.** `SubtitleSelection` pure (language → arguments, auto vs manual, availability, audio ⇒
off); JSON parser against fixtures; ViewModel. Instrumented: CC badge; a real download on the
emulator with an embedded track, checked by counting tracks with `MediaExtractor`.

## 6. D · Playlists

**User-facing.** A playlist link (`list=`; on a video URL a chip "This video is in a playlist —
the whole playlist?", not a dialog) leads, after *Load info*, to the **playlist sheet** instead of
the single preview: title, count, the entries (thumbnail, title, duration) with checkboxes,
"Select all", and at the bottom **"Download 12 videos"** — with the quality and subtitles from the
summary line. Entries enter the queue in playlist order; each card shows "Playlist name · 3/12".
A shared playlist with auto-download **opens this sheet instead of starting** — the one
deliberate exception to principle 1. Cap: 100 entries per selection, with a hint.

**Technical.** `-J --flat-playlist` (fast, no video page fetched) → `PlaylistInfo(entries)` from
the parser of C. `UrlParser` learns playlist detection (YouTube; Dailymotion `/playlist/`). Each
entry becomes a **normal** single download with `--no-playlist` and the entry URL — no yt-dlp
playlist mode. That is why pause, reorder and retry work per video **with no extra code**, and why
the queue comes first. DB v4: `playlistId`, `playlistTitle`, `playlistIndex`. X, Instagram,
TikTok, Facebook: no playlists — scraping profiles is explicitly out of scope.

**Tests.** `UrlParser` playlist cases; `PlaylistInfo` from fixture JSON; selection model (all /
none / partial, cap); enqueue order; the share behaviour. Instrumented: sheet + "Download N".

## 7. Cross-cutting

- **Strings EN + DE** from the start (`TranslationTest` holds it); settings for default quality
  and subtitle language.
- **Docs, once, at the end:** README features / FAQ / roadmap ticks, CHANGELOG 1.10.0 section
  (= the release notes), new mockups showing the sheet, the queue and the playlist.
- **As always:** every new pin mutated once; every feature measured with real downloads on the
  emulator; release through the workflow, certificate check included.
- **New dependency:** only `reorderable` (A). Nothing else.
- **Later, not now:** parallel downloads (2/3), multi-language subtitles, playlist grouping in
  History, cookie file for other login-gated platforms.

## 8. TODO — the checklist for the weekend

Tick in order. Nothing below is started.

**A · Queue**
- [ ] Add `reorderable` to `libs.versions.toml`; pin the version
- [ ] DB v2 migration: `queueOrder`, `PAUSED`; migration test (v1 fixture → v2)
- [ ] `QueueOrdering` pure rule + tests (next / reorder / resume position / clamping)
- [ ] `DownloadQueueWorker` (single unique work, sequential drain, one notification)
- [ ] Repository: `pause`, `resume`, `reorder(ids)`; upgrade path (cancel old per-item work, RUNNING → QUEUED) + test
- [ ] WebView fetch: `Range` resume from existing bytes
- [ ] History: two sections, drag handle, `animateItem()`, swipe-to-delete on finished
- [ ] Instrumented: drag reorder; pause/resume of a real download with `.part` bytes measured
- [ ] Mutation probe of every new pin

**B · Quality**
- [ ] `FormatSelection` pure rule + tests with YouTube/X/Dailymotion format fixtures
- [ ] `VideoInfo.formats`; DB v3 `quality` on the record; settings "Default quality"
- [ ] Home: SplitButton, options sheet with the Quality group, summary line; Video/Audio toggle merged
- [ ] Backend: pass `quality` through
- [ ] Instrumented: chevron → sheet → summary; leading half downloads directly
- [ ] Mutation probe

**C · Subtitles**
- [ ] **Spike:** `--embed-subs` with the bundled ffmpeg on the emulator — record the outcome here
- [ ] `InfoJson` parser (formats, subtitles, automatic captions, entries) + fixture tests
- [ ] `SubtitleSelection` pure rule + tests; settings "Subtitle language"
- [ ] Sheet section "Subtitles"; CC badge on the card
- [ ] Instrumented: embedded track counted via `MediaExtractor`
- [ ] Mutation probe

**D · Playlists**
- [ ] `UrlParser` playlist detection + tests (YouTube `list=`, video-in-playlist, Dailymotion)
- [ ] `PlaylistInfo` via `-J --flat-playlist`; DB v4 playlist columns
- [ ] Playlist sheet (checklist, select all, cap 100, "Download N"); playlist chip on cards
- [ ] Share of a playlist with auto-download → sheet, not download
- [ ] Instrumented: sheet + "Download N" enqueues in order
- [ ] Mutation probe

**Release 1.10.0**
- [ ] Strings EN + DE complete; `TranslationTest` green
- [ ] README (features, FAQ, roadmap ticks), CHANGELOG 1.10.0, new mockups
- [ ] Badges (tests, LoC, test code, APK size)
- [ ] Full gate, tag `v1.10.0`, verify the release page and the published file, install on the S24
