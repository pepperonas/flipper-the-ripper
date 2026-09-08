package io.celox.flipperripper.data.engine

/**
 * Cleans up a media URL scraped out of a page's embedded JSON.
 *
 * Those URLs arrive JSON-escaped inside HTML — `https://v16-webapp-prime.tiktok.com/…`
 * — and the scraper used to undo three escapes it had happened to meet: `&`, `%` and `\/`.
 * `/` was not among them, so a TikTok URL reached the downloader with its slashes still escaped
 * and the host parsed as the literal string "u002f": *Unable to resolve host "u002f"*.
 *
 * Hand-picking escapes is the bug, not the missing one. This decodes any `\uXXXX` and the JSON string
 * escapes, so the next platform to encode a different character needs no fix here.
 */
object MediaUrl {
    private val UNICODE_ESCAPE = Regex("""\\u([0-9a-fA-F]{4})""")

    /** Decode JSON-style escapes in [raw]. Returns it unchanged when there is nothing to decode. */
    fun decodeEscapes(raw: String): String =
        UNICODE_ESCAPE.replace(raw) { m -> m.groupValues[1].toInt(16).toChar().toString() }
            .replace("\\/", "/")
            .replace("\\\"", "\"")
}
