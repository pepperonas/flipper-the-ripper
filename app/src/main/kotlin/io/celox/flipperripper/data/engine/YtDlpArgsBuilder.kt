package io.celox.flipperripper.data.engine

import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.Platform

/**
 * Builds the yt-dlp argument list.
 *
 * Faithful port of `build_dl_args()` from inspector-rust (`core/rust-lib/src/social_dl.rs`), with
 * two deliberate Android deviations, both documented here:
 *
 *  1. `--no-progress` is **omitted** — youtubedl-android parses progress from stdout to drive the
 *     UI/notification, which that flag would suppress. inspector-rust wants a quiet CLI; we want live
 *     progress.
 *  2. `--cookies-from-browser` (the desktop cookie-retry fallback) is **not applicable on Android** —
 *     there are no desktop browser profiles to read. A login wall is surfaced as
 *     [io.celox.flipperripper.domain.model.DownloadError.LoginRequired] instead. (User-supplied
 *     cookie files are a roadmap item.)
 *
 * Everything else — H.264/m4a format sorting for playability, mp4 muxing, audio extraction, the
 * YouTube SABR player-client workaround, `--no-playlist`, `--no-mtime`, the `--` flag-injection
 * guard, and the `%(title).100B [%(id)s]` output template — is preserved verbatim.
 */
object YtDlpArgsBuilder {
    /**
     * @param outputTemplate absolute output template path passed to `-o`. Callers place this inside a
     *   per-download working directory so the produced file can be located and moved to MediaStore.
     */
    fun build(
        url: String,
        platform: Platform,
        mode: DownloadMode,
        outputTemplate: String,
    ): List<String> = buildOptions(platform, mode, outputTemplate) + "--" + url

    /**
     * The option tokens only — deliberately **without** a trailing `--`.
     *
     * The `--` end-of-options guard belongs immediately before the URL, and on Android we do not own
     * that position: youtubedl-android appends its own flags (notably `--ffmpeg-location <path>`)
     * *after* the options we supply. A trailing `--` therefore demoted the library's own flags to
     * positional arguments, so yt-dlp treated the ffmpeg binary path as another thing to download and
     * ended every run with `ERROR: [generic] '…/libffmpeg.so' is not a valid URL`. Downloads visibly
     * progressed (the real URL was fetched first) and then failed, so nothing ever reached the gallery.
     *
     * [build] — which does own the ordering — still emits `--` right before the URL. For the engine
     * path the guard's job is done by validating the URL scheme instead (see [YoutubeDlEngine]).
     */
    fun buildOptions(
        platform: Platform,
        mode: DownloadMode,
        outputTemplate: String,
        preferProgressive: Boolean = false,
    ): List<String> {
        val args = mutableListOf<String>()

        when (mode) {
            DownloadMode.VIDEO ->
                if (preferProgressive) {
                    // Progressive fallback: a single pre-muxed file (video+audio in one stream), so no
                    // ffmpeg merge is required. Used to retry when a merge/ffmpeg failure occurred.
                    args += listOf("-f", "best[ext=mp4]/best")
                } else {
                    // Prefer H.264 + m4a so the muxed mp4 plays everywhere; avoids the "audio-only/broken"
                    // VP9-in-mp4 problem described in social_dl.rs. (Best quality — may require ffmpeg merge.)
                    args += listOf("-S", "vcodec:h264,res,acodec:m4a")
                    args += listOf("--merge-output-format", "mp4")
                }
            DownloadMode.AUDIO ->
                if (preferProgressive) {
                    // Grab the best m4a audio stream directly, without an ffmpeg extraction/convert pass.
                    args += listOf("-f", "ba[ext=m4a]/ba")
                } else {
                    args += "-x"
                    args += listOf("--audio-format", "m4a")
                    args += listOf("--audio-quality", "0")
                }
        }

        if (platform == Platform.YOUTUBE) {
            args += listOf("--extractor-args", "youtube:player_client=$YOUTUBE_PLAYER_CLIENTS")
        }

        args += "--no-playlist"
        args += "--no-mtime"

        args += listOf("-o", outputTemplate)

        return args
    }

    /** The output template used inside a per-download working directory. */
    const val OUTPUT_TEMPLATE = "%(title).100B [%(id)s].%(ext)s"

    /**
     * The YouTube player client used **on device**. Exactly one clears both of YouTube's gates without
     * machinery a stock Android app cannot ship:
     *
     *  1. the `n`-signature **JavaScript challenge** — every `web*` client (`web`, `web_safari`,
     *     `web_embedded`, `mweb`) needs a JS runtime to solve it. There is none on Android, so those
     *     clients fail with "n challenge solving failed: Some formats may be missing".
     *  2. the **GVS PO Token**, demanded before googlevideo serves the media — `ios`, `android`, `mweb`
     *     and `tv_simply` all require one, so their formats are skipped with "…require a GVS PO Token
     *     which was not provided" and the download fails even though extraction looked fine.
     *
     * `android_vr` is an app client (no JS challenge) that needs no PO token — the only combination
     * that leaves a downloadable format. `tv` is token-free but serves DRM without account cookies, and
     * `default` resolves into the gated clients above, so both are excluded.
     *
     * The server backend deliberately uses a *wider* list (it runs deno, so it can solve the JS
     * challenge); see `backend/app.py`.
     *
     * Source: yt-dlp PO Token Guide (https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide).
     */
    const val YOUTUBE_PLAYER_CLIENTS = "android_vr"
}
