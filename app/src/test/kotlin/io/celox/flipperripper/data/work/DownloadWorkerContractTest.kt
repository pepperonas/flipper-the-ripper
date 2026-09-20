package io.celox.flipperripper.data.work

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.celox.flipperripper.domain.model.DownloadStatus
import org.junit.Test
import java.io.File

/**
 * The worker's contract with the record — the part no unit test can reach, because running the
 * worker needs WorkManager, an engine and MediaStore.
 *
 * Each pin here is a bug that shipped, and each failed silently: nothing crashed, nothing logged,
 * the state was simply wrong on screen.
 */
class DownloadWorkerContractTest {
    private val source =
        File("src/main/kotlin/io/celox/flipperripper/data/work/DownloadWorker.kt").readText()

    /** The file without comments, so a comment quoting a removed line cannot satisfy a pin. */
    private val code =
        source.lines()
            .filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") || it.trim().startsWith("/*") }
            .joinToString("\n")

    @Test
    fun `the worker never writes a whole row back`() {
        // `dao.upsert(record.copy(...))` replaces every column from a copy read at the start of the
        // run — which is how a running download was reset to QUEUED / 0 %. Field-scoped updates
        // cannot do that, so the whole-row write must not come back.
        assertThat(code).doesNotContain("dao.upsert(")
    }

    @Test
    fun `the worker reports every phase it moves through`() {
        listOf(DownloadStatus.PREPARING, DownloadStatus.RUNNING, DownloadStatus.PROCESSING)
            .forEach {
                assertWithMessage("phase ${it.name}").that(code).contains("DownloadStatus.${it.name}")
            }
    }

    @Test
    fun `the progress callback does not block the engine`() {
        // It used to do a Room write and a binder call inside `runBlocking`, on the engine's own
        // download thread, about a hundred times per file.
        assertThat(code).doesNotContain("runBlocking")
    }

    @Test
    fun `the record and the notification are written from one place`() {
        // Two call sites drifting apart is exactly how the card and the shade came to disagree.
        // `applyPhase` is that place: it writes the row and rebuilds the notification from the same
        // values, so a phase cannot reach one surface without reaching the other.
        val body = code.substringAfter("private suspend fun applyPhase").substringBefore("\n    }")
        assertThat(body).contains("dao.updateProgress")
        assertThat(body).contains("setForeground")
        assertThat(code.split("setForeground(").size - 1).isEqualTo(1)
    }
}
