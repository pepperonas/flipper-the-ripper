package io.celox.flipperripper.data.share

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.celox.flipperripper.R

/**
 * Publishes the shortcut that lets the app be offered as a *direct* share target — in the row of
 * suggested targets at the top of the share sheet, rather than only in the long alphabetical app
 * list further down, where someone scanning for it easily concludes it is not installed.
 *
 * A `<share-target>` alone does nothing: the system pairs it with a published shortcut carrying the
 * same category, so the shortcut has to exist before the sheet is opened.
 */
object ShareTargets {
    /**
     * Best-effort by design. Shortcut publishing can be rate-limited by the launcher and is a
     * convenience, never a requirement — sharing keeps working through the app list regardless, so a
     * failure here must not be able to affect start-up.
     */
    fun publish(context: Context) {
        runCatching {
            val open =
                Intent(Intent.ACTION_MAIN)
                    .setClassName(context, MAIN_ACTIVITY)

            val shortcut =
                ShortcutInfoCompat.Builder(context, ShareShortcut.ID)
                    .setShortLabel(context.getString(R.string.share_target_short))
                    .setLongLabel(context.getString(R.string.share_target_long))
                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                    .setCategories(setOf(ShareShortcut.CATEGORY))
                    // Keeps the shortcut usable after it leaves the dynamic list, which is what lets
                    // the system go on ranking it as a share target.
                    .setLongLived(true)
                    .setIntent(open)
                    .build()

            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
    }

    private const val MAIN_ACTIVITY = "io.celox.flipperripper.ui.MainActivity"
}
