package io.celox.flipperripper.domain.model

/**
 * Tells downloading apart from post-processing, from the engine's own status line.
 *
 * yt-dlp reports 100 % when the last byte of the *stream* has arrived — and then starts the ffmpeg
 * merge, which for a long video takes as long again. Without this the app showed a full progress bar
 * and "Downloading…" for the whole merge, which reads as "finished, but stuck".
 *
 * The markers are yt-dlp's post-processor prefixes. Matching the bracketed tag rather than prose
 * keeps this stable across yt-dlp's wording changes; anything unrecognised counts as downloading,
 * because claiming post-processing that is not happening would be the worse error.
 */
object DownloadPhases {
    private val POST_PROCESSING_TAGS =
        listOf(
            "[merger]",
            "[extractaudio]",
            "[ffmpeg]",
            "[videoconvertor]",
            "[videoremuxer]",
            "[fixup",
            "[metadata]",
            "[embedsubtitle]",
            "[thumbnailsconvertor]",
        )

    fun isPostProcessing(line: String?): Boolean {
        val l = line?.lowercase() ?: return false
        return POST_PROCESSING_TAGS.any { it in l }
    }
}
