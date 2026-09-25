package io.celox.flipperripper.domain.util

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.Platform
import org.junit.Test

class InstagramSignInTest {
    private val login = DownloadError.LoginRequired("x").kind

    @Test
    fun `an Instagram login error without a session offers the sign-in`() {
        assertThat(InstagramSignIn.shouldOffer(Platform.INSTAGRAM, login, signedIn = false)).isTrue()
    }

    @Test
    fun `signed in already, signing in again would not help`() {
        assertThat(InstagramSignIn.shouldOffer(Platform.INSTAGRAM, login, signedIn = true)).isFalse()
    }

    @Test
    fun `other platforms have no in-app sign-in to offer`() {
        Platform.entries.filter { it != Platform.INSTAGRAM }.forEach {
            assertThat(InstagramSignIn.shouldOffer(it, login, signedIn = false)).isFalse()
        }
    }

    @Test
    fun `other errors are not about an account`() {
        listOf(
            DownloadError.Network().kind,
            DownloadError.PrivateVideo("x").kind,
            DownloadError.Unavailable("x").kind,
            null,
        ).forEach { assertThat(InstagramSignIn.shouldOffer(Platform.INSTAGRAM, it, signedIn = false)).isFalse() }
    }

    @Test
    fun `the kind it matches is the one the download runner stores`() {
        // DownloadRunner writes error.kind into the record; a rename of the class must not silently
        // switch the offer off.
        assertThat(InstagramSignIn.LOGIN_REQUIRED_KIND).isEqualTo("LoginRequired")
    }
}
