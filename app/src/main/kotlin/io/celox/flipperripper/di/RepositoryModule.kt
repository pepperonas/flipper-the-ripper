package io.celox.flipperripper.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.celox.flipperripper.data.engine.YoutubeDlEngine
import io.celox.flipperripper.data.engine.YtDlpEngine
import io.celox.flipperripper.data.media.MediaStoreWriter
import io.celox.flipperripper.data.media.MediaStoreWriterImpl
import io.celox.flipperripper.data.repository.ClipboardRepositoryImpl
import io.celox.flipperripper.data.repository.DownloadRepositoryImpl
import io.celox.flipperripper.data.repository.EngineRepositoryImpl
import io.celox.flipperripper.data.repository.SettingsRepositoryImpl
import io.celox.flipperripper.data.repository.VideoRepositoryImpl
import io.celox.flipperripper.domain.repository.ClipboardRepository
import io.celox.flipperripper.domain.repository.DownloadRepository
import io.celox.flipperripper.domain.repository.EngineRepository
import io.celox.flipperripper.domain.repository.SettingsRepository
import io.celox.flipperripper.domain.repository.VideoRepository
import io.celox.flipperripper.util.IdGenerator
import io.celox.flipperripper.util.UuidGenerator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindEngine(impl: YoutubeDlEngine): YtDlpEngine

    @Binds
    abstract fun bindMediaStoreWriter(impl: MediaStoreWriterImpl): MediaStoreWriter

    @Binds
    abstract fun bindIdGenerator(impl: UuidGenerator): IdGenerator

    @Binds
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository

    @Binds
    abstract fun bindEngineRepository(impl: EngineRepositoryImpl): EngineRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindClipboardRepository(impl: ClipboardRepositoryImpl): ClipboardRepository
}
