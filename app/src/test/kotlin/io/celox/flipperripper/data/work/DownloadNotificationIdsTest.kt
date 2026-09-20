package io.celox.flipperripper.data.work

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Why a download needs two notification ids.
 *
 * WorkManager cancels the foreground notification's id when the worker finishes. The completed
 * notification was posted under that same id a moment earlier, so WorkManager took it down with it:
 * a successful download left nothing in the shade — measured as zero notifications at +0 s, +2 s and
 * +5 s after completion.
 */
class DownloadNotificationIdsTest {
    @Test
    fun `the terminal notification never shares the id WorkManager cancels`() {
        listOf("a", "record-1", "7f3c9e02-1111-4444-8888-aaaabbbbcccc", "", "üñí©ode")
            .forEach { id ->
                assertWithMessage("record id $id")
                    .that(DownloadNotificationIds.terminal(id))
                    .isNotEqualTo(DownloadNotificationIds.progress(id))
            }
    }

    @Test
    fun `both ids are stable for a given record`() {
        // A notification whose id changed between calls could never be replaced or cancelled again.
        repeat(3) {
            assertThat(DownloadNotificationIds.progress("rec")).isEqualTo(DownloadNotificationIds.progress("rec"))
            assertThat(DownloadNotificationIds.terminal("rec")).isEqualTo(DownloadNotificationIds.terminal("rec"))
        }
    }

    @Test
    fun `different records get different ids`() {
        assertThat(DownloadNotificationIds.progress("a")).isNotEqualTo(DownloadNotificationIds.progress("b"))
        assertThat(DownloadNotificationIds.terminal("a")).isNotEqualTo(DownloadNotificationIds.terminal("b"))
    }

    @Test
    fun `the worker posts terminal notifications under the terminal id only`() {
        // The bug was one character wide: `id.hashCode()` instead of the terminal id. Pinned on the
        // call sites, because nothing at runtime complains when WorkManager removes the notification.
        val worker = java.io.File("src/main/kotlin/io/celox/flipperripper/data/work/DownloadWorker.kt").readText()
        listOf("notifyCompleted(", "notifyFailed(").forEach { call ->
            val args = worker.substringAfter(call).substringBefore(")")
            assertWithMessage("$call uses the terminal id")
                .that(args).contains("DownloadNotificationIds.terminal")
        }
    }
}
