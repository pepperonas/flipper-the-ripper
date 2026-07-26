package io.celox.flipperripper.domain.util

import io.celox.flipperripper.domain.model.Platform

/** A URL extracted from free text together with the platform it belongs to. */
data class ParsedUrl(val url: String, val platform: Platform)

/**
 * Pure URL extraction/normalisation, shared by share-intent handling and clipboard detection.
 *
 * Share sheets frequently deliver a blob like "Check this out! https://youtu.be/x?si=y", so we
 * scan the text for the first http(s) URL that resolves to a supported platform. Mirrors the
 * intent of inspector-rust's `detect_platform` (host-substring match), but hardened for the messy
 * multi-token strings Android's ACTION_SEND produces.
 */
object UrlParser {
    private val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** Trailing punctuation that commonly clings to a URL in shared text. */
    private const val TRAILING_JUNK = ").,!?;:”’'\""

    /**
     * Return the first supported URL found in [text], or null. The URL is trimmed of trailing
     * punctuation but query parameters are preserved (yt-dlp needs them).
     */
    fun extractSupported(text: String?): ParsedUrl? {
        if (text.isNullOrBlank()) return null
        return URL_REGEX.findAll(text)
            .map { it.value.trimEnd { ch -> ch in TRAILING_JUNK } }
            .firstNotNullOfOrNull { cleaned ->
                val platform = hostOf(cleaned)?.let(Platform::matchHost)
                platform?.let { ParsedUrl(cleaned, it) }
            }
    }

    /** Detect the platform for a single, already-isolated http(s) URL string. */
    fun detectPlatform(url: String?): Platform? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        val host = hostOf(trimmed) ?: return null
        return Platform.matchHost(host)
    }

    /** Extract the host from an http(s) URL without pulling in java.net (keeps domain pure). */
    private fun hostOf(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return null
        val afterScheme = url.substring(schemeEnd + 3)
        if (afterScheme.isEmpty()) return null
        val end = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val authority = if (end < 0) afterScheme else afterScheme.substring(0, end)
        // Strip optional userinfo@ and :port.
        val hostPart = authority.substringAfterLast('@').substringBefore(':')
        return hostPart.ifBlank { null }
    }
}
