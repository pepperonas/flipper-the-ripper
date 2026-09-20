package io.celox.flipperripper.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DownloadEntity::class], version = 2, exportSchema = false)
abstract class FlipperDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val NAME = "flipper.db"

        /**
         * Adds the queue position.
         *
         * Existing rows are seeded from `createdAtEpochMs`, which preserves exactly the order the
         * History list already showed. The column is NOT NULL with a default so that a row written
         * by an older code path — there is none, but the schema outlives the assumption — cannot
         * land without a position and sort ahead of everything.
         *
         * There is a real migration here at all because until 1.10.0 the database was built with
         * `fallbackToDestructiveMigration()`: the *first* schema change would have silently wiped
         * every user's download history.
         */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE downloads ADD COLUMN queueOrder INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE downloads SET queueOrder = createdAtEpochMs")
                }
            }

        val MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}
