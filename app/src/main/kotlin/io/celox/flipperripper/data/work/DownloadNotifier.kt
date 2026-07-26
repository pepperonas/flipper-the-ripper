package io.celox.flipperripper.data.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.celox.flipperripper.R
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the foreground/progress/terminal notifications for downloads. */
@Singleton
class DownloadNotifier
@Inject
constructor(@ApplicationContext private val context: Context) {
    companion object {
        const val PROGRESS_CHANNEL_ID = "downloads_progress"
        const val STATUS_CHANNEL_ID = "downloads_status"
    }

    private val manager = context.getSystemService<NotificationManager>()

    fun ensureChannels() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val progress =
            NotificationChannel(
                PROGRESS_CHANNEL_ID,
                context.getString(R.string.channel_downloads_progress),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        val status =
            NotificationChannel(
                STATUS_CHANNEL_ID,
                context.getString(R.string.channel_downloads_status),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        manager?.createNotificationChannel(progress)
        manager?.createNotificationChannel(status)
    }

    fun buildProgress(title: String, percent: Float?): Notification {
        val builder =
            NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notif_downloading))
                .setContentText(title)
                .setSmallIcon(R.drawable.ic_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (percent == null) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, percent.toInt().coerceIn(0, 100), false)
        }
        return builder.build()
    }

    fun notifyCompleted(id: Int, title: String, openIntent: Intent?) {
        if (!areNotificationsPermitted()) return
        val builder =
            NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notif_completed))
                .setContentText(title)
                .setSmallIcon(R.drawable.ic_done)
                .setAutoCancel(true)
        if (openIntent != null) {
            val pending =
                android.app.PendingIntent.getActivity(
                    context,
                    id,
                    openIntent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                )
            builder.setContentIntent(pending)
        }
        manager?.notify(id, builder.build())
    }

    fun notifyFailed(id: Int, title: String, reason: String) {
        if (!areNotificationsPermitted()) return
        val builder =
            NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notif_failed))
                .setContentText("$title — $reason")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$reason"))
                .setSmallIcon(R.drawable.ic_error)
                .setAutoCancel(true)
        manager?.notify(id, builder.build())
    }

    private fun areNotificationsPermitted(): Boolean =
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
}
