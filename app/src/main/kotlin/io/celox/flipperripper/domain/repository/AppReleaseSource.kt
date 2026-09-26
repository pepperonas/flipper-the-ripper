package io.celox.flipperripper.domain.repository

import io.celox.flipperripper.domain.model.AppUpdate

/** Where the newest published app release comes from. Null = unknown right now (offline, error). */
interface AppReleaseSource {
    suspend fun fetchLatestRelease(): AppUpdate?
}

/** Posts the "new version available" notification. Returns false when it could not be shown. */
interface UpdateNotifications {
    fun show(update: AppUpdate): Boolean
}
