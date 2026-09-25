package io.celox.flipperripper.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.R
import io.celox.flipperripper.data.engine.InstagramSession
import io.celox.flipperripper.domain.model.DownloadRecord
import io.celox.flipperripper.domain.model.DownloadRequest
import io.celox.flipperripper.domain.repository.DownloadRepository
import io.celox.flipperripper.domain.usecase.RetryDownloadUseCase
import io.celox.flipperripper.ui.login.InstagramSignInViewModel
import io.celox.flipperripper.ui.login.InstagramSignInWizard
import io.celox.flipperripper.ui.theme.FlipperTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

/**
 * The sign-in wizard as a user sees it. Step two (Instagram's own page) needs the network and an
 * account, so it is covered by the ViewModel tests; here: the explanation, the step indicator, and the
 * end state that restarts the failed download.
 */
class InstagramSignInWizardTest {
    @get:Rule val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val retried = mutableListOf<String>()

    private val repo =
        object : DownloadRepository {
            override suspend fun enqueue(request: DownloadRequest) = "x"
            override fun observeHistory(): Flow<List<DownloadRecord>> = flowOf(emptyList())
            override fun observeRecord(id: String): Flow<DownloadRecord?> = flowOf(null)
            override suspend fun cancel(id: String) = Unit
            override suspend fun pause(id: String) = Unit
            override suspend fun resume(id: String) = Unit
            override suspend fun reorder(ids: List<String>) = Unit
            override suspend fun retry(id: String) {
                retried += id
            }
            override suspend fun delete(id: String): DownloadRecord? = null
            override suspend fun restore(record: DownloadRecord) = Unit
            override suspend fun clearHistory() = Unit
        }

    private fun show(signedIn: Boolean, retry: String? = "rec-1") {
        val vm =
            InstagramSignInViewModel(
                savedStateHandle = SavedStateHandle(if (retry == null) emptyMap() else mapOf("retry" to retry)),
                session = InstagramSession { signedIn },
                retryDownload = RetryDownloadUseCase(repo),
                appNavigator = AppNavigator(),
            )
        composeRule.setContent { FlipperTheme { InstagramSignInWizard(onClose = {}, viewModel = vm) } }
    }

    @Test
    fun theFirstStepExplainsWhatHappensToThePassword() {
        show(signedIn = false)
        composeRule.onNodeWithText(context.getString(R.string.ig_wizard_step, 1, 3)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.ig_wizard_intro_password)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.ig_wizard_continue)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.ig_wizard_not_now)).assertIsDisplayed()
    }

    @Test
    fun continueMovesToInstagramsPage() {
        show(signedIn = false)
        composeRule.onNodeWithText(context.getString(R.string.ig_wizard_continue)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.ig_wizard_step, 2, 3)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.ig_wizard_login_hint)).assertIsDisplayed()
    }

    @Test
    fun signedInItConfirmsAndHasAlreadyRestartedTheDownload() {
        show(signedIn = true)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.ig_wizard_step, 3, 3)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.ig_wizard_done_retry)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.ig_wizard_show_downloads)).assertIsDisplayed()
        assertThat(retried).containsExactly("rec-1")
    }

    @Test
    fun fromHomeItSaysTheInfoReloadsInstead() {
        show(signedIn = true, retry = null)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.ig_wizard_done_home)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.ig_wizard_back)).assertIsDisplayed()
        assertThat(retried).isEmpty()
    }
}
