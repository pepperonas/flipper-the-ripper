package io.celox.flipperripper.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.ui.history.ClearHistoryDialog
import io.celox.flipperripper.ui.theme.FlipperTheme
import org.junit.Rule
import org.junit.Test

/**
 * Clearing the history is irreversible, so the guarantee under test is a negative one: the confirm
 * callback fires only on the confirm button, and every other way out of the dialog leaves the history
 * untouched.
 */
class ClearHistoryDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var confirmed = 0
    private var dismissed = 0

    private fun show(entryCount: Int = 7) {
        composeRule.setContent {
            FlipperTheme {
                ClearHistoryDialog(
                    entryCount = entryCount,
                    onConfirm = { confirmed++ },
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    @Test
    fun theDialogSaysWhatIsLostAndWhatIsNot() {
        show(entryCount = 7)
        composeRule.onNodeWithText("Clear download history?").assertIsDisplayed()
        // The count is named, and so is the fact that the files survive — otherwise the safe option
        // looks like the dangerous one.
        composeRule.onNodeWithText("7", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("stay on your device", substring = true).assertIsDisplayed()
    }

    @Test
    fun aSingleEntryIsNotCalledOneEntries() {
        // A count of 1 must not read "all 1 entries" — the body is a plural resource.
        show(entryCount = 1)
        composeRule.onNodeWithText("1 entries", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("the one entry", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("video stays on your device", substring = true).assertIsDisplayed()
    }

    @Test
    fun cancellingClearsNothing() {
        show()
        composeRule.onNodeWithText("Cancel").performClick()
        assertThat(confirmed).isEqualTo(0)
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun onlyTheConfirmButtonConfirms() {
        show()
        composeRule.onNodeWithText("Clear history").performClick()
        assertThat(confirmed).isEqualTo(1)
        assertThat(dismissed).isEqualTo(0)
    }
}
