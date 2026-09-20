package io.celox.flipperripper.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.QualityChoice
import io.celox.flipperripper.ui.home.QualityOptions
import io.celox.flipperripper.ui.theme.FlipperTheme
import org.junit.Rule
import org.junit.Test

/**
 * Which tiers a person can actually reach.
 *
 * Worth an instrumented test rather than a `uiautomator` check: Compose's disabled state does not
 * show up in the accessibility dump's `enabled` attribute — reading that on the device reported
 * every tier as enabled while the screenshot plainly showed three of them greyed out.
 */
class QualitySheetTest {
    @get:Rule val composeRule = createComposeRule()

    private val picked = mutableListOf<QualityChoice>()

    private fun show(available: Set<QualityChoice>, selected: QualityChoice = QualityChoice.BEST) {
        composeRule.setContent {
            FlipperTheme {
                QualityOptions(
                    available = available,
                    selected = selected,
                    onSelect = { picked += it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    @Test
    fun aTierTheVideoCannotDeliverIsNotSelectable() {
        // "Me at the zoo" exists in 144p and 240p only.
        show(setOf(QualityChoice.BEST, QualityChoice.AUDIO_ONLY))
        composeRule.onNodeWithText("Best").assertIsEnabled()
        composeRule.onNodeWithText("Audio only").assertIsEnabled()
        listOf("1080p", "720p", "480p").forEach {
            composeRule.onNodeWithText(it).assertIsNotEnabled()
        }
    }

    @Test
    fun everyTierIsOfferedWhenTheVideoReachesThem() {
        show(QualityChoice.entries.toSet())
        listOf("Best", "1080p", "720p", "480p", "Audio only").forEach {
            composeRule.onNodeWithText(it).assertIsEnabled()
        }
    }

    @Test
    fun tappingATierReportsIt() {
        show(QualityChoice.entries.toSet())
        composeRule.onNodeWithText("720p").performClick()
        assertThat(picked).containsExactly(QualityChoice.P720)
    }

    @Test
    fun tappingAnUnreachableTierChangesNothing() {
        show(setOf(QualityChoice.BEST))
        composeRule.onNodeWithText("1080p").performClick()
        assertThat(picked).isEmpty()
    }

    @Test
    fun everyTierIsAlwaysVisibleEvenWhenItCannotBeChosen() {
        // Hiding them would make the group's size change under the finger, and "this video does not
        // have 1080p" is information — an absent button says nothing at all.
        show(setOf(QualityChoice.BEST))
        listOf("Best", "1080p", "720p", "480p", "Audio only").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
    }
}
