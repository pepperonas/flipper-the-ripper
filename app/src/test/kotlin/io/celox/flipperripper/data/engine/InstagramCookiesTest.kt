package io.celox.flipperripper.data.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The Cookie header for a CDN media download. The invariant that matters: signed out it must be exactly
 * the CDN host's own cookies (so public downloads are unchanged), and signed in it must additionally carry
 * the instagram.com account session that gates logged-in media.
 */
class InstagramCookiesTest {
    @Test
    fun `signed out sends only the cdn host cookies`() {
        // No sessionid in the instagram.com jar => nothing is lifted, even if a csrftoken exists there.
        val header = InstagramCookies.merge(cdnCookie = "datr=abc; ig_did=xyz", instagramCookie = "csrftoken=tok")

        assertThat(header).isEqualTo("datr=abc; ig_did=xyz")
    }

    @Test
    fun `signed in adds the account session cookies`() {
        val header =
            InstagramCookies.merge(
                cdnCookie = "datr=abc",
                instagramCookie = "sessionid=SID; ds_user_id=42; csrftoken=CT; ig_did=ignored",
            )

        assertThat(header).contains("datr=abc")
        assertThat(header).contains("sessionid=SID")
        assertThat(header).contains("ds_user_id=42")
        assertThat(header).contains("csrftoken=CT")
        // Only the gating cookies are lifted; unrelated jar cookies stay behind.
        assertThat(header).doesNotContain("ig_did=ignored")
    }

    @Test
    fun `the cdn host cookie is never duplicated by a same-named session cookie`() {
        // If the CDN host already carries a csrftoken, the instagram.com one must not be appended twice.
        val header =
            InstagramCookies.merge(
                cdnCookie = "csrftoken=FROM_CDN",
                instagramCookie = "sessionid=SID; csrftoken=FROM_IG",
            )

        assertThat(header).contains("csrftoken=FROM_CDN")
        assertThat(header).doesNotContain("csrftoken=FROM_IG")
        assertThat(header).contains("sessionid=SID")
    }

    @Test
    fun `works when the cdn jar is empty but signed in`() {
        val header = InstagramCookies.merge(cdnCookie = null, instagramCookie = "sessionid=SID; ds_user_id=42")

        assertThat(header).isEqualTo("sessionid=SID; ds_user_id=42")
    }

    @Test
    fun `both jars empty yields an empty header`() {
        assertThat(InstagramCookies.merge(null, null)).isEmpty()
        assertThat(InstagramCookies.merge("", "")).isEmpty()
    }

    @Test
    fun `trims stray whitespace between pairs`() {
        val header = InstagramCookies.merge(cdnCookie = "  datr=abc ;  ig_did=xyz ", instagramCookie = null)

        assertThat(header).isEqualTo("datr=abc; ig_did=xyz")
    }
}
