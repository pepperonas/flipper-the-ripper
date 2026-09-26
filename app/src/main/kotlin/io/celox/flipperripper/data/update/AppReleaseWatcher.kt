package io.celox.flipperripper.data.update

import io.celox.flipperripper.domain.model.AppUpdate
import io.celox.flipperripper.domain.repository.AppReleaseSource
import io.celox.flipperripper.domain.repository.SettingsRepository
import io.celox.flipperripper.domain.repository.UpdateNotifications
import io.celox.flipperripper.domain.util.AppVersions
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One release check, shared by the app-start check and the periodic background worker: fetch the
 * newest release, remember it (the Home notice reads it), and post a notification for it — once per
 * release, only when newer than the installed build and only while the user has not switched
 * notifications off. A notification that could not be shown (permission denied) is not recorded, so a
 * later check can still show it once the permission is granted.
 */
@Singleton
class AppReleaseWatcher
@Inject
constructor(
    private val source: AppReleaseSource,
    private val settings: SettingsRepository,
    private val notifications: UpdateNotifications,
) {
    suspend fun check(installedVersion: String): AppUpdate? {
        val release = source.fetchLatestRelease() ?: return null
        settings.setKnownAppUpdate(release)
        val enabled = settings.preferences.first().updateNotifications
        val notified = settings.notifiedUpdateVersion.first()
        if (AppVersions.shouldNotify(installedVersion, release, notified, enabled) && notifications.show(release)) {
            settings.setNotifiedUpdateVersion(release.version)
        }
        return release
    }
}
