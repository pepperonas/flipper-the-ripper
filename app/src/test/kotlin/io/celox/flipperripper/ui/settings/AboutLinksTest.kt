package io.celox.flipperripper.ui.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The About card must never show a made-up address: every link is pinned to its real target. */
class AboutLinksTest {
    @Test
    fun `author, site and repository are the real ones`() {
        assertThat(AboutLinks.AUTHOR).isEqualTo("Martin Pfeffer")
        assertThat(AboutLinks.WEBSITE_URL).isEqualTo("https://celox.io")
        assertThat(AboutLinks.REPO_URL).isEqualTo("https://github.com/pepperonas/flipper-the-ripper")
        assertThat(AboutLinks.LICENSE_URL).startsWith(AboutLinks.REPO_URL)
        assertThat(AboutLinks.LICENSE_URL).endsWith("/LICENSE")
        assertThat(AboutLinks.LICENSE_NAME).isEqualTo("MIT License")
    }

    @Test
    fun `donate link goes to the author's paypal account with the app as payment note`() {
        val url = AboutLinks.donateUrl()
        assertThat(url).startsWith("https://www.paypal.com/donate/?")
        assertThat(url).contains("business=martin.pfeffer@celox.io")
        assertThat(url).contains("currency_code=EUR")
        // Spaces must be %20, not '+': PayPal shows '+' literally in the note.
        assertThat(url).endsWith("item_name=Flipper%20the%20Ripper")
        assertThat(url).doesNotContain("+")
    }

    @Test
    fun `every link is https`() {
        listOf(AboutLinks.WEBSITE_URL, AboutLinks.REPO_URL, AboutLinks.LICENSE_URL, AboutLinks.donateUrl())
            .forEach { assertThat(it).startsWith("https://") }
    }
}
