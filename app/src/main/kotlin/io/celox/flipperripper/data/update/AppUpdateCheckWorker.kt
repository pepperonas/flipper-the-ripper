package io.celox.flipperripper.data.update

import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.celox.flipperripper.BuildConfig
import io.celox.flipperripper.domain.util.UpdatePolicy
import java.util.concurrent.TimeUnit

/**
 * Checks for a new app release in the background — twice a day, only with a network — so a user who
 * does not open the app still hears about an update. The check itself is [AppReleaseWatcher].
 */
@HiltWorker
class AppUpdateCheckWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val watcher: AppReleaseWatcher,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // Best-effort: a failed check waits for the next period instead of retrying in a loop.
        runCatching { watcher.check(BuildConfig.VERSION_NAME) }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "app_update_check"
        private const val INTERVAL_HOURS = 12L

        /**
         * Keeps one periodic check scheduled. KEEP: calling it on every start must not reset the
         * period. A device that cannot install the releases (32-bit only) is never scheduled.
         */
        fun schedule(context: Context) {
            if (!UpdatePolicy.releasesInstallOn(Build.SUPPORTED_ABIS.toList())) return
            val request =
                PeriodicWorkRequestBuilder<AppUpdateCheckWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
