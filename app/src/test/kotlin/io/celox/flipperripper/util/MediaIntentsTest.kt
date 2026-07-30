package io.celox.flipperripper.util

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadMode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Intents for the saved file. The share flow must hand a real, readable `content://` URI to other apps
 * and must never expose a `file://` URI (FileUriExposedException on API 24+).
 */
@RunWith(RobolectricTestRunner::class)
class MediaIntentsTest {
    private val contentUri = "content://media/external/video/media/42"

    @Test
    fun `share builds a chooser wrapping a send with the media as a read-granted stream`() {
        val chooser = MediaIntents.shareIntent(contentUri, DownloadMode.VIDEO)

        assertThat(chooser).isNotNull()
        assertThat(chooser!!.action).isEqualTo(Intent.ACTION_CHOOSER)
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertThat(send).isNotNull()
        assertThat(send!!.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(send.type).isEqualTo("video/*")
        assertThat(send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM).toString())
            .isEqualTo(contentUri)
        assertThat(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
    }

    @Test
    fun `share uses an audio mime for audio downloads`() {
        val chooser = MediaIntents.shareIntent("content://media/external/audio/media/7", DownloadMode.AUDIO)
        val send = chooser!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertThat(send!!.type).isEqualTo("audio/*")
    }

    @Test
    fun `share refuses a null, blank or non-content uri`() {
        assertThat(MediaIntents.shareIntent(null, DownloadMode.VIDEO)).isNull()
        assertThat(MediaIntents.shareIntent("", DownloadMode.VIDEO)).isNull()
        assertThat(MediaIntents.shareIntent("file:///storage/emulated/0/Movies/x.mp4", DownloadMode.VIDEO))
            .isNull()
    }

    @Test
    fun `view builds an action_view for the content uri, refusing file uris`() {
        val view = MediaIntents.viewIntent(contentUri, DownloadMode.VIDEO)
        assertThat(view).isNotNull()
        assertThat(view!!.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(view.type).isEqualTo("video/*")
        assertThat(MediaIntents.viewIntent("file:///x.mp4", DownloadMode.VIDEO)).isNull()
    }
}
