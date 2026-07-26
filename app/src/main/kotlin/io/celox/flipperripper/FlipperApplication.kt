package io.celox.flipperripper

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.celox.flipperripper.data.engine.YtDlpEngine
import io.celox.flipperripper.data.work.DownloadNotifier
import io.celox.flipperripper.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FlipperApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var engine: YtDlpEngine

    @Inject lateinit var notifier: DownloadNotifier

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
        notifier.ensureChannels()
        // Warm up the engine off the main thread so the first download is instant.
        appScope.launch { engine.ensureInitialized() }
    }
}
