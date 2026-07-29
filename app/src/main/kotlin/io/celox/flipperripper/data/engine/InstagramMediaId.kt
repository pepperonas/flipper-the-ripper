package io.celox.flipperripper.data.engine

import java.math.BigInteger

/**
 * Converts an Instagram shortcode — the `DbDBPYJnUMW` in `instagram.com/reel/DbDBPYJnUMW/` — into the
 * numeric media id its API is keyed by.
 *
 * The shortcode is just a number written in base 64 over Instagram's own alphabet; the media id is that
 * same number in base 10. It is needed to call `/api/v1/media/<id>/info/`, the authoritative source for a
 * signed-in reel's video URL (the embed page only exposes a URL that is not authorised for gated content).
 */
object InstagramMediaId {
    private const val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val BASE = BigInteger.valueOf(ALPHABET.length.toLong())

    /** The base-10 media id, or null if [shortcode] is empty or holds a character outside the alphabet. */
    fun fromShortcode(shortcode: String): String? {
        if (shortcode.isEmpty()) return null
        var acc = BigInteger.ZERO
        for (c in shortcode) {
            val digit = ALPHABET.indexOf(c)
            if (digit < 0) return null
            acc = acc.multiply(BASE).add(BigInteger.valueOf(digit.toLong()))
        }
        return acc.toString()
    }
}
