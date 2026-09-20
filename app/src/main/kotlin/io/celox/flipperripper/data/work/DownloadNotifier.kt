package io.celox.flipperripper.data.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.celox.flipperripper.R
import io.celox.flipperripper.domain.model.DownloadStatus
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

    /**
     * The ongoing notification, built from the same record phase the History card reads — the two
     * can no longer disagree about what is happening or how far along it is.
     *
     * A percentage is shown only while bytes are actually moving. Preparing and post-processing have
     * no measurable total, so they stay indeterminate rather than parking a bar at 0 % or 100 %.
     */
    fun buildProgress(
        phase: DownloadStatus,
        title: String,
        percent: Float?,
        heading: String = context.getString(phaseTitleRes(phase)),
    ): Notification {
        val builder =
            NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
                .setContentTitle(heading)
                .setContentText(title)
                .setSmallIcon(R.drawable.ic_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        openAppIntent()?.let(builder::setContentIntent)

        val measurable = phase == DownloadStatus.RUNNING && percent != null
        if (measurable) {
            val whole = percent!!.toInt().coerceIn(0, 100)
            builder.setProgress(100, whole, false)
            builder.setSubText(context.getString(R.string.notif_percent, whole))
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    /**
     * The queue's ongoing notification: the same thing [buildProgress] shows, plus where in the
     * batch we are.
     *
     * The position is only added when there is actually more than one download, because "1 of 1" is
     * noise dressed up as information.
     */
    fun buildQueueProgress(
        phase: DownloadStatus,
        title: String,
        percent: Float?,
        position: Int,
        total: Int,
    ): Notification {
        val heading =
            if (total > 1) {
                context.getString(R.string.notif_queue_position, context.getString(phaseTitleRes(phase)), position, total)
            } else {
                context.getString(phaseTitleRes(phase))
            }
        return buildProgress(phase, title, percent, heading)
    }

    fun notifyCompleted(id: Int, title: String, openIntent: Intent?) {
        if (!areNotificationsPermitted()) return
        val builder =
            NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notif_completed))
                .setContentText(title)
                .setSmallIcon(R.drawable.ic_done)
                .setAutoCancel(true)
        val target = openIntent?.let { pendingActivity(id, it) } ?: openAppIntent()
        target?.let(builder::setContentIntent)
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
        openAppIntent()?.let(builder::setContentIntent)
        manager?.notify(id, builder.build())
    }

    private fun phaseTitleRes(phase: DownloadStatus): Int =
        when (phase) {
            DownloadStatus.QUEUED, DownloadStatus.PREPARING -> R.string.notif_preparing
            DownloadStatus.PROCESSING -> R.string.notif_processing
            else -> R.string.notif_downloading
        }

    /**
     * Brings the app forward when the notification is tapped. Resolved through the package manager
     * rather than naming the Activity class, so the data layer keeps no reference to the UI; the
     * activity is `singleTask`, so this returns to the running task instead of starting a second one.
     */
    private fun openAppIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return pendingActivity(0, launch)
    }

    private fun pendingActivity(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun areNotificationsPermitted(): Boolean =
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
}
