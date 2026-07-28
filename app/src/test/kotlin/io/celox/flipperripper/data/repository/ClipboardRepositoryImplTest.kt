package io.celox.flipperripper.data.repository

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression tests for the clipboard read that froze the app on launch.
 *
 * `HomeScreen` peeks at the clipboard from a `LaunchedEffect`, whose coroutine runs on the main
 * dispatcher. The old implementation was synchronous and called `ClipData.Item.coerceToText()`, which
 * for a URI item opens the owning app's content provider and reads it — blocking cross-process I/O on
 * the UI thread. With a `content://` item from a slow or unresponsive app on the clipboard (routine on
 * devices with a clipboard manager), the app hung immediately after opening.
 */
@RunWith(RobolectricTestRunner::class)
class ClipboardRepositoryImplTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clipboard = context.getSystemService<ClipboardManager>()!!
    private val repository = ClipboardRepositoryImpl(context, Dispatchers.Unconfined)

    private fun setClip(clip: ClipData) {
        clipboard.setPrimaryClip(clip)
    }

    @Test
    fun `finds a supported link in plain clipboard text`() =
        runTest {
            setClip(ClipData.newPlainText("l", "Look at this https://youtu.be/abc123 :)"))

            val result = repository.peekSupportedUrl()

            assertThat(result?.url).isEqualTo("https://youtu.be/abc123")
            assertThat(result?.platform).isEqualTo(Platform.YOUTUBE)
        }

    @Test
    fun `ignores clipboard text without a supported link`() =
        runTest {
            setClip(ClipData.newPlainText("l", "just some notes"))

            assertThat(repository.peekSupportedUrl()).isNull()
        }

    @Test
    fun `reads an http uri item without resolving a provider`() =
        runTest {
            setClip(ClipData.newRawUri("l", Uri.parse("https://www.tiktok.com/@a/video/1")))

            assertThat(repository.peekSupportedUrl()?.platform).isEqualTo(Platform.TIKTOK)
        }

    @Test
    fun `a content uri item is skipped rather than opened`() =
        runTest {
            // The dangerous case: coerceToText() would query this provider on the calling thread.
            // Nothing here can match a video link, so it must simply be ignored.
            setClip(ClipData.newRawUri("l", Uri.parse("content://com.example.slowprovider/item/1")))

            assertThat(repository.peekSupportedUrl()).isNull()
        }

    @Test
    fun `a content uri does not hide a real link in a later item`() =
        runTest {
            val clip = ClipData.newRawUri("l", Uri.parse("content://com.example.slowprovider/item/1"))
            clip.addItem(ClipData.Item("https://www.instagram.com/reel/xyz/"))
            setClip(clip)

            val result = repository.peekSupportedUrl()

            assertThat(result?.platform).isEqualTo(Platform.INSTAGRAM)
        }

    @Test
    fun `an empty clipboard yields null`() =
        runTest {
            setClip(ClipData.newPlainText("l", ""))

            assertThat(repository.peekSupportedUrl()).isNull()
        }
}
