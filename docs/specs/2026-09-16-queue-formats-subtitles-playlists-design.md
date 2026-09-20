# Download queue, quality picker, subtitles, playlists — design

**Status:** planned, not started. Work begins the weekend of **2026-09-19/20**.
**Ships as:** **one release, 1.10.0** (owner's decision — the four features were proposed as four
releases; they are built and verified in the order below but tagged once).
**Decisions taken (2026-09-16):** reorder via the `reorderable` library · ~~subtitles embedded in
the MP4~~ · a shared playlist opens the playlist sheet instead of auto-downloading · one release.
**2026-09-21:** subtitles dropped on the owner's decision. The release is A + B + D.

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
| ~~C~~ | ~~Subtitles~~ | **dropped 2026-09-21 — not wanted** |
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

## 5. ~~C · Subtitles~~ — DROPPED 2026-09-21

> Not wanted; kept below only so the reasoning is not lost if it is ever revisited.

### (original design)

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

- **Strings EN + DE** from the start (`TranslationTest` holds it); a setting for default quality.
  (The subtitle-language setting is gone with C.)
- **Docs, once, at the end:** README features / FAQ / roadmap ticks, CHANGELOG 1.10.0 section
  (= the release notes), new mockups showing the sheet, the queue and the playlist.
- **As always:** every new pin mutated once; every feature measured with real downloads on the
  emulator; release through the workflow, certificate check included.
- **New dependency:** only `reorderable` (A). Nothing else.
- **Later, not now:** parallel downloads (2/3), subtitles at all (dropped, see §5), playlist
  grouping in History, cookie file for other login-gated platforms.

## 8. TODO — the checklist for the weekend

Tick in order. Nothing below is started.

**A · Queue — DONE 2026-09-20**
- [x] Add `reorderable` to `libs.versions.toml`; pin the version (3.1.0)
- [x] DB v2 migration: `queueOrder`, `PAUSED`; migration test (v1 fixture → v2)
- [x] `QueueOrdering` pure rule + tests (next / reorder / resume position / clamping)
- [x] `DownloadQueueWorker` (single unique work, sequential drain, one notification)
- [x] Repository: `pause`, `resume`, `reorder(ids)`; upgrade path + test
- [x] WebView fetch: `Range` resume from existing bytes
- [x] History: two sections, drag handle, `animateItem()`, swipe-to-delete on finished
- [x] Instrumented: drag reorder; pause/resume of a real download with `.part` bytes measured
- [x] Mutation probe of every new pin (14 caught; one blind pin sharpened, one invalid mutation
      corrected — `QueueOrdering.next` sorts for itself, so the SQL order was not the defect)

Found along the way, all measured on the device and fixed here:
- ⚠️ The database was built with `fallbackToDestructiveMigration()`. The first schema change would
  have deleted every user's history without a word.
- ⚠️ A pause could be erased by the phase write that followed it. The runner opens a download with
  an unconditional PREPARING write, and resolving metadata takes 3–11 s with the Pause button
  visible for all of it. The guard is now in the WHERE clause.
- ⚠️ Resume restarted from zero: every engine opens a run with `dir.deleteRecursively()`, which
  deletes the `.part` file the pause exists to keep. Measured: paused at 62,436,533 bytes, resumed
  at 0. Now `DownloadSpec.resume`, verified at 52,345,281 → 59,932,610 → 134,557,823.
- ⚠️ Waiting downloads were drawn with a spinner and a moving progress bar. Defensible before the
  queue (QUEUED was a blink), untrue after it.
- The card showed "watch" for every YouTube link until metadata resolved.

Measured after: never more than 1 download active across 80 samples over 160 s (it was 2 in
parallel before); queue order followed end to end, including after a drag.

**B · Quality — DONE 2026-09-21**
- [x] `FormatSelection` pure rule + tests with **real** captured format fixtures
      (`app/src/test/resources/formats/`: a 144/240p-only video and a full 27–2160p ladder).
      ⚠️ X and Dailymotion fixtures could not be captured — the sample URLs tried are gone. The two
      YouTube fixtures cover the shapes that matter (no ladder / full ladder); noted rather than faked.
- [x] `VideoInfo.formats`; DB v3 `quality` on the record; settings "Default quality"
- [x] Home: SplitButton, options sheet with the Quality group, summary line; Video/Audio toggle merged
- [x] Backend: `max_height` passed through and clamped server-side
- [x] Instrumented: 5 tests on the picker (disabled tiers, tapping, visibility); chevron → sheet →
      summary verified on the device
- [x] Mutation probe (6 caught)

Found along the way:
- ⚠️ The first `availableTiers` asked whether the **cap could be honoured** rather than whether the
  video **reaches** the tier, and offered 480p for a video that exists only in 144p and 240p. A real
  fixture caught it; an invented one would have agreed with the bug.
- ⚠️ `-S res:720` does not cap. It prefers the closest match and returns 1080p when that is closer.
  The rule uses `height<=720`, ending in `/b` so a video that exists only above the cap still
  downloads.
- ⚠️ Picking 720p and then resolving a 240p video left "720p" under the button while the tier itself
  was greyed out. Resolving now drops an unreachable choice back to Best.
- ⚠️ `uiautomator`'s `enabled` attribute does **not** reflect Compose's disabled state — it reported
  every tier as enabled while the screenshot showed three greyed out. Use the Compose test API.
- ⚠️ Blind coordinate taps uninstalled the app under test (they landed on the launcher and dragged
  the icon onto Uninstall). Every device tap now checks the foreground first.

Measured end to end, same video, same device: **480p → 38,776,395 bytes, 720p → 161,192,090** —
matching yt-dlp's own prediction for those filters (38,478,640 / 160,796,363).

**Known limitation:** when the ffmpeg merge fails, the existing self-heal retries with a single
pre-muxed stream, and YouTube's progressive formats top out well below 720p. The download then
succeeds at a lower resolution than the card says was asked for. Observed once. Showing the
*delivered* height (from MediaStore) rather than the requested one would fix it and is not built.

**C · Subtitles — DROPPED 2026-09-21 (owner's decision: "die untertitel brauchen wir nicht")**

Nothing was built. ⚠️ One thing moves rather than disappearing: the `InfoJson` parser was to be
introduced here and *reused* by D for playlist entries, so **D now introduces it itself**. The
mapper does not expose `entries`, so D still needs its own parse of `-J --flat-playlist`.

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
