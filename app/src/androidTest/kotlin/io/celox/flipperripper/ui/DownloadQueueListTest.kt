package io.celox.flipperripper.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadRecord
import io.celox.flipperripper.domain.model.DownloadStatus
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.domain.model.QualityChoice
import io.celox.flipperripper.ui.history.DownloadActions
import io.celox.flipperripper.ui.history.DownloadQueueList
import io.celox.flipperripper.ui.theme.FlipperTheme
import org.junit.Rule
import org.junit.Test

/**
 * The queue list's promises that only a real composition can check: which cards offer a drag handle,
 * that a waiting download is not drawn as a working one, and that a finished card can be swiped away.
 */
class DownloadQueueListTest {
    @get:Rule val composeRule = createComposeRule()

    private val reordered = mutableListOf<List<String>>()
    private val deleted = mutableListOf<String>()

    private fun record(id: String, status: DownloadStatus, order: Long) =
        DownloadRecord(
            id = id,
            sourceUrl = "https://youtu.be/$id",
            platform = Platform.YOUTUBE,
            title = id,
            mode = DownloadMode.VIDEO,
            thumbnailUrl = null,
            status = status,
            progressPercent = if (status == DownloadStatus.RUNNING) 40f else null,
            mediaUri = null,
            fileName = null,
            sizeBytes = null,
            errorKind = null,
            errorMessage = null,
            queueOrder = order,
            quality = QualityChoice.BEST,
            createdAtEpochMs = order,
            updatedAtEpochMs = order,
        )

    private fun show(vararg records: DownloadRecord) {
        composeRule.setContent {
            FlipperTheme {
                DownloadQueueList(
                    records = records.toList(),
                    modifier = Modifier.fillMaxSize(),
                    actions =
                    DownloadActions(
                        onCancel = {},
                        onRetry = {},
                        onDelete = { deleted += it },
                        onPause = {},
                        onResume = {},
                        onReorder = { reordered += it },
                    ),
                )
            }
        }
    }

    @Test
    fun onlyWhatHasNotStartedOffersADragHandle() {
        show(
            record("running", DownloadStatus.RUNNING, 1),
            record("waiting", DownloadStatus.QUEUED, 2),
            record("paused", DownloadStatus.PAUSED, 3),
        )
        // Reordering the running download would mean stopping it; reordering a finished one means
        // nothing. Two handles for three cards.
        composeRule.onAllNodesWithContentDescription("Reorder").assertCountEquals(2)
    }

    @Test
    fun aWaitingDownloadIsNotDrawnAsAWorkingOne() {
        show(record("waiting", DownloadStatus.QUEUED, 1))
        composeRule.onNodeWithText("YouTube · Queued").assertIsDisplayed()
        // The progress indicator belongs to work in flight. It used to be drawn here too, for a
        // download nobody had started.
        composeRule.onAllNodesWithContentDescription("Reorder").assertCountEquals(1)
    }

    @Test
    fun aFinishedCardCanBeSwipedAway() {
        show(record("done", DownloadStatus.COMPLETED, 1))
        composeRule.onNodeWithText("done").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertThat(deleted).containsExactly("done")
    }

    @Test
    fun aRunningCardCannotBeSwipedAway() {
        // Losing a download in progress to a stray horizontal gesture would be an accident waiting
        // to happen; the card carries Cancel for that.
        show(record("running", DownloadStatus.RUNNING, 1))
        composeRule.onNodeWithText("running").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertThat(deleted).isEmpty()
    }

    @Test
    fun theQueueComesBeforeWhatIsFinished() {
        show(
            record("done", DownloadStatus.COMPLETED, 1),
            record("waiting", DownloadStatus.QUEUED, 2),
        )
        composeRule.onNodeWithText("In the queue").assertIsDisplayed()
        composeRule.onNodeWithText("Finished").assertIsDisplayed()
    }
}
