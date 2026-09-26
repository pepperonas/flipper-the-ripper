package io.celox.flipperripper.data.update

import android.os.Build
import io.celox.flipperripper.BuildConfig
import io.celox.flipperripper.data.engine.YtDlpEngine
import io.celox.flipperripper.di.ApplicationScope
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.repository.SettingsRepository
import io.celox.flipperripper.domain.util.UpdatePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the two update surfaces fresh, best-effort and throttled:
 *
 *  1. **yt-dlp engine** — the bundled extractor is frozen at the library's release date, and
 *     YouTube in particular breaks old extractors within months (the shipped 2024-09 yt-dlp ended
 *     every YouTube download in "Sign in to confirm you're not a bot"). The runtime update pulls
 *     the current yt-dlp release so extraction keeps working between app releases.
 *  2. **the app itself** — the newest release is fetched and remembered; the Home screen shows an
 *     update notice and a notification is posted when it is newer than the installed build. The
 *     same check also runs twice a day in the background ([AppUpdateCheckWorker]).
 *
 * Called from app start AND from every shared-in link ([io.celox.flipperripper.ui.MainActivity]) —
 * a warm process never re-runs `Application.onCreate`, so start-only checks went stale exactly for
 * the people who keep the app in the background and share links into it.
 *
 * Everything is wrapped: an update check must never take the process down or block a download.
 */
@Singleton
class UpdateCoordinator
@Inject
constructor(
    private val engine: YtDlpEngine,
    private val releaseWatcher: AppReleaseWatcher,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val mutex = Mutex()

    /** Fire-and-forget; safe to call from the main thread. */
    fun runChecksAsync() {
        appScope.launch {
            runCatching { runChecks() }
        }
    }

    private suspend fun runChecks() {
        mutex.withLock {
            if (engine.ensureInitialized() is EngineResult.Success) {
                maybeUpdateEngine()
            }
            maybeCheckAppRelease()
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

    private suspend fun maybeCheckAppRelease() {
        // A device that cannot install the releases (32-bit only, since 1.9.0 ships 64-bit ARM
        // alone) is not told about them: the notice would be permanent and never actionable.
        if (!UpdatePolicy.releasesInstallOn(Build.SUPPORTED_ABIS.toList())) return
        val last = settingsRepository.lastAppUpdateCheckMs.first()
        val now = System.currentTimeMillis()
        if (now - last < APP_CHECK_INTERVAL_MS) return
        // Also posts the release notification, once per release (see AppReleaseWatcher).
        releaseWatcher.check(BuildConfig.VERSION_NAME) ?: return
        settingsRepository.setLastAppUpdateCheckMs(now)
    }

    private companion object {
        const val ENGINE_UPDATE_INTERVAL_MS = 12L * 60 * 60 * 1000 // 12 hours
        const val APP_CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000 // 12 hours
    }
}
