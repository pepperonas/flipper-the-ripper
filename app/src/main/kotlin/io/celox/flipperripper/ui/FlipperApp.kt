package io.celox.flipperripper.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.celox.flipperripper.ui.history.HistoryScreen
import io.celox.flipperripper.ui.home.HomeScreen
import io.celox.flipperripper.ui.motion.rememberReduceMotion
import io.celox.flipperripper.ui.navigation.Destination
import io.celox.flipperripper.ui.settings.SettingsScreen

// Material 3 "fade-through" for lateral (tab) navigation: the outgoing screen leaves first, then the
// incoming one arrives. The previous timings overlapped (400 ms in vs 200 ms out) and only scaled on
// the way back, so every tab switch showed both screens at once and appeared to zoom.
private const val FADE_OUT_MILLIS = 90
private const val FADE_IN_MILLIS = 210

/**
 * Navigate to a top-level destination.
 *
 * Every route into a tab must go through here. The bottom bar and the automatic jump after a download
 * starts previously used *different* navigation options — the latter passed none at all — so the same
 * destination could be pushed twice onto the back stack and bypassed the save/restore of tab state,
 * letting a stale saved state later be restored over the live one.
 */
private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        // Keep a single tab on the back stack and remember each tab's own state.
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun FlipperApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination
    val reduceMotion = rememberReduceMotion()

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.bottomBar.forEach { destination ->
                    val selected =
                        currentRoute?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTab(destination.route) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.HOME.route,
            // Each screen has its own Scaffold with a top app bar. This outer Scaffold already reserves
            // the status-bar and navigation-bar insets (as `padding`), so the inner Scaffolds must be
            // told those insets are handled — otherwise they add the status bar again at the top (a fat
            // empty strip above every title) and the nav-bar inset again at the bottom (a dead strip
            // above the menu bar that also clipped the last of the content). Consuming `padding` here is
            // what stops the double counting.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { if (reduceMotion) EnterTransition.None else fadeThroughEnter() },
            exitTransition = { if (reduceMotion) ExitTransition.None else fadeThroughExit() },
            popEnterTransition = { if (reduceMotion) EnterTransition.None else fadeThroughEnter() },
            popExitTransition = { if (reduceMotion) ExitTransition.None else fadeThroughExit() },
        ) {
            composable(Destination.HOME.route) {
                HomeScreen(
                    onDownloadStarted = { navController.navigateToTab(Destination.HISTORY.route) },
                )
            }
            composable(Destination.HISTORY.route) { HistoryScreen() }
            composable(Destination.SETTINGS.route) { SettingsScreen() }
        }
    }
}

/** Incoming screen: waits for the outgoing one to clear, then fades in with a slight settle. */
private fun fadeThroughEnter(): EnterTransition =
    fadeIn(tween(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS)) +
        scaleIn(
            tween(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS),
            initialScale = 0.92f,
        )

/** Outgoing screen: leaves quickly and completely, so the two never overlap. */
private fun fadeThroughExit(): ExitTransition = fadeOut(tween(FADE_OUT_MILLIS))
