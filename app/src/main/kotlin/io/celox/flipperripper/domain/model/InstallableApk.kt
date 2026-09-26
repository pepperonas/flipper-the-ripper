package io.celox.flipperripper.domain.model

/**
 * The release APK the app can install on itself: where to fetch it and what it must hash to.
 * [version] keeps the raw tag ("v1.13.0").
 */
data class InstallableApk(val version: String, val name: String, val url: String, val size: Long, val sha256: String)

/** Where the in-app update is. [Idle] also covers "finished": a successful install replaces the process. */
sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState

    /** [fraction] is null until the size is known. */
    data class Downloading(val fraction: Float?) : UpdateInstallState

    data object Verifying : UpdateInstallState

    /** Android has not yet allowed this app to install apps; the user has to switch it on once. */
    data object NeedsPermission : UpdateInstallState

    /** Handed to Android's installer; its confirmation screen is up or the install is running. */
    data object Installing : UpdateInstallState

    data class Failed(val reason: UpdateFailure) : UpdateInstallState
}

enum class UpdateFailure {
    /** No release, or the release has no APK this app can verify. */
    NO_RELEASE,

    /** The download broke off (the next attempt resumes from the last byte). */
    NETWORK,

    /** The file does not hash to the published SHA-256 — deleted, never installed. */
    CHECKSUM,

    /** Android's installer refused it (e.g. storage full, a foreign signature). */
    INSTALL,
}
