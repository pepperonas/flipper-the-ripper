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
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDownloadDao(database: FlipperDatabase): DownloadDao = database.downloadDao()

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)
}
