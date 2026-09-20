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
    fun `updating the title leaves the phase and the progress alone`() =
        runTest {
            // The worker used to refresh the title by writing a whole row back, from a copy it had
            // read while the record still said QUEUED. Every such write reset a running download to
            // QUEUED / 0 % — twice per download, once after metadata resolution and once at 100 %
            // right before saving. Measured: RUNNING/99.4 -> QUEUED/0.0.
            dao.upsert(DownloadEntity.fromDomain(sampleRecord(id = "m", status = DownloadStatus.QUEUED)))
            dao.updateProgress("m", DownloadStatus.RUNNING.name, 99.4f, 500)

            dao.updateMetadata("m", "Resolved title", "https://img/thumb.jpg", 600)

            val row = dao.getById("m")!!
            assertThat(row.title).isEqualTo("Resolved title")
            assertThat(row.thumbnailUrl).isEqualTo("https://img/thumb.jpg")
            assertThat(row.status).isEqualTo(DownloadStatus.RUNNING.name)
            assertThat(row.progressPercent).isEqualTo(99.4f)
        }

    @Test
    fun `updating the title without a thumbnail keeps the one already stored`() =
        runTest {
            dao.upsert(
                DownloadEntity.fromDomain(sampleRecord(id = "t")).copy(thumbnailUrl = "https://img/old.jpg"),
            )
            dao.updateMetadata("t", "New title", null, 700)
            assertThat(dao.getById("t")!!.thumbnailUrl).isEqualTo("https://img/old.jpg")
        }

    @Test
    fun `updating the phase leaves the title alone`() =
        runTest {
            dao.upsert(DownloadEntity.fromDomain(sampleRecord(id = "s")).copy(title = "Keep me"))
            dao.updateProgress("s", DownloadStatus.PROCESSING.name, 100f, 800)
            val row = dao.getById("s")!!
            assertThat(row.status).isEqualTo(DownloadStatus.PROCESSING.name)
            assertThat(row.title).isEqualTo("Keep me")
        }

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
    fun `getByStatus hands rows back in the order they will run`() =
        runTest {
            // The query promises "in the order the runner will take them". Nothing downstream leans
            // on it — `QueueOrdering` sorts for itself — but a promise nobody checks is how a
            // caller later comes to rely on an order that quietly stopped being true.
            dao.upsert(entityWith(id = "late", queueOrder = 30, created = 1))
            dao.upsert(entityWith(id = "early", queueOrder = 10, created = 3))
            dao.upsert(entityWith(id = "middle", queueOrder = 20, created = 2))

            val ids = dao.getByStatus(listOf(DownloadStatus.QUEUED.name)).map { it.id }

            assertThat(ids).containsExactly("early", "middle", "late").inOrder()
        }

    @Test
    fun `a phase write is refused once the row is paused`() =
        runTest {
            dao.upsert(entityWith(id = "p", queueOrder = 1, created = 1))
            dao.updateStatus("p", DownloadStatus.PAUSED.name, 2)

            val changed = dao.updateProgressUnlessPaused("p", DownloadStatus.RUNNING.name, 42f, 3)

            assertThat(changed).isEqualTo(0)
            assertThat(dao.getById("p")!!.status).isEqualTo("PAUSED")
        }

    @Test
    fun `a phase write goes through while the row is not paused`() =
        runTest {
            dao.upsert(entityWith(id = "r", queueOrder = 1, created = 1))

            val changed = dao.updateProgressUnlessPaused("r", DownloadStatus.RUNNING.name, 42f, 3)

            assertThat(changed).isEqualTo(1)
            assertThat(dao.getById("r")!!.progressPercent).isEqualTo(42f)
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

    /** Creation time deliberately contradicts the queue order, so the two cannot be confused. */
    private fun entityWith(id: String, queueOrder: Long, created: Long) =
        DownloadEntity.fromDomain(
            sampleRecord(id = id, status = DownloadStatus.QUEUED, queueOrder = queueOrder)
                .copy(createdAtEpochMs = created),
        )
}
