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

    private val FILE_EXTENSION = Regex("""\.[A-Za-z0-9]{1,5}$""")

    /**
     * The label shown in the history list.
     *
     * Two things are cleaned up here. The stored file name carries its extension, which does not belong
     * in a title (`Video by kvashenaya.mp4`). And an entry whose metadata never resolved — a failed
     * download, typically — still holds the raw source URL, which filled the card with
     * `https://www.instagram.com/reel/DbDBPYJnUMW/?igsh=…`; that is shortened to the part which
     * identifies the video.
     */
    fun displayTitle(fileName: String?, fallbackTitle: String): String =
        fileName?.takeIf { it.isNotBlank() }?.let { it.replace(FILE_EXTENSION, "") }
            ?: shortenUrl(fallbackTitle)

    /** Reduce a bare URL to its identifying tail; anything that is not a URL is returned unchanged. */
    private fun shortenUrl(value: String): String {
        val trimmed = value.trim()
        if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) return trimmed
        val afterScheme = trimmed.substringAfter("://")
        val host = afterScheme.substringBefore('/').substringBefore('?')
        val path = afterScheme.substringAfter('/', "").substringBefore('?').substringBefore('#')
        val segments = path.split('/').filter { it.isNotBlank() }
        // A single generic segment says nothing: `youtube.com/watch?v=YE7VzlLtp-4` rendered as
        // "watch", which is what every YouTube link looks like. The identity is in the query there.
        // Only for a one-segment path — a Reel or a Short carries its id in the path itself, and a
        // tracking parameter must not displace it.
        if (segments.size == 1) {
            longestQueryValue(afterScheme)?.let { return it }
        }
        return if (segments.isEmpty()) host else segments.takeLast(2).joinToString("/")
    }

    /** The longest `key=value` value in the query, which is as close to "the id" as guessing gets. */
    private fun longestQueryValue(afterScheme: String): String? =
        afterScheme.substringAfter('?', "")
            .substringBefore('#')
            .split('&')
            .mapNotNull { it.substringAfter('=', "").takeIf { v -> v.isNotBlank() } }
            .maxByOrNull { it.length }
}
