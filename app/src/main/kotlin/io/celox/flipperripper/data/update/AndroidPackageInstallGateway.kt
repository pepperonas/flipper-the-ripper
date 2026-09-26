package io.celox.flipperripper.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.celox.flipperripper.domain.repository.PackageInstallGateway
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** [PackageInstallGateway] on Android's [PackageInstaller]: one session per update, result by broadcast. */
@Singleton
class AndroidPackageInstallGateway
@Inject
constructor(@ApplicationContext private val context: Context) : PackageInstallGateway {
    override fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    override fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Once this app installed itself, later updates may go through without a prompt.
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val callback =
                PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName),
                    // Mutable: the installer fills in the status extras.
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
                )
            // Lets PackageReplacedReceiver tell a self-update apart from a manual install afterwards.
            SelfUpdateMarker.set(context)
            session.commit(callback.intentSender)
        }
    }
}

/**
 * Receives Android's installer status. [PackageInstaller.STATUS_PENDING_USER_ACTION] carries the
 * confirmation screen, which has to be started by the app; everything else is the final outcome.
 */
@AndroidEntryPoint
class InstallResultReceiver : BroadcastReceiver() {
    @Inject lateinit var installer: AppUpdateInstaller

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
            if (confirm == null) {
                installer.onInstallResult(InstallOutcome.FAILED)
                return
            }
            runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { installer.onInstallResult(InstallOutcome.FAILED) }
            return
        }
        installer.onInstallResult(outcomeOf(status))
    }

    companion object {
        fun outcomeOf(status: Int): InstallOutcome =
            when (status) {
                PackageInstaller.STATUS_SUCCESS -> InstallOutcome.SUCCESS
                PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.CANCELLED
                else -> InstallOutcome.FAILED
            }
    }
}
