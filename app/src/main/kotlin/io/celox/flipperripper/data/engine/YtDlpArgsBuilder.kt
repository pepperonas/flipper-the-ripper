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
    ): List<String> = buildOptions(platform, mode, outputTemplate) + url

    /**
     * The option tokens only, ending with the `--` flag-injection guard. The engine passes the URL
     * separately (via the request constructor), which appends it after these options — reproducing
     * `… -- <url>`.
     */
    fun buildOptions(
        platform: Platform,
        mode: DownloadMode,
        outputTemplate: String,
    ): List<String> {
        val args = mutableListOf<String>()

        when (mode) {
            DownloadMode.VIDEO -> {
                // Prefer H.264 + m4a so the muxed mp4 plays everywhere; avoids the "audio-only/broken"
                // VP9-in-mp4 problem described in social_dl.rs.
                args += listOf("-S", "vcodec:h264,res,acodec:m4a")
                args += listOf("--merge-output-format", "mp4")
            }
            DownloadMode.AUDIO -> {
                args += "-x"
                args += listOf("--audio-format", "m4a")
                args += listOf("--audio-quality", "0")
            }
        }

        // YouTube SABR workaround: the default web client returns formats without URLs.
        if (platform == Platform.YOUTUBE) {
            args += listOf("--extractor-args", "youtube:player_client=default,ios,web_safari")
        }

        args += "--no-playlist"
        args += "--no-mtime"

        args += listOf("-o", outputTemplate)

        // Flag-injection guard: everything after `--` is treated as a positional arg, so a URL that
        // starts with `-` cannot smuggle flags. The URL itself is appended by the caller.
        args += "--"

        return args
    }

    /** The output template used inside a per-download working directory. */
    const val OUTPUT_TEMPLATE = "%(title).100B [%(id)s].%(ext)s"
}
