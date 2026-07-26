package io.celox.flipperripper.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
abstract class FlipperDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val NAME = "flipper.db"
    }
}
