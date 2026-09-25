package io.celox.flipperripper.ui.login

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.data.engine.InstagramSession
import io.celox.flipperripper.domain.usecase.RetryDownloadUseCase
import io.celox.flipperripper.testing.FakeDownloadRepository
import io.celox.flipperripper.testing.MainDispatcherRule
import io.celox.flipperripper.ui.AppNavTarget
import io.celox.flipperripper.ui.AppNavigator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InstagramSignInViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeDownloadRepository()
    private val navigator = AppNavigator()
    private var cookie = false
    private val session = InstagramSession { cookie }

    private fun vm(retry: String?) =
        InstagramSignInViewModel(
            savedStateHandle = SavedStateHandle(if (retry == null) emptyMap() else mapOf(InstagramSignInViewModel.ARG_RETRY to retry)),
            session = session,
            retryDownload = RetryDownloadUseCase(repo),
            appNavigator = navigator,
        )

    @Test
    fun `the wizard walks intro, login, done and restarts the failed download`() =
        runTest {
            val vm = vm("rec-1")
            assertThat(vm.step.value).isEqualTo(SignInStep.INTRO)
            vm.onContinue()
            assertThat(vm.step.value).isEqualTo(SignInStep.LOGIN)
            cookie = true
            vm.onSignedIn()
            advanceUntilIdle()
            assertThat(vm.step.value).isEqualTo(SignInStep.DONE)
            assertThat(repo.retried).containsExactly("rec-1")
        }

    @Test
    fun `a page load without the session cookie does not count as signed in`() =
        runTest {
            val vm = vm("rec-1")
            vm.onContinue()
            vm.onSignedIn()
            advanceUntilIdle()
            assertThat(vm.step.value).isEqualTo(SignInStep.LOGIN)
            assertThat(repo.retried).isEmpty()
        }

    @Test
    fun `the download is restarted once, however often the page reports the login`() =
        runTest {
            val vm = vm("rec-1")
            vm.onContinue()
            cookie = true
            vm.onSignedIn()
            vm.onSignedIn()
            advanceUntilIdle()
            assertThat(repo.retried).containsExactly("rec-1")
        }

    @Test
    fun `already signed in when it opens, it goes straight to done and retries`() =
        runTest {
            cookie = true
            val vm = vm("rec-2")
            advanceUntilIdle()
            assertThat(vm.step.value).isEqualTo(SignInStep.DONE)
            assertThat(repo.retried).containsExactly("rec-2")
        }

    @Test
    fun `finishing after a restarted download shows the downloads`() =
        runTest {
            val vm = vm("rec-1")
            vm.onContinue()
            cookie = true
            vm.onSignedIn()
            advanceUntilIdle()
            var closed = false
            navigator.events.test {
                vm.onFinished { closed = true }
                assertThat(awaitItem()).isEqualTo(AppNavTarget.HISTORY)
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(closed).isTrue()
        }

    @Test
    fun `from Home there is nothing to restart and nowhere else to go`() =
        runTest {
            val vm = vm(null)
            vm.onContinue()
            cookie = true
            vm.onSignedIn()
            advanceUntilIdle()
            assertThat(vm.step.value).isEqualTo(SignInStep.DONE)
            assertThat(repo.retried).isEmpty()
            var closed = false
            navigator.events.test {
                vm.onFinished { closed = true }
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(closed).isTrue()
        }

    @Test
    fun `not now closes without restarting anything`() =
        runTest {
            val vm = vm("rec-1")
            var closed = false
            vm.onFinished { closed = true }
            advanceUntilIdle()
            assertThat(closed).isTrue()
            assertThat(repo.retried).isEmpty()
        }

    @Test
    fun `the route argument name matches the navigation pattern`() {
        // FlipperApp spells the pattern out as "?retry={retry}" (line length); both must agree.
        assertThat(InstagramSignInViewModel.ARG_RETRY).isEqualTo("retry")
    }
}
