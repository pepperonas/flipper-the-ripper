package io.celox.flipperripper.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import io.celox.flipperripper.R

enum class Destination(val route: String, val labelRes: Int, val icon: ImageVector) {
    HOME("home", R.string.nav_home, Icons.Outlined.Download),
    HISTORY("history", R.string.nav_history, Icons.Outlined.History),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings),
    ;

    companion object {
        val bottomBar = listOf(HOME, HISTORY, SETTINGS)
    }
}
