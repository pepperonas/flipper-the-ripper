package io.celox.flipperripper.data.work

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.celox.flipperripper.domain.model.DownloadStatus
import org.junit.Test
import java.io.File

/**
 * The runner's and the queue's contract with the record — the part no unit test can reach, because
 * running them needs WorkManager, an engine and MediaStore.
 *
 * Each pin here is a bug that shipped, and each failed silently: nothing crashed, nothing logged,
 * the state was simply wrong on screen.
 */
class DownloadRunnerContractTest {
    private val runner = read("DownloadRunner.kt")
    private val queue = read("DownloadQueueWorker.kt")
    private val repository =
        read("../repository/DownloadRepositoryImpl.kt")

    /** The file without comments, so a comment quoting a removed line cannot satisfy a pin. */
    private fun read(name: String): String =
        File("src/main/kotlin/io/celox/flipperripper/data/work/$name").readText()
            .lines()
            .filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") || it.trim().startsWith("/*") }
            .joinToString("\n")

    @Test
    fun `the runner never writes a whole row back`() {
        // `dao.upsert(record.copy(...))` replaces every column from a copy read at the start of the
        // run — which is how a running download was reset to QUEUED / 0 %. Field-scoped updates
        // cannot do that, so the whole-row write must not come back.
        assertThat(runner).doesNotContain("dao.upsert(")
    }

    @Test
    fun `the runner reports every phase it moves through`() {
        listOf(DownloadStatus.PREPARING, DownloadStatus.RUNNING, DownloadStatus.PROCESSING)
            .forEach {
                assertWithMessage("phase ${it.name}").that(runner).contains("DownloadStatus.${it.name}")
            }
    }

    @Test
    fun `the progress callback does not block the engine`() {
        // It used to do a Room write and a binder call inside `runBlocking`, on the engine's own
        // download thread, about a hundred times per file.
        assertThat(runner).doesNotContain("runBlocking")
    }

    @Test
    fun `the record and the notification are written from one place`() {
        // Two call sites drifting apart is exactly how the card and the shade came to disagree.
        // `applyPhase` is that place: it writes the row and hands the same values on, so a phase
        // cannot reach one surface without reaching the other.
        val body = runner.substringAfter("private suspend fun applyPhase").substringBefore("\n    }")
        assertThat(body).contains("dao.updateProgress")
        assertThat(body).contains("onPhase(")
        assertThat(runner.split("onPhase(PhaseUpdate").size - 1).isEqualTo(1)
    }

    @Test
    fun `the queue posts its notification from one place`() {
        val body = queue.substringAfter("private suspend fun publish").substringBefore("\n    }")
        assertThat(body).contains("setForeground")
        assertThat(queue.split("setForeground(").size - 1).isEqualTo(1)
    }

    @Test
    fun `the queue asks the ordering rule what runs next`() {
        // Picking "the first row the query returned" would make the order an accident of SQL rather
        // than a rule anyone can test.
        assertThat(queue).contains("QueueOrdering.next(")
    }

    @Test
    fun `pausing writes the status before it stops the engine`() {
        // Load-bearing order: the runner sees a killed process and asks the record whether this was
        // a pause or a cancel. Stopping first would race, and a lost race deletes the partial file.
        val body = repository.substringAfter("override suspend fun pause").substringBefore("\n    }")
        val statusAt = body.indexOf("DownloadStatus.PAUSED.name")
        val cancelAt = body.indexOf("engine.cancel(")
        assertWithMessage("pause writes PAUSED and stops the engine").that(statusAt).isGreaterThan(-1)
        assertWithMessage("pause stops the engine").that(cancelAt).isGreaterThan(-1)
        assertWithMessage("the status write comes first").that(statusAt).isLessThan(cancelAt)
    }

    @Test
    fun `cancelling a download never cancels the queue itself`() {
        // The queue work is shared by every download waiting behind this one; cancelling it would
        // stop them all.
        val body = repository.substringAfter("override suspend fun cancel").substringBefore("\n    }")
        assertThat(body).doesNotContain("cancelUniqueWork")
        assertThat(body).doesNotContain("DownloadQueueWorker.WORK_NAME")
    }
}
