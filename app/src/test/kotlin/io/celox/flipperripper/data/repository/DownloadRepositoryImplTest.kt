package io.celox.flipperripper.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.data.local.FlipperDatabase
import io.celox.flipperripper.data.work.DownloadQueueWorker
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadRequest
import io.celox.flipperripper.domain.model.DownloadStatus
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.testing.FakeIdGenerator
import io.celox.flipperripper.testing.FakeYtDlpEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DownloadRepositoryImplTest {
    private lateinit var db: FlipperDatabase
    private lateinit var workManager: WorkManager
    private lateinit var engine: FakeYtDlpEngine
    private lateinit var repository: DownloadRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, FlipperDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        workManager = WorkManager.getInstance(context)
        engine = FakeYtDlpEngine()
        repository =
            DownloadRepositoryImpl(
                dao = db.downloadDao(),
                workManager = workManager,
                engine = engine,
                idGenerator = FakeIdGenerator(mutableListOf("rec-1", "rec-2")),
            )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `enqueue persists a queued record and schedules unique work`() =
        runTest {
            val id =
                repository.enqueue(
                    DownloadRequest("https://youtu.be/x", Platform.YOUTUBE, mode = DownloadMode.VIDEO, title = "Clip"),
                )
            assertThat(id).isEqualTo("rec-1")

            val record = repository.observeRecord(id).first()
            assertThat(record).isNotNull()
            assertThat(record!!.status).isEqualTo(DownloadStatus.QUEUED)
            assertThat(record.title).isEqualTo("Clip")

            // One queue worker for the whole queue, not one job per download — that is what makes
            // the order in the table the order things actually run in.
            val infos = workManager.getWorkInfosForUniqueWork(DownloadQueueWorker.WORK_NAME).get()
            assertThat(infos).hasSize(1)
        }

    @Test
    fun `history observes inserted records`() =
        runTest {
            repository.enqueue(DownloadRequest("https://youtu.be/x", Platform.YOUTUBE))
            repository.enqueue(DownloadRequest("https://www.tiktok.com/@a/video/1", Platform.TIKTOK))
            assertThat(repository.observeHistory().first()).hasSize(2)
        }

    @Test
    fun `cancel marks a queued record cancelled and calls engine`() =
        runTest {
            val id = repository.enqueue(DownloadRequest("https://youtu.be/x", Platform.YOUTUBE))
            repository.cancel(id)
            val record = repository.observeRecord(id).first()
            assertThat(record!!.status).isEqualTo(DownloadStatus.CANCELLED)
            assertThat(engine.cancelled).contains(id)
        }

    @Test
    fun `delete removes the record`() =
        runTest {
            val id = repository.enqueue(DownloadRequest("https://youtu.be/x", Platform.YOUTUBE))
            repository.delete(id)
            assertThat(repository.observeRecord(id).first()).isNull()
        }

    @Test
    fun `clearHistory empties the table`() =
        runTest {
            repository.enqueue(DownloadRequest("https://youtu.be/x", Platform.YOUTUBE))
            repository.clearHistory()
            assertThat(repository.observeHistory().first()).isEmpty()
        }

    @Test
    fun `pause keeps the record in the queue and stops the engine`() =
        runTest {
            val id = repository.enqueue(DownloadRequest("https://youtu.be/x", Platform.YOUTUBE))
            val before = repository.observeRecord(id).first()!!.queueOrder
            repository.pause(id)

            val record = repository.observeRecord(id).first()!!
            assertThat(record.status).isEqualTo(DownloadStatus.PAUSED)
            // Pausing must not cost the download its place.
            assertThat(record.queueOrder).isEqualTo(before)
            assertThat(engine.cancelled).contains(id)
        }

    @Test
    fun `resume puts it back at the position it held`() =
        runTest {
            val first = repository.enqueue(DownloadRequest("https://youtu.be/1", Platform.YOUTUBE))
            val second = repository.enqueue(DownloadRequest("https://youtu.be/2", Platform.YOUTUBE))
            repository.pause(first)
            repository.resume(first)

            val records = repository.observeHistory().first().associateBy { it.id }
            assertThat(records.getValue(first).status).isEqualTo(DownloadStatus.QUEUED)
            assertThat(records.getValue(first).queueOrder).isLessThan(records.getValue(second).queueOrder)
        }

    @Test
    fun `a finished download cannot be paused`() =
        runTest {
            val id = repository.enqueue(DownloadRequest("https://youtu.be/x", Platform.YOUTUBE))
            db.downloadDao().markCompleted(id, DownloadStatus.COMPLETED.name, "content://x", "x.mp4", 1, 1)
            repository.pause(id)
            assertThat(repository.observeRecord(id).first()!!.status).isEqualTo(DownloadStatus.COMPLETED)
        }

    @Test
    fun `reorder swaps the positions of two waiting downloads`() =
        runTest {
            val first = repository.enqueue(DownloadRequest("https://youtu.be/1", Platform.YOUTUBE))
            val second = repository.enqueue(DownloadRequest("https://youtu.be/2", Platform.YOUTUBE))
            repository.reorder(listOf(second, first))

            val records = repository.observeHistory().first().associateBy { it.id }
            assertThat(records.getValue(second).queueOrder).isLessThan(records.getValue(first).queueOrder)
        }

    @Test
    fun `reorder leaves a running download where it is`() =
        runTest {
            val running = repository.enqueue(DownloadRequest("https://youtu.be/1", Platform.YOUTUBE))
            val waiting = repository.enqueue(DownloadRequest("https://youtu.be/2", Platform.YOUTUBE))
            db.downloadDao().updateStatus(running, DownloadStatus.RUNNING.name, 5)
            val before = repository.observeRecord(running).first()!!.queueOrder

            // The UI cannot drag a running card, but a stale list could still name it.
            repository.reorder(listOf(waiting, running))

            assertThat(repository.observeRecord(running).first()!!.queueOrder).isEqualTo(before)
        }

    @Test
    fun `each new download joins the back of the queue`() =
        runTest {
            val first = repository.enqueue(DownloadRequest("https://youtu.be/1", Platform.YOUTUBE))
            val second = repository.enqueue(DownloadRequest("https://youtu.be/2", Platform.YOUTUBE))
            val records = repository.observeHistory().first().associateBy { it.id }
            assertThat(records.getValue(first).queueOrder).isLessThan(records.getValue(second).queueOrder)
        }

    @Test
    fun `a retry goes behind downloads that have been waiting`() =
        runTest {
            val failed = repository.enqueue(DownloadRequest("https://youtu.be/1", Platform.YOUTUBE))
            val waiting = repository.enqueue(DownloadRequest("https://youtu.be/2", Platform.YOUTUBE))
            db.downloadDao().markFailed(failed, DownloadStatus.FAILED.name, "Network", "offline", 1)
            repository.retry(failed)

            val records = repository.observeHistory().first().associateBy { it.id }
            assertThat(records.getValue(waiting).queueOrder).isLessThan(records.getValue(failed).queueOrder)
        }

    @Test
    fun `retry re-queues a failed record`() =
        runTest {
            val id = repository.enqueue(DownloadRequest("https://youtu.be/x", Platform.YOUTUBE))
            db.downloadDao().markFailed(id, DownloadStatus.FAILED.name, "Network", "offline", 1)
            repository.retry(id)
            assertThat(repository.observeRecord(id).first()!!.status).isEqualTo(DownloadStatus.QUEUED)
        }
}
