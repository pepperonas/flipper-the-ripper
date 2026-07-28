package io.celox.flipperripper

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp
import io.celox.flipperripper.data.engine.YtDlpEngine
import io.celox.flipperripper.data.work.DownloadNotifier
import io.celox.flipperripper.di.ApplicationScope
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FlipperApplication :
    Application(),
    Configuration.Provider,
    ImageLoaderFactory {
    /**
     * Adds video-frame decoding, so a saved download can show a still from the file itself. Several
     * platforms return no thumbnail URL at all (Instagram in particular), which left every card with a
     * bare placeholder even though the video was sitting on the device.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var engine: YtDlpEngine

    @Inject lateinit var notifier: DownloadNotifier

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        runCatching { notifier.ensureChannels() }
        // Warm up the engine off the main thread, then keep the yt-dlp extractor fresh: the bundled
        // yt-dlp is frozen at the library's release, so sites like YouTube/Instagram need a periodic
        // update to keep extracting. Throttled to at most once per interval to avoid needless traffic.
        //
        // Everything here is best-effort and wrapped: start-up warm-up must never be able to take the
        // process down. It once did — a third-party init failure escaped this coroutine and put the app
        // in an unrecoverable launch-crash loop, with no way to even reach Settings. The UI already
        // surfaces engine problems as typed errors when a download is actually attempted.
        appScope.launch {
            runCatching {
                if (engine.ensureInitialized() is EngineResult.Success) {
                    maybeUpdateEngine()
                }
            }
        }
    }

    private suspend fun maybeUpdateEngine() {
        val last = settingsRepository.lastEngineUpdateMs.first()
        val now = System.currentTimeMillis()
        if (now - last < ENGINE_UPDATE_INTERVAL_MS) return
        if (engine.update() is EngineResult.Success) {
            settingsRepository.setLastEngineUpdateMs(now)
        }
    }

    private companion object {
        const val ENGINE_UPDATE_INTERVAL_MS = 12L * 60 * 60 * 1000 // 12 hours
    }
}
