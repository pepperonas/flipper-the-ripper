package io.celox.flipperripper.data.engine

/**
 * Decides the title a finished download is saved under.
 *
 * A download started straight from a shared link begins life with the raw URL as its title, because the
 * metadata lookup happens later (and may fail). Taking that URL as the name produced files like
 * `https www.youtube.com watch v=jNQXAC9IVRw.mp4` instead of the title-based name the app promises.
 *
 * yt-dlp has already written the real title into the downloaded file via the `%(title).100B [%(id)s]`
 * output template, so the produced file name is a much better fallback than the URL.
 */
object DownloadNaming {
    private val ID_SUFFIX = Regex("""\s*\[[^\]]+\]\s*$""")

    /** Recover the plain title from yt-dlp's `<title> [<id>]` file base name. */
    fun titleFromProducedFile(fileBaseName: String): String = fileBaseName.replace(ID_SUFFIX, "").trim()

    /**
     * The title to save under, best source first: resolved metadata, then the title yt-dlp itself put
     * on the file, then whatever the record already carried (typically the source URL).
     */
    fun preferredTitle(
        resolvedTitle: String?,
        producedFileBaseName: String,
        fallbackTitle: String,
    ): String =
        resolvedTitle?.takeIf { it.isNotBlank() }
            ?: titleFromProducedFile(producedFileBaseName).takeIf { it.isNotBlank() }
            ?: fallbackTitle
}
