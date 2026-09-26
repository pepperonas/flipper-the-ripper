package io.celox.flipperripper.data.update

import io.celox.flipperripper.di.ApplicationScope
import io.celox.flipperripper.di.IoDispatcher
import io.celox.flipperripper.domain.model.InstallableApk
import io.celox.flipperripper.domain.model.UpdateFailure
import io.celox.flipperripper.domain.model.UpdateInstallState
import io.celox.flipperripper.domain.repository.InstallableApkSource
import io.celox.flipperripper.domain.repository.PackageInstallGateway
import io.celox.flipperripper.domain.util.AppVersions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Updates the app from inside the app: fetch the newest release APK, verify it against the
 * published SHA-256, hand it to Android's package installer.
 *
 * Why not just open the download link: browsers hold an APK back until the user confirms a
 * "this file might be harmful" question, and in a browser opened from another app that question can
 * stay hidden — the download then hangs at 100 % (seen on a Galaxy S24). Here nothing is left to a
 * browser. Android still shows its own install confirmation, and the new APK must carry the same
 * signature as the installed app, or Android refuses it.
 *
 * The file lives in the app's cache; a broken download resumes from the last byte on the next try,
 * and a file that does not verify is deleted, never installed.
 */
@Singleton
class AppUpdateInstaller
@Inject
constructor(
    private val source: InstallableApkSource,
    private val gateway: PackageInstallGateway,
    @Named(UPDATE_DIR) private val dir: File,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    val state: StateFlow<UpdateInstallState> = _state.asStateFlow()

    /** Verified and waiting for the install permission. */
    private var ready: File? = null

    /** Starts the update unless one is already running. [installedVersion] is this build's versionName. */
    fun start(installedVersion: String) {
        if (busy()) return
        _state.value = UpdateInstallState.Downloading(null)
        scope.launch { run(installedVersion) }
    }

    /** After the user came back from "Install unknown apps": install if it is allowed now. */
    fun onPermissionMaybeGranted() {
        val file = ready ?: return
        if (_state.value != UpdateInstallState.NeedsPermission || !gateway.canInstallPackages()) return
        install(file)
    }

    /** Android's installer reports back (see [InstallResultReceiver]). */
    fun onInstallResult(outcome: InstallOutcome) {
        _state.value =
            when (outcome) {
                // On success the process is replaced and never sees this; a cancel is not an error.
                InstallOutcome.SUCCESS, InstallOutcome.CANCELLED -> UpdateInstallState.Idle
                InstallOutcome.FAILED -> UpdateInstallState.Failed(UpdateFailure.INSTALL)
            }
    }

    fun dismissFailure() {
        if (_state.value is UpdateInstallState.Failed) _state.value = UpdateInstallState.Idle
    }

    private fun busy(): Boolean =
        when (_state.value) {
            is UpdateInstallState.Downloading, UpdateInstallState.Verifying, UpdateInstallState.Installing -> true
            else -> false
        }

    private suspend fun run(installedVersion: String) {
        try {
            val apk = source.latest()
            if (apk == null || !AppVersions.isNewer(installedVersion, apk.version)) {
                return fail(UpdateFailure.NO_RELEASE)
            }
            val file = withContext(ioDispatcher) { prepare(apk) }
            source.download(apk, file) { have, total ->
                _state.value = UpdateInstallState.Downloading(if (total > 0) have.toFloat() / total else null)
            }
            _state.value = UpdateInstallState.Verifying
            val sha = withContext(ioDispatcher) { sha256(file) }
            if (sha != apk.sha256) {
                withContext(ioDispatcher) { file.delete() }
                return fail(UpdateFailure.CHECKSUM)
            }
            ready = file
            if (gateway.canInstallPackages()) install(file) else _state.value = UpdateInstallState.NeedsPermission
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            // Network, storage: the partial file stays, the next attempt resumes from it.
            fail(UpdateFailure.NETWORK)
        }
    }

    private fun install(file: File) {
        _state.value = UpdateInstallState.Installing
        runCatching { gateway.install(file) }.onFailure { fail(UpdateFailure.INSTALL) }
    }

    /** The target file for [apk]; files of other versions are removed so the cache holds one APK. */
    private fun prepare(apk: InstallableApk): File {
        dir.mkdirs()
        dir.listFiles()?.filter { it.name != apk.name }?.forEach { it.delete() }
        return File(dir, apk.name)
    }

    private fun fail(reason: UpdateFailure) {
        _state.value = UpdateInstallState.Failed(reason)
    }

    companion object {
        const val UPDATE_DIR = "appUpdateDir"

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

/** What Android's installer reported, reduced to what the app acts on. */
enum class InstallOutcome { SUCCESS, CANCELLED, FAILED }
