package io.celox.flipperripper.data.share

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The rule that decides whether an incoming intent carries a shared link.
 *
 * Its failure mode is silent, and it has already happened: v1.8.2 widened the manifest filter to every
 * `text/` subtype, the activity kept demanding `text/plain`, and a share from any sender that labels its
 * text differently opened the app and was dropped without a message.
 */
class SharedTextTest {
    private val send = android.content.Intent.ACTION_SEND

    @Test
    fun `a plain-text share is read`() {
        assertThat(SharedText.isShare(send, "text/plain")).isTrue()
    }

    @Test
    fun `text the sender labels differently is read too`() {
        // The whole point of the widened filter: these reach the app, so they must not be dropped.
        listOf("text/html", "text/x-url", "text/uri-list", "text/plain;charset=utf-8")
            .forEach { assertThat(SharedText.isShare(send, it)).isTrue() }
    }

    @Test
    fun `a share of something that is not text is left alone`() {
        // The app takes links. An image or video share is someone else's job, and reading
        // EXTRA_TEXT from it would at best find a caption.
        listOf("image/jpeg", "video/mp4", "application/pdf", "*/*")
            .forEach { assertThat(SharedText.isShare(send, it)).isFalse() }
    }

    @Test
    fun `a type that merely mentions text is not a text type`() {
        // Prefix matching on the wrong side: these are not `text/` types.
        listOf("application/text", "x-text/plain", "textual/plain")
            .forEach { assertThat(SharedText.isShare(send, it)).isFalse() }
    }

    @Test
    fun `launching the app normally is not a share`() {
        // The launcher intent reaches the same activity; treating it as a share would post whatever
        // stale extra it happened to carry.
        assertThat(SharedText.isShare(android.content.Intent.ACTION_MAIN, "text/plain")).isFalse()
        assertThat(SharedText.isShare(android.content.Intent.ACTION_VIEW, "text/plain")).isFalse()
        assertThat(SharedText.isShare(null, "text/plain")).isFalse()
    }

    @Test
    fun `a send with no type at all is not read`() {
        assertThat(SharedText.isShare(send, null)).isFalse()
        assertThat(SharedText.isShare(send, "")).isFalse()
    }
}
