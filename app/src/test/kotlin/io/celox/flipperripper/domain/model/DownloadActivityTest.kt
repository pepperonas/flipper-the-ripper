package io.celox.flipperripper.domain.model

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.testing.sampleRecord
import org.junit.Test

/**
 * The app's continuous motion is gated on this predicate, so "is anything actually happening?" has to
 * be exact — a wrong `true` means the UI animates forever again.
 */
class DownloadActivityTest {
    @Test
    fun `queued and running count as active`() {
        assertThat(DownloadStatus.QUEUED.isActive).isTrue()
        assertThat(DownloadStatus.RUNNING.isActive).isTrue()
    }

    @Test
    fun `terminal states do not count as active`() {
        assertThat(DownloadStatus.COMPLETED.isActive).isFalse()
        assertThat(DownloadStatus.FAILED.isActive).isFalse()
        assertThat(DownloadStatus.CANCELLED.isActive).isFalse()
    }

    @Test
    fun `every status is classified`() {
        // Guards against a new status silently defaulting to "not active".
        val active = DownloadStatus.entries.filter { it.isActive }
        assertThat(active).containsExactly(DownloadStatus.QUEUED, DownloadStatus.RUNNING)
    }

    @Test
    fun `empty history has no active download`() {
        assertThat(emptyList<DownloadRecord>().hasActiveDownload()).isFalse()
    }

    @Test
    fun `a history of finished downloads has no active download`() {
        val history =
            listOf(
                sampleRecord(id = "a", status = DownloadStatus.COMPLETED),
                sampleRecord(id = "b", status = DownloadStatus.FAILED),
                sampleRecord(id = "c", status = DownloadStatus.CANCELLED),
            )

        assertThat(history.hasActiveDownload()).isFalse()
    }

    @Test
    fun `a single running download among finished ones counts`() {
        val history =
            listOf(
                sampleRecord(id = "a", status = DownloadStatus.COMPLETED),
                sampleRecord(id = "b", status = DownloadStatus.RUNNING),
                sampleRecord(id = "c", status = DownloadStatus.FAILED),
            )

        assertThat(history.hasActiveDownload()).isTrue()
    }

    @Test
    fun `a queued download alone counts`() {
        assertThat(listOf(sampleRecord(status = DownloadStatus.QUEUED)).hasActiveDownload()).isTrue()
    }
}
