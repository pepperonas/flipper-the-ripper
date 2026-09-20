package io.celox.flipperripper.data.work

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.data.engine.DownloadedFile
import io.celox.flipperripper.data.local.DownloadEntity
import io.celox.flipperripper.data.local.FlipperDatabase
import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.DownloadStatus
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.testing.FakeMediaStoreWriter
import io.celox.flipperripper.testing.FakeYtDlpEngine
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * What the runner does with the half-downloaded file when a download stops.
 *
 * Pause and cancel look identical from the engine's side — both arrive as a killed process — and the
 * difference is entirely in what happens to the working directory. Getting it wrong is silent: the
 * user taps Resume and the download starts from zero, having thrown away what it had.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DownloadRunnerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: FlipperDatabase
    private lateinit var engine: FakeYtDlpEngine
    private lateinit var runner: DownloadRunner

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(context, FlipperDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        engine = FakeYtDlpEngine()
        runner =
            DownloadRunner(
                context = context,
                dao = db.downloadDao(),
                engine = engine,
                mediaWriter = FakeMediaStoreWriter(),
                notifier = DownloadNotifier(context),
            )
    }

    @After
    fun tearDown() {
        db.close()
        runner.workingDirFor(ID).deleteRecursively()
    }

    @Test
    fun `pausing keeps the partial file`() =
        runTest {
            givenRecord(DownloadStatus.PAUSED)
            val partial = givenPartialBytes()
            engine.downloadResult = EngineResult.Failure(DownloadError.Cancelled())

            val outcome = runner.run(ID) {}

            assertThat(outcome).isEqualTo(RunOutcome.STOPPED)
            assertThat(partial.exists()).isTrue()
            // Still paused: the runner must not overwrite the user's decision with CANCELLED.
            assertThat(statusOf()).isEqualTo(DownloadStatus.PAUSED)
        }

    @Test
    fun `cancelling throws the partial file away`() =
        runTest {
            givenRecord(DownloadStatus.RUNNING)
            val partial = givenPartialBytes()
            engine.downloadResult = EngineResult.Failure(DownloadError.Cancelled())

            val outcome = runner.run(ID) {}

            assertThat(outcome).isEqualTo(RunOutcome.STOPPED)
            assertThat(partial.exists()).isFalse()
            assertThat(statusOf()).isEqualTo(DownloadStatus.CANCELLED)
        }

    @Test
    fun `a finished download reports every phase in order`() =
        runTest {
            givenRecord(DownloadStatus.QUEUED)
            val file = File(runner.workingDirFor(ID).apply { mkdirs() }, "clip.mp4").apply { writeText("x") }
            engine.downloadResult = EngineResult.Success(DownloadedFile(file, "mp4"))

            val phases = mutableListOf<DownloadStatus>()
            val outcome = runner.run(ID) { phases += it.status }

            assertThat(outcome).isEqualTo(RunOutcome.COMPLETED)
            // PREPARING before anything transfers, RUNNING while it does, PROCESSING while the file
            // is saved — the sequence the card and the notification both draw from.
            assertThat(phases.first()).isEqualTo(DownloadStatus.PREPARING)
            assertThat(phases).contains(DownloadStatus.RUNNING)
            assertThat(phases.last()).isEqualTo(DownloadStatus.PROCESSING)
            assertThat(statusOf()).isEqualTo(DownloadStatus.COMPLETED)
        }

    @Test
    fun `a pause during the transfer stops the engine and keeps the file`() =
        runTest {
            givenRecord(DownloadStatus.RUNNING)
            val partial = givenPartialBytes()
            // The user taps Pause while bytes are moving: the repository writes PAUSED, and the
            // engine process is still alive. The runner has to notice and stop it, or it downloads
            // to completion something the user already stopped.
            val pausing =
                object : FakeYtDlpEngine() {
                    override suspend fun download(
                        spec: io.celox.flipperripper.data.engine.DownloadSpec,
                        onProgress: (io.celox.flipperripper.domain.model.DownloadProgress) -> Unit,
                    ): EngineResult<DownloadedFile> {
                        onProgress(io.celox.flipperripper.domain.model.DownloadProgress(10f, null, "line 10"))
                        db.downloadDao().updateStatus(ID, DownloadStatus.PAUSED.name, 2)
                        onProgress(io.celox.flipperripper.domain.model.DownloadProgress(20f, null, "line 20"))
                        // Whatever happens next, the engine reports the process was killed.
                        return EngineResult.Failure(DownloadError.Cancelled())
                    }
                }
            val pausingRunner =
                DownloadRunner(
                    context = context,
                    dao = db.downloadDao(),
                    engine = pausing,
                    mediaWriter = FakeMediaStoreWriter(),
                    notifier = DownloadNotifier(context),
                )

            val outcome = pausingRunner.run(ID) {}

            assertThat(outcome).isEqualTo(RunOutcome.STOPPED)
            assertThat(statusOf()).isEqualTo(DownloadStatus.PAUSED)
            assertThat(partial.exists()).isTrue()
            assertThat(pausing.cancelled).contains(ID)
        }

    @Test
    fun `a network failure asks the queue to try again later`() =
        runTest {
            givenRecord(DownloadStatus.QUEUED)
            engine.downloadResult = EngineResult.Failure(DownloadError.Network("offline"))

            assertThat(runner.run(ID) {}).isEqualTo(RunOutcome.RETRYABLE)
            assertThat(statusOf()).isEqualTo(DownloadStatus.FAILED)
        }

    private suspend fun statusOf(): DownloadStatus? =
        db.downloadDao().getById(ID)?.let { DownloadStatus.valueOf(it.status) }

    private fun givenPartialBytes(): File =
        File(runner.workingDirFor(ID).apply { mkdirs() }, "clip.mp4.part").apply { writeText("half a video") }

    private suspend fun givenRecord(status: DownloadStatus) {
        db.downloadDao().upsert(
            DownloadEntity(
                id = ID,
                sourceUrl = "https://youtu.be/x",
                platform = "YOUTUBE",
                title = "Clip",
                mode = "VIDEO",
                thumbnailUrl = null,
                status = status.name,
                mediaUri = null,
                fileName = null,
                sizeBytes = null,
                progressPercent = null,
                errorKind = null,
                errorMessage = null,
                queueOrder = 1,
                createdAtEpochMs = 1,
                updatedAtEpochMs = 1,
            ),
        )
    }

    private companion object {
        const val ID = "rec-1"
    }
}
