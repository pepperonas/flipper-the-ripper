package io.celox.flipperripper.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadActivityTest {
    @Test
    fun `a paused download is pending but not active`() {
        // The distinction the History sections are built on: it still belongs to the queue, but
        // nothing is happening for it, so it must not draw a spinner.
        assertThat(DownloadStatus.PAUSED.isPending).isTrue()
        assertThat(DownloadStatus.PAUSED.isActive).isFalse()
    }

    @Test
    fun `every in-flight phase counts as active`() {
        listOf(
            DownloadStatus.PREPARING,
            DownloadStatus.RUNNING,
            DownloadStatus.PROCESSING,
        ).forEach { assertThat(it.isActive).isTrue() }
    }

    @Test
    fun `a download waiting its turn is not drawn as working`() {
        // It was, until the queue existed — and with one runner that wait is minutes, so a moving
        // progress bar over a download nobody has started is a lie the UI tells.
        assertThat(DownloadStatus.QUEUED.isActive).isFalse()
        assertThat(DownloadStatus.QUEUED.isWaiting).isTrue()
        assertThat(DownloadStatus.QUEUED.isPending).isTrue()
    }

    @Test
    fun `nothing finished is waiting`() {
        listOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.CANCELLED, DownloadStatus.PAUSED)
            .forEach { assertThat(it.isWaiting).isFalse() }
    }

    @Test
    fun `finished statuses are neither active nor pending`() {
        listOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.CANCELLED).forEach {
            assertThat(it.isActive).isFalse()
            assertThat(it.isPending).isFalse()
        }
    }

    @Test
    fun `post-processing cannot be paused`() {
        // The transfer is already done there; stopping an ffmpeg merge or a MediaStore copy leaves
        // a half-written file and nothing to continue from.
        assertThat(DownloadStatus.PROCESSING.isPausable).isFalse()
        assertThat(DownloadStatus.PROCESSING.isCancellable).isTrue()
    }

    @Test
    fun `only what has not started can be dragged`() {
        assertThat(DownloadStatus.QUEUED.isReorderable).isTrue()
        assertThat(DownloadStatus.PAUSED.isReorderable).isTrue()
        listOf(
            DownloadStatus.PREPARING,
            DownloadStatus.RUNNING,
            DownloadStatus.PROCESSING,
            DownloadStatus.COMPLETED,
            DownloadStatus.FAILED,
            DownloadStatus.CANCELLED,
        ).forEach { assertThat(it.isReorderable).isFalse() }
    }

    @Test
    fun `everything in the queue can be cancelled`() {
        DownloadStatus.entries.filter { it.isPending }.forEach {
            assertThat(it.isCancellable).isTrue()
        }
    }
}
