package io.celox.flipperripper.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.celox.flipperripper.R
import io.celox.flipperripper.domain.model.AppUpdate
import io.celox.flipperripper.domain.repository.UpdateNotifications
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Flipper the Ripper 1.13.0 is available" — on its own channel, so a user can silence release news
 * without losing download notifications. Tapping it opens the stable download link, which always
 * serves the newest APK; *What's new* opens the release notes.
 */
@Singleton
class UpdateNotifier
@Inject
constructor(@ApplicationContext private val context: Context) : UpdateNotifications {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_app_updates),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun show(update: AppUpdate): Boolean {
        val manager = NotificationManagerCompat.from(context)
        val permitted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!permitted || !manager.areNotificationsEnabled()) return false
        ensureChannel()
        val version = update.version.removePrefix("v")
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(context.getString(R.string.notif_update_title, version))
                .setContentText(context.getString(R.string.notif_update_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_update_text)))
                .setContentIntent(viewIntent(AppUpdateChecker.DOWNLOAD_URL, REQUEST_DOWNLOAD))
                .addAction(0, context.getString(R.string.notif_update_whats_new), viewIntent(update.url, REQUEST_NOTES))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .build()
        return runCatching { manager.notify(NOTIFICATION_ID, notification) }.isSuccess
    }

    private fun viewIntent(url: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 42_001
        private const val REQUEST_DOWNLOAD = 1
        private const val REQUEST_NOTES = 2
    }
}
