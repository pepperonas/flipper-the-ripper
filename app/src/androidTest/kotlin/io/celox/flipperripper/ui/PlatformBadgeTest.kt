package io.celox.flipperripper.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.ui.components.PlatformBadge
import io.celox.flipperripper.ui.theme.FlipperTheme
import org.junit.Rule
import org.junit.Test

class PlatformBadgeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsPlatformDisplayName() {
        // The badge contains a continuously morphing motif; drive the clock manually so the infinite
        // animation doesn't block test idle synchronization.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipperTheme { PlatformBadge(platform = Platform.TIKTOK) }
        }
        composeRule.onNodeWithText("TikTok").assertIsDisplayed()
    }
}
