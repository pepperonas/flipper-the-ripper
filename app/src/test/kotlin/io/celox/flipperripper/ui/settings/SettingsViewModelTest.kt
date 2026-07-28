package io.celox.flipperripper.ui.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.EngineResult
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
    fun `update engine success emits message`() =
        runTest {
            engineRepo.updateResult = EngineResult.Success("DONE")
            val vm = createViewModel()
            vm.messages.test {
                vm.updateEngineNow()
                advanceUntilIdle()
                assertThat(awaitItem()).contains("DONE")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `update engine failure emits error message`() =
        runTest {
            engineRepo.updateResult =
                EngineResult.Failure(io.celox.flipperripper.domain.model.DownloadError.Network())
            val vm = createViewModel()
            vm.messages.test {
                vm.updateEngineNow()
                advanceUntilIdle()
                assertThat(awaitItem()).isNotEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }
}
