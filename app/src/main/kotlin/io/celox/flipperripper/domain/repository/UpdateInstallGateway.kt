package io.celox.flipperripper.domain.repository

import io.celox.flipperripper.domain.model.InstallableApk
import java.io.File

/** Where the newest installable APK comes from, and how its bytes get here. */
interface InstallableApkSource {
    suspend fun latest(): InstallableApk?

    /**
     * Downloads [apk] into [target], continuing after the bytes already there (a broken download
     * resumes instead of starting over). [onProgress] gets (bytes so far, total). Throws on failure.
     */
    suspend fun download(apk: InstallableApk, target: File, onProgress: (Long, Long) -> Unit)
}

/** Android's package installer, behind an interface so the update flow is testable without a device. */
interface PackageInstallGateway {
    /** Whether this app may install apps (Android 8+: "Install unknown apps" for this app). */
    fun canInstallPackages(): Boolean

    /** Hands [apk] to Android's installer. The outcome arrives later through the installer's callback. */
    fun install(apk: File)
}
