package io.celox.flipperripper.data.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The shortcode → media-id conversion that lets the extractor call `/api/v1/media/<id>/info/`, the only
 * source of the authorized video URL for a signed-in/gated reel.
 *
 * The anchor value (`DbDBPYJnUMW` → `3946003153276453654`) was confirmed against Instagram's live API in
 * a real browser, so it pins the exact base-64 alphabet and digit order.
 */
class InstagramMediaIdTest {
    @Test
    fun `converts a real shortcode to the id Instagram's API expects`() {
        assertThat(InstagramMediaId.fromShortcode("DbDBPYJnUMW")).isEqualTo("3946003153276453654")
    }

    @Test
    fun `first alphabet character is zero`() {
        // 'A' is index 0 in Instagram's alphabet.
        assertThat(InstagramMediaId.fromShortcode("A")).isEqualTo("0")
    }

    @Test
    fun `single characters map to their alphabet index`() {
        assertThat(InstagramMediaId.fromShortcode("B")).isEqualTo("1")
        assertThat(InstagramMediaId.fromShortcode("Z")).isEqualTo("25")
        assertThat(InstagramMediaId.fromShortcode("a")).isEqualTo("26")
        assertThat(InstagramMediaId.fromShortcode("_")).isEqualTo("63")
    }

    @Test
    fun `is positional in base 64`() {
        // "BA" = 1 * 64 + 0
        assertThat(InstagramMediaId.fromShortcode("BA")).isEqualTo("64")
        // "BB" = 1 * 64 + 1
        assertThat(InstagramMediaId.fromShortcode("BB")).isEqualTo("65")
    }

    @Test
    fun `handles both url-safe alphabet extras`() {
        assertThat(InstagramMediaId.fromShortcode("-")).isEqualTo("62")
        assertThat(InstagramMediaId.fromShortcode("_")).isEqualTo("63")
    }

    @Test
    fun `does not overflow when the id exceeds 64-bit range`() {
        // The largest 11-char shortcode is 64^11 - 1, which is far past Long.MAX_VALUE — a Long-based
        // implementation would wrap. The BigInteger path must return the exact value.
        val id = InstagramMediaId.fromShortcode("___________")

        assertThat(id).isEqualTo("73786976294838206463")
        assertThat(id!!.toBigInteger()).isGreaterThan(Long.MAX_VALUE.toBigInteger())
    }

    @Test
    fun `rejects a character outside the alphabet`() {
        // '!' and '/' are not part of the shortcode alphabet.
        assertThat(InstagramMediaId.fromShortcode("Db!DB")).isNull()
        assertThat(InstagramMediaId.fromShortcode("a/b")).isNull()
    }

    @Test
    fun `rejects an empty shortcode`() {
        assertThat(InstagramMediaId.fromShortcode("")).isNull()
    }
}
