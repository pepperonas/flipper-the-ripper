package io.celox.flipperripper.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.celox.flipperripper.ui.history.HistoryScreen
import io.celox.flipperripper.ui.home.HomeScreen
import io.celox.flipperripper.ui.login.InstagramLoginScreen
import io.celox.flipperripper.ui.motion.ScreenMotion
import io.celox.flipperripper.ui.motion.ScreenTransitions
import io.celox.flipperripper.ui.motion.rememberReduceMotion
import io.celox.flipperripper.ui.navigation.Destination
import io.celox.flipperripper.ui.settings.SettingsScreen
import io.celox.flipperripper.ui.util.ObserveAsEvents

/** A full-screen destination outside the bottom-nav tabs. */
private const val INSTAGRAM_LOGIN_ROUTE = "instagram_login"

/** Test tag of the bottom bar, so its measured height can be pinned. */
const val BOTTOM_BAR_TAG = "bottom-bar"

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
fun FlipperApp(appNavigator: AppNavigator) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination
    val reduceMotion = rememberReduceMotion()
    // All screen transitions come from the theme's motion scheme — one physics for the whole app.
    val motion = MaterialTheme.motionScheme
    val transitions = remember(motion, reduceMotion) { ScreenTransitions(motion, reduceMotion) }

    // App-level navigation (e.g. a shared link auto-started a download → show History). Collected
    // here because this composable exists for the whole app lifetime, unlike any single screen.
    ObserveAsEvents(appNavigator.events) { target ->
        val route =
            when (target) {
                AppNavTarget.HOME -> Destination.HOME.route
                AppNavTarget.HISTORY -> Destination.HISTORY.route
            }
        navController.navigateToTab(route)
    }

    Scaffold(
        bottomBar = {
            // The M3 Expressive *short* navigation bar: 64 dp instead of the classic 80 dp bar
            // (`NavigationBarTokens.ContainerHeight` vs `TallContainerHeight`), while every item still
            // spans the full bar height — comfortably above the 48 dp touch-target minimum.
            ShortNavigationBar(modifier = Modifier.testTag(BOTTOM_BAR_TAG)) {
                Destination.bottomBar.forEach { destination ->
                    val selected =
                        currentRoute?.hierarchy?.any { it.route == destination.route } == true
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTab(destination.route) },
                        icon = {
                            Icon(
                                if (selected) destination.selectedIcon else destination.icon,
                                contentDescription = null,
                            )
                        },
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
            enterTransition = { transitions.tabEnter(forward = isForward()) },
            exitTransition = { transitions.tabExit(forward = isForward()) },
            popEnterTransition = { transitions.tabEnter(forward = isForward()) },
            popExitTransition = { transitions.tabExit(forward = isForward()) },
        ) {
            composable(Destination.HOME.route) { HomeScreen() }
            composable(Destination.HISTORY.route) { HistoryScreen() }
            composable(
                Destination.SETTINGS.route,
                // Settings is the parent of the sign-in screen: when that child covers or uncovers it,
                // the hierarchical pair applies instead of the lateral tab motion.
                exitTransition = {
                    if (targetState.isHierarchyChild()) {
                        transitions.hierarchyExit()
                    } else {
                        transitions.tabExit(forward = isForward())
                    }
                },
                popEnterTransition = {
                    if (initialState.isHierarchyChild()) {
                        transitions.hierarchyPopEnter()
                    } else {
                        transitions.tabEnter(forward = isForward())
                    }
                },
            ) {
                SettingsScreen(onOpenInstagramLogin = { navController.navigate(INSTAGRAM_LOGIN_ROUTE) })
            }
            composable(
                INSTAGRAM_LOGIN_ROUTE,
                enterTransition = { transitions.hierarchyEnter() },
                popExitTransition = { transitions.hierarchyPopExit() },
            ) {
                InstagramLoginScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}

private fun NavBackStackEntry.isHierarchyChild(): Boolean = destination.route == INSTAGRAM_LOGIN_ROUTE

/** Left-to-right in the bar = forward. */
private fun androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.isForward(): Boolean =
    ScreenMotion.isForward(
        fromIndex = Destination.tabIndexOf(initialState.destination.route),
        toIndex = Destination.tabIndexOf(targetState.destination.route),
    )
