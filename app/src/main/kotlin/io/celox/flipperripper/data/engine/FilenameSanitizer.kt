package io.celox.flipperripper.data.engine

/**
 * Produces safe, human-readable filenames from a video title.
 *
 * Rules (aligned with inspector-rust's `%(title).100B [%(id)s]` template and the requirement that
 * "the filename equals the video title, invalid filesystem characters replaced"):
 *  - characters illegal on Android/FAT/exFAT (`/ \ : * ? " < > |`) and control chars → a space,
 *  - reserved trailing dots/spaces stripped,
 *  - runs of whitespace collapsed,
 *  - truncated to a byte budget (UTF-8, never splitting a code point) so long titles stay valid,
 *  - empty/blank results fall back to a stable default.
 */
object FilenameSanitizer {
    private const val DEFAULT_MAX_BYTES = 120
    private const val FALLBACK = "video"
    private val ILLEGAL = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    /**
     * @param title raw video title.
     * @param extension file extension without dot, e.g. "mp4". Blank → no extension appended.
     * @param maxBytes byte budget for the base name (before extension).
     */
    fun sanitize(title: String, extension: String = "mp4", maxBytes: Int = DEFAULT_MAX_BYTES): String {
        val base = sanitizeBase(title, maxBytes)
        val ext = extension.trim().trimStart('.').lowercase()
        return if (ext.isEmpty()) base else "$base.$ext"
    }

    /** Sanitize just the base name (no extension). Exposed for reuse/testing. */
    fun sanitizeBase(title: String, maxBytes: Int = DEFAULT_MAX_BYTES): String {
        val replaced =
            buildString(title.length) {
                for (ch in title) {
                    when {
                        ch in ILLEGAL -> append(' ')
                        ch.isISOControl() -> append(' ')
                        else -> append(ch)
                    }
                }
            }
        val collapsed = replaced.replace(WHITESPACE, " ").trim()
        val trimmed = collapsed.trimEnd('.', ' ')
        val truncated = truncateToBytes(trimmed, maxBytes).trimEnd('.', ' ')
        return truncated.ifBlank { FALLBACK }
    }

    private val WHITESPACE = Regex("""\s+""")

    /** Truncate to at most [maxBytes] UTF-8 bytes without splitting a surrogate pair or code point. */
    private fun truncateToBytes(input: String, maxBytes: Int): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return input
        // Walk code points, accumulating byte length until the budget is reached.
        val sb = StringBuilder()
        var used = 0
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            val cpChars = Character.charCount(cp)
            val cpLen = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (used + cpLen > maxBytes) break
            sb.appendCodePoint(cp)
            used += cpLen
            i += cpChars
        }
        return sb.toString()
    }
}
