package io.celox.flipperripper.data.update

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.celox.flipperripper.BuildConfig
import io.celox.flipperripper.R
import io.celox.flipperripper.ui.MainActivity

/**
 * Android ends the running app when it replaces it, so after an in-app update the app simply
 * vanishes. This receiver runs in the NEW version and says so: "Updated to 1.14.0 — tap to open".
 * Only after a self-update (see [SelfUpdateMarker]); a manual install shows Android's own "Open".
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED || !SelfUpdateMarker.consume(context)) return
        val permitted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!permitted) return
        UpdateNotifier(context.applicationContext).ensureChannel()
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat.Builder(context, UpdateNotifier.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(context.getString(R.string.notif_updated_title, BuildConfig.VERSION_NAME))
                .setContentText(context.getString(R.string.notif_updated_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        runCatching { NotificationManagerCompat.from(context).notify(UpdateNotifier.NOTIFICATION_ID, notification) }
    }
}

/** A one-shot flag: "the next package replacement is our own update". Plain prefs: read in a receiver. */
object SelfUpdateMarker {
    private const val PREFS = "self_update"
    private const val KEY = "pending"

    fun set(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }

    fun consume(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getBoolean(KEY, false)
        if (pending) prefs.edit().remove(KEY).apply()
        return pending
    }
}
