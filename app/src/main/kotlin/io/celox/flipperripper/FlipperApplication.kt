package io.celox.flipperripper

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp
import io.celox.flipperripper.data.share.ShareTargets
import io.celox.flipperripper.data.update.UpdateCoordinator
import io.celox.flipperripper.data.work.DownloadNotifier
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

    @Inject lateinit var notifier: DownloadNotifier

    @Inject lateinit var updateCoordinator: UpdateCoordinator

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        runCatching { notifier.ensureChannels() }
        // Makes the app offerable in the suggested row of the share sheet (see ShareTargets).
        ShareTargets.publish(this)
        // Warm up the engine off the main thread and keep yt-dlp + the app-release notice fresh
        // (see UpdateCoordinator — it is also re-triggered by every shared-in link, because a warm
        // process never runs onCreate again).
        //
        // Best-effort by design: start-up warm-up must never be able to take the process down. It
        // once did — a third-party init failure escaped this coroutine and put the app in an
        // unrecoverable launch-crash loop, with no way to even reach Settings. The UI already
        // surfaces engine problems as typed errors when a download is actually attempted.
        updateCoordinator.runChecksAsync()
    }
}
