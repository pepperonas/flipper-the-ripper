package io.celox.flipperripper.data.work

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.data.engine.DownloadSpec
import io.celox.flipperripper.data.engine.DownloadedFile
import io.celox.flipperripper.data.local.DownloadEntity
import io.celox.flipperripper.data.local.FlipperDatabase
import io.celox.flipperripper.domain.model.DownloadProgress
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
 * The queue's whole reason for existing: downloads run **one at a time, in the stated order**.
 *
 * Before 1.10.0 each download was its own WorkManager job and several ran at once, so the order in
 * the table was decoration. The engine is the seam used to observe it here — the order in which it
 * is asked to fetch is the order things actually run in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DownloadQueueWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: FlipperDatabase
    private lateinit var engine: RecordingEngine
    private lateinit var runner: DownloadRunner

    /** Notes the order downloads are handed to it, and hands back a file so they complete. */
    private class RecordingEngine : FakeYtDlpEngine() {
        val order = mutableListOf<String>()

        override suspend fun download(
            spec: DownloadSpec,
            onProgress: (DownloadProgress) -> Unit,
        ): EngineResult<DownloadedFile> {
            order += spec.processId
            onProgress(DownloadProgress(50f, null, "line 50"))
            val file = File(spec.workingDir.apply { mkdirs() }, "clip.mp4").apply { writeText("x") }
            return EngineResult.Success(DownloadedFile(file, "mp4"))
        }
    }

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(context, FlipperDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        engine = RecordingEngine()
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
        File(context.cacheDir, "downloads").deleteRecursively()
    }

    @Test
    fun `the queue runs downloads in queue order, not insertion order`() =
        runTest {
            given("c", order = 30)
            given("a", order = 10)
            given("b", order = 20)

            worker().doWork()

            assertThat(engine.order).containsExactly("a", "b", "c").inOrder()
        }

    @Test
    fun `a paused download is skipped and stays paused`() =
        runTest {
            given("paused", order = 10, status = DownloadStatus.PAUSED)
            given("waiting", order = 20)

            worker().doWork()

            assertThat(engine.order).containsExactly("waiting")
            assertThat(statusOf("paused")).isEqualTo(DownloadStatus.PAUSED)
        }

    @Test
    fun `rows left mid-flight by a killed process are picked up again`() =
        runTest {
            // Nothing else runs downloads, so a row still claiming RUNNING is wreckage — either from
            // a process the system killed or from the pre-1.10 per-download workers.
            given("stranded", order = 10, status = DownloadStatus.RUNNING)

            worker().doWork()

            assertThat(engine.order).containsExactly("stranded")
            assertThat(statusOf("stranded")).isEqualTo(DownloadStatus.COMPLETED)
        }

    @Test
    fun `an empty queue is not an error`() =
        runTest {
            assertThat(worker().doWork()).isEqualTo(ListenableWorker.Result.success())
            assertThat(engine.order).isEmpty()
        }

    private fun worker(): DownloadQueueWorker =
        TestListenableWorkerBuilder<DownloadQueueWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker =
                        DownloadQueueWorker(
                            appContext,
                            workerParameters,
                            db.downloadDao(),
                            runner,
                            DownloadNotifier(appContext),
                        )
                },
            )
            .build()

    private suspend fun statusOf(id: String): DownloadStatus? =
        db.downloadDao().getById(id)?.let { DownloadStatus.valueOf(it.status) }

    private suspend fun given(id: String, order: Long, status: DownloadStatus = DownloadStatus.QUEUED) {
        db.downloadDao().upsert(
            DownloadEntity(
                id = id,
                sourceUrl = "https://youtu.be/$id",
                platform = "YOUTUBE",
                title = id,
                mode = "VIDEO",
                thumbnailUrl = null,
                status = status.name,
                mediaUri = null,
                fileName = null,
                sizeBytes = null,
                progressPercent = null,
                errorKind = null,
                errorMessage = null,
                queueOrder = order,
                createdAtEpochMs = order,
                updatedAtEpochMs = order,
            ),
        )
    }
}
