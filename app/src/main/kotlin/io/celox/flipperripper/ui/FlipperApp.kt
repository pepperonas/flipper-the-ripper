package io.celox.flipperripper.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.celox.flipperripper.ui.history.HistoryScreen
import io.celox.flipperripper.ui.home.HomeScreen
import io.celox.flipperripper.ui.navigation.Destination
import io.celox.flipperripper.ui.settings.SettingsScreen

private const val DURATION = 400

@Composable
fun FlipperApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.bottomBar.forEach { destination ->
                    val selected =
                        currentRoute?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
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
            modifier = Modifier.padding(padding),
            // Expressive cross-fade + gentle scale between destinations.
            enterTransition = { fadeIn(tween(DURATION)) + scaleIn(tween(DURATION), initialScale = 0.94f) },
            exitTransition = { fadeOut(tween(DURATION / 2)) },
            popEnterTransition = { fadeIn(tween(DURATION)) + scaleIn(tween(DURATION), initialScale = 0.94f) },
            popExitTransition = { fadeOut(tween(DURATION / 2)) + scaleOut(tween(DURATION), targetScale = 0.96f) },
        ) {
            composable(Destination.HOME.route) {
                HomeScreen(onDownloadStarted = { navController.navigate(Destination.HISTORY.route) })
            }
            composable(Destination.HISTORY.route) { HistoryScreen() }
            composable(Destination.SETTINGS.route) { SettingsScreen() }
        }
    }
}
