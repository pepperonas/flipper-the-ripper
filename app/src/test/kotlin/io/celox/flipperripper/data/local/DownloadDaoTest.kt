package io.celox.flipperripper.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadStatus
import io.celox.flipperripper.testing.sampleRecord
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
class DownloadDaoTest {
    private lateinit var db: FlipperDatabase
    private lateinit var dao: DownloadDao

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                FlipperDatabase::class.java,
            ).allowMainThreadQueries().build()
        dao = db.downloadDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `upsert then observe returns entity`() =
        runTest {
            dao.upsert(DownloadEntity.fromDomain(sampleRecord(id = "a", status = DownloadStatus.QUEUED)))
            val all = dao.observeAll().first()
            assertThat(all).hasSize(1)
            assertThat(all.first().id).isEqualTo("a")
        }

    @Test
    fun `observeAll orders newest first`() =
        runTest {
            dao.upsert(DownloadEntity.fromDomain(sampleRecord(id = "old")).copy(createdAtEpochMs = 100))
            dao.upsert(DownloadEntity.fromDomain(sampleRecord(id = "new")).copy(createdAtEpochMs = 200))
            val all = dao.observeAll().first()
            assertThat(all.map { it.id }).containsExactly("new", "old").inOrder()
        }

    @Test
    fun `updateProgress changes status and percent`() =
        runTest {
            dao.upsert(DownloadEntity.fromDomain(sampleRecord(id = "p", status = DownloadStatus.QUEUED)))
            dao.updateProgress("p", DownloadStatus.RUNNING.name, 42f, 999)
            val entity = dao.getById("p")!!
            assertThat(entity.status).isEqualTo("RUNNING")
            assertThat(entity.progressPercent).isEqualTo(42f)
        }

    @Test
    fun `markCompleted stores uri and size`() =
        runTest {
            dao.upsert(DownloadEntity.fromDomain(sampleRecord(id = "c", status = DownloadStatus.RUNNING)))
            dao.markCompleted("c", DownloadStatus.COMPLETED.name, "content://x", "file.mp4", 1234, 1000)
            val entity = dao.getById("c")!!
            assertThat(entity.status).isEqualTo("COMPLETED")
            assertThat(entity.mediaUri).isEqualTo("content://x")
            assertThat(entity.fileName).isEqualTo("file.mp4")
            assertThat(entity.sizeBytes).isEqualTo(1234)
        }

    @Test
    fun `markFailed stores error`() =
        runTest {
            dao.upsert(DownloadEntity.fromDomain(sampleRecord(id = "f", status = DownloadStatus.RUNNING)))
            dao.markFailed("f", DownloadStatus.FAILED.name, "PrivateVideo", "It's private", 1000)
            val entity = dao.getById("f")!!
            assertThat(entity.status).isEqualTo("FAILED")
            assertThat(entity.errorKind).isEqualTo("PrivateVideo")
            assertThat(entity.errorMessage).isEqualTo("It's private")
        }

    @Test
    fun `delete and clear remove rows`() =
        runTest {
            dao.upsert(DownloadEntity.fromDomain(sampleRecord(id = "1")))
            dao.upsert(DownloadEntity.fromDomain(sampleRecord(id = "2")))
            dao.delete("1")
            assertThat(dao.observeAll().first().map { it.id }).containsExactly("2")
            dao.clear()
            assertThat(dao.observeAll().first()).isEmpty()
        }
}
