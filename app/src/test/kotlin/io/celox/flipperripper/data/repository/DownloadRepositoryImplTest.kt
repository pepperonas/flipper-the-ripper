package io.celox.flipperripper.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.data.local.FlipperDatabase
import io.celox.flipperripper.data.work.DownloadWorker
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
                    DownloadRequest("https://youtu.be/x", Platform.YOUTUBE, DownloadMode.VIDEO, title = "Clip"),
                )
            assertThat(id).isEqualTo("rec-1")

            val record = repository.observeRecord(id).first()
            assertThat(record).isNotNull()
            assertThat(record!!.status).isEqualTo(DownloadStatus.QUEUED)
            assertThat(record.title).isEqualTo("Clip")

            val infos = workManager.getWorkInfosForUniqueWork(DownloadWorker.WORK_NAME_PREFIX + id).get()
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
    fun `retry re-queues a failed record`() =
        runTest {
            val id = repository.enqueue(DownloadRequest("https://youtu.be/x", Platform.YOUTUBE))
            db.downloadDao().markFailed(id, DownloadStatus.FAILED.name, "Network", "offline", 1)
            repository.retry(id)
            assertThat(repository.observeRecord(id).first()!!.status).isEqualTo(DownloadStatus.QUEUED)
        }
}
