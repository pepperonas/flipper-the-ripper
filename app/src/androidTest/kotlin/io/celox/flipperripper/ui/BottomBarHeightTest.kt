package io.celox.flipperripper.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.ui.navigation.Destination
import io.celox.flipperripper.ui.theme.FlipperTheme
import org.junit.Rule
import org.junit.Test

/**
 * The bottom bar's height is a product decision (compact, but never below the 48 dp touch minimum),
 * so it is measured, not assumed — the classic bar the app shipped until 1.4.0 against the short bar
 * it ships now. System-bar insets are zeroed so the numbers are the bars' own.
 */
class BottomBarHeightTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theClassicNavigationBarIs80dp() {
        composeRule.setContent {
            FlipperTheme {
                Box(Modifier.fillMaxSize()) {
                    NavigationBar(modifier = Modifier.testTag("classic"), windowInsets = WindowInsets(0.dp)) {
                        Destination.bottomBar.forEachIndexed { index, destination ->
                            NavigationBarItem(
                                selected = index == 0,
                                onClick = {},
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(destination.route) },
                            )
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("classic").assertHeightIsEqualTo(80.dp)
    }

    @Test
    fun theShortNavigationBarIs64dpWithFullHeightItems() {
        composeRule.setContent {
            FlipperTheme {
                Box(Modifier.fillMaxSize()) {
                    ShortNavigationBar(modifier = Modifier.testTag(BOTTOM_BAR_TAG), windowInsets = WindowInsets(0.dp)) {
                        Destination.bottomBar.forEachIndexed { index, destination ->
                            ShortNavigationBarItem(
                                selected = index == 0,
                                onClick = {},
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(destination.route) },
                            )
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag(BOTTOM_BAR_TAG).assertHeightIsEqualTo(64.dp)
        // Every item spans the full bar — comfortably above the 48 dp touch-target minimum.
        Destination.bottomBar.forEach { destination ->
            val item = composeRule.onNodeWithText(destination.route).onParent()
            item.assertHeightIsAtLeast(48.dp)
            assertThat(item.getUnclippedBoundsInRoot().height).isEqualTo(64.dp)
        }
    }
}
