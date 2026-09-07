package io.celox.flipperripper.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import io.celox.flipperripper.R

/**
 * A top-level destination. [icon] is the resting (outlined) glyph, [selectedIcon] the filled one the
 * bar shows for the active tab — the Material navigation-bar convention.
 */
enum class Destination(val route: String, val labelRes: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("home", R.string.nav_home, Icons.Outlined.Download, Icons.Filled.Download),
    HISTORY("history", R.string.nav_history, Icons.Outlined.History, Icons.Filled.History),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
    ;

    companion object {
        val bottomBar = listOf(HOME, HISTORY, SETTINGS)

        /** Position of [route] in the bar, or -1 for a route outside it (drives the slide direction). */
        fun tabIndexOf(route: String?): Int = bottomBar.indexOfFirst { it.route == route }
    }
}
