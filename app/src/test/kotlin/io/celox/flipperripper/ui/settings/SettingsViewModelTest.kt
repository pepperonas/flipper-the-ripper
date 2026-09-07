package io.celox.flipperripper.ui.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.EngineUpdateOutcome
import io.celox.flipperripper.domain.model.ThemeMode
import io.celox.flipperripper.domain.usecase.UpdateEngineUseCase
import io.celox.flipperripper.testing.FakeEngineRepository
import io.celox.flipperripper.testing.FakeSettingsRepository
import io.celox.flipperripper.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val engineRepo = FakeEngineRepository()

    private fun createViewModel() =
        SettingsViewModel(
            settings,
            io.celox.flipperripper.testing.FakeBackendConfigRepository(),
            UpdateEngineUseCase(engineRepo),
            io.celox.flipperripper.data.engine.InstagramSession(),
        )

    @Test
    fun `setters update preferences`() =
        runTest {
            val vm = createViewModel()
            vm.setThemeMode(ThemeMode.DARK)
            vm.setDynamicColor(false)
            vm.setAutoDownload(false)
            vm.setClipboardDetection(false)
            vm.setDefaultMode(DownloadMode.AUDIO)
            advanceUntilIdle()
            val prefs = settings.state.value
            assertThat(prefs.themeMode).isEqualTo(ThemeMode.DARK)
            assertThat(prefs.useDynamicColor).isFalse()
            assertThat(prefs.autoDownloadOnShare).isFalse()
            assertThat(prefs.clipboardDetection).isFalse()
            assertThat(prefs.defaultMode).isEqualTo(DownloadMode.AUDIO)
        }

    @Test
    fun `a real update is reported as an update, not as a library enum name`() =
        runTest {
            // The screen used to show "Engine updated: DONE". What crosses the boundary now is state.
            engineRepo.updateResult = EngineResult.Success("DONE")
            val vm = createViewModel()
            vm.messages.test {
                vm.updateEngineNow()
                advanceUntilIdle()
                assertThat(awaitItem())
                    .isEqualTo(SettingsMessage.EngineUpdate(EngineUpdateOutcome.Updated))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an engine that was already current is its own outcome`() =
        runTest {
            // Not a failure, and not the same sentence as a real update.
            engineRepo.updateResult = EngineResult.Success("ALREADY_UP_TO_DATE")
            val vm = createViewModel()
            vm.messages.test {
                vm.updateEngineNow()
                advanceUntilIdle()
                assertThat(awaitItem())
                    .isEqualTo(SettingsMessage.EngineUpdate(EngineUpdateOutcome.AlreadyCurrent))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `update engine failure keeps the error's own wording`() =
        runTest {
            val error = io.celox.flipperripper.domain.model.DownloadError.Network()
            engineRepo.updateResult = EngineResult.Failure(error)
            val vm = createViewModel()
            vm.messages.test {
                vm.updateEngineNow()
                advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(SettingsMessage.Plain(error.message))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the update button reports that it is running, and only runs once`() =
        runTest {
            engineRepo.updateResult = EngineResult.Success("DONE")
            val vm = createViewModel()
            assertThat(vm.updatingEngine.value).isFalse()
            vm.messages.test {
                // A second tap while the first fetch is in flight must not start another one; the
                // update is a network call that takes seconds with the button still on screen.
                vm.updateEngineNow()
                vm.updateEngineNow()
                advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(SettingsMessage.EngineUpdate(EngineUpdateOutcome.Updated))
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(vm.updatingEngine.value).isFalse()
        }
}
