package io.celox.flipperripper.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.QualityChoice
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The download history has to survive a schema change.
 *
 * Until 1.10.0 the database was built with `fallbackToDestructiveMigration()`, which means the very
 * first schema change would have deleted every user's history without a word. This test exists to
 * make that failure mode impossible to reintroduce quietly: it writes a real version-1 file, opens
 * it with the current Room definition, and checks the rows are still there afterwards.
 *
 * Room does the hard part of the checking itself — after running the migration it validates the
 * actual schema against the entity and throws if they differ, so a migration that forgets a column
 * fails here rather than on a stranger's phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class FlipperDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"
    private var db: FlipperDatabase? = null

    @Before
    fun setUp() {
        context.getDatabasePath(name).also { it.parentFile?.mkdirs() }.delete()
    }

    @After
    fun tearDown() {
        db?.close()
        context.getDatabasePath(name).delete()
    }

    @Test
    fun `a version 1 history survives the upgrade and keeps its order`() =
        runTest {
            writeVersion1(
                Row(id = "old-1", title = "First", created = 1_000),
                Row(id = "old-2", title = "Second", created = 2_000),
            )

            val dao = openCurrent().downloadDao()
            val rows = dao.getByStatus(listOf("COMPLETED"))

            assertThat(rows.map { it.id }).containsExactly("old-1", "old-2")
            assertThat(rows.map { it.title }).containsExactly("First", "Second")
            // Seeded from the creation time, so the list keeps exactly the order it already showed.
            assertThat(rows.map { it.queueOrder }).containsExactly(1_000L, 2_000L).inOrder()
        }

    @Test
    fun `the migrated schema is the one the current entity expects`() =
        runTest {
            writeVersion1(Row(id = "old-1", title = "First", created = 1_000))
            // Writing through the current entity only works if the migrated table really has every
            // column Room generated code addresses — the check that catches a forgotten ALTER.
            val dao = openCurrent().downloadDao()
            dao.updateQueueOrder("old-1", 42, 5_000)
            assertThat(dao.getById("old-1")?.queueOrder).isEqualTo(42L)
        }

    @Test
    fun `an empty version 1 database upgrades without complaint`() =
        runTest {
            writeVersion1()
            assertThat(openCurrent().downloadDao().getByStatus(listOf("COMPLETED"))).isEmpty()
        }

    @Test
    fun `an audio download stays audio when the quality column arrives`() =
        runTest {
            // Defaulting every pre-1.10 row to BEST would have turned a saved audio download into a
            // video one the moment somebody retried it.
            writeVersion1(
                Row(id = "song", title = "Song", created = 1_000, mode = "AUDIO"),
                Row(id = "clip", title = "Clip", created = 2_000, mode = "VIDEO"),
            )

            val dao = openCurrent().downloadDao()

            assertThat(dao.getById("song")?.toDomain()?.quality).isEqualTo(QualityChoice.AUDIO_ONLY)
            assertThat(dao.getById("clip")?.toDomain()?.quality).isEqualTo(QualityChoice.BEST)
        }

    @Test
    fun `a version 2 database upgrades on its own`() =
        runTest {
            // The chain 1 to 3 is what an old install takes; this is what an install from the queue
            // release takes, and it has to work without passing through the first migration.
            writeVersion2(Row(id = "kept", title = "Kept", created = 5_000, mode = "AUDIO"))

            val row = openCurrent().downloadDao().getById("kept")

            assertThat(row?.title).isEqualTo("Kept")
            assertThat(row?.toDomain()?.quality).isEqualTo(QualityChoice.AUDIO_ONLY)
        }

    private fun openCurrent(): FlipperDatabase =
        Room.databaseBuilder(context, FlipperDatabase::class.java, name)
            .addMigrations(*FlipperDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            .also { db = it }

    private data class Row(val id: String, val title: String, val created: Long, val mode: String = "VIDEO")

    /** Version 2: the queue column exists, the quality column does not. */
    private fun writeVersion2(vararg rows: Row) {
        writeVersion1(*rows)
        val raw = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        raw.execSQL("ALTER TABLE downloads ADD COLUMN queueOrder INTEGER NOT NULL DEFAULT 0")
        raw.execSQL("UPDATE downloads SET queueOrder = createdAtEpochMs")
        raw.version = 2
        raw.close()
    }

    /** The table exactly as version 1 shipped it — no `queueOrder`. */
    private fun writeVersion1(vararg rows: Row) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        raw.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `downloads` (
                `id` TEXT NOT NULL, `sourceUrl` TEXT NOT NULL, `platform` TEXT NOT NULL,
                `title` TEXT NOT NULL, `mode` TEXT NOT NULL, `thumbnailUrl` TEXT,
                `status` TEXT NOT NULL, `mediaUri` TEXT, `fileName` TEXT, `sizeBytes` INTEGER,
                `progressPercent` REAL, `errorKind` TEXT, `errorMessage` TEXT,
                `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        rows.forEach {
            raw.execSQL(
                "INSERT INTO downloads VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf(
                    it.id, "https://youtu.be/${it.id}", "YOUTUBE", it.title, it.mode, null,
                    "COMPLETED", null, null, null, null, null, null, it.created, it.created,
                ),
            )
        }
        raw.version = 1
        raw.close()
    }
}
