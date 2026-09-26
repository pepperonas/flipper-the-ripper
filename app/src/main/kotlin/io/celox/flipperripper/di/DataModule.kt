package io.celox.flipperripper.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.celox.flipperripper.data.local.DownloadDao
import io.celox.flipperripper.data.local.FlipperDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): FlipperDatabase =
        Room.databaseBuilder(context, FlipperDatabase::class.java, FlipperDatabase.NAME)
            // Real migrations, deliberately without a destructive fallback: this database is the
            // user's download history, and the previous `fallbackToDestructiveMigration()` would
            // have thrown all of it away at the first schema change. A missing migration should
            // fail loudly in a test, not delete data on a stranger's phone.
            .apply { FlipperDatabase.MIGRATIONS.forEach { migration -> addMigrations(migration) } }
            .build()

    @Provides
    fun provideDownloadDao(database: FlipperDatabase): DownloadDao = database.downloadDao()

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)

    /** Where the in-app update keeps the downloaded APK (cache: Android may clear it, nothing is lost). */
    @Provides
    @javax.inject.Named(io.celox.flipperripper.data.update.AppUpdateInstaller.UPDATE_DIR)
    fun provideUpdateDir(
        @ApplicationContext context: Context,
    ): java.io.File = java.io.File(context.cacheDir, "app-update")
}
