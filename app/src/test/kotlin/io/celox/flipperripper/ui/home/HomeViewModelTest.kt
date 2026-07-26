package io.celox.flipperripper.ui.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.domain.model.UserPreferences
import io.celox.flipperripper.domain.usecase.ObserveEngineReadyUseCase
import io.celox.flipperripper.domain.usecase.PeekClipboardUrlUseCase
import io.celox.flipperripper.domain.usecase.ResolveUrlUseCase
import io.celox.flipperripper.domain.usecase.ResolveVideoInfoUseCase
import io.celox.flipperripper.domain.usecase.StartDownloadUseCase
import io.celox.flipperripper.domain.util.ParsedUrl
import io.celox.flipperripper.testing.FakeClipboardRepository
import io.celox.flipperripper.testing.FakeDownloadRepository
import io.celox.flipperripper.testing.FakeEngineRepository
import io.celox.flipperripper.testing.FakeSettingsRepository
import io.celox.flipperripper.testing.FakeVideoRepository
import io.celox.flipperripper.testing.MainDispatcherRule
import io.celox.flipperripper.ui.IncomingLinkBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val engineRepo = FakeEngineRepository(ready = true)
    private val videoRepo = FakeVideoRepository()
    private val downloadRepo = FakeDownloadRepository()
    private val clipboardRepo = FakeClipboardRepository()
    private val settings = FakeSettingsRepository()
    private val bus = IncomingLinkBus()

    private fun createViewModel() =
        HomeViewModel(
            resolveUrl = ResolveUrlUseCase(),
            resolveVideoInfo = ResolveVideoInfoUseCase(engineRepo, videoRepo),
            startDownload = StartDownloadUseCase(downloadRepo),
            peekClipboardUrl = PeekClipboardUrlUseCase(clipboardRepo),
            observeEngineReady = ObserveEngineReadyUseCase(engineRepo),
            settingsRepository = settings,
            incomingLinkBus = bus,
        )

    @Test
    fun `onUrlChange detects platform`() =
        runTest {
            val vm = createViewModel()
            vm.onUrlChange("https://youtu.be/abc")
            assertThat(vm.state.value.detectedPlatform).isEqualTo(Platform.YOUTUBE)
            assertThat(vm.state.value.canDownload).isTrue()
            assertThat(vm.state.value.showAudioOption).isTrue()
        }

    @Test
    fun `resolve populates video info`() =
        runTest {
            val vm = createViewModel()
            vm.onUrlChange("https://youtu.be/abc")
            vm.resolve()
            advanceUntilIdle()
            assertThat(vm.state.value.isResolving).isFalse()
            assertThat(vm.state.value.videoInfo?.title).isEqualTo("Sample title")
        }

    @Test
    fun `resolve surfaces engine error`() =
        runTest {
            videoRepo.result = EngineResult.Failure(DownloadError.PrivateVideo("This video is private."))
            val vm = createViewModel()
            vm.onUrlChange("https://youtu.be/abc")
            vm.resolve()
            advanceUntilIdle()
            assertThat(vm.state.value.errorMessage).contains("private")
        }

    @Test
    fun `resolve on unsupported url sets error without calling engine`() =
        runTest {
            val vm = createViewModel()
            vm.onUrlChange("https://vimeo.com/1")
            vm.resolve()
            advanceUntilIdle()
            assertThat(vm.state.value.errorMessage).isNotNull()
            assertThat(videoRepo.lastUrl).isNull()
        }

    @Test
    fun `download emits DownloadStarted and resets input`() =
        runTest {
            downloadRepo.nextId = "rec-9"
            val vm = createViewModel()
            vm.onUrlChange("https://youtu.be/abc")
            vm.events.test {
                vm.download(DownloadMode.VIDEO)
                advanceUntilIdle()
                val event = awaitItem()
                assertThat(event).isInstanceOf(HomeEvent.DownloadStarted::class.java)
                assertThat((event as HomeEvent.DownloadStarted).recordId).isEqualTo("rec-9")
            }
            assertThat(downloadRepo.enqueued).hasSize(1)
            assertThat(vm.state.value.urlInput).isEmpty()
        }

    @Test
    fun `clipboard suggestion is offered and accepted`() =
        runTest {
            clipboardRepo.suggestion = ParsedUrl("https://www.tiktok.com/@a/video/1", Platform.TIKTOK)
            val vm = createViewModel()
            advanceUntilIdle()
            vm.checkClipboard(prefEnabled = true)
            assertThat(vm.state.value.clipboardSuggestion).isNotNull()
            vm.acceptClipboardSuggestion()
            advanceUntilIdle()
            assertThat(vm.state.value.clipboardSuggestion).isNull()
            assertThat(vm.state.value.urlInput).isEqualTo("https://www.tiktok.com/@a/video/1")
            assertThat(vm.state.value.detectedPlatform).isEqualTo(Platform.TIKTOK)
        }

    @Test
    fun `shared link with auto-download enqueues immediately`() =
        runTest {
            settings.state.value = UserPreferences(autoDownloadOnShare = true)
            val vm = createViewModel()
            advanceUntilIdle()
            bus.post("Watch this https://www.instagram.com/reel/abc/")
            advanceUntilIdle()
            assertThat(downloadRepo.enqueued).hasSize(1)
            assertThat(downloadRepo.enqueued.first().platform).isEqualTo(Platform.INSTAGRAM)
        }

    @Test
    fun `shared link without auto-download only resolves`() =
        runTest {
            settings.state.value = UserPreferences(autoDownloadOnShare = false)
            val vm = createViewModel()
            advanceUntilIdle()
            bus.post("https://www.instagram.com/reel/abc/")
            advanceUntilIdle()
            assertThat(downloadRepo.enqueued).isEmpty()
            assertThat(vm.state.value.videoInfo).isNotNull()
        }

    @Test
    fun `engine readiness is reflected in state`() =
        runTest {
            engineRepo.setReady(false)
            val vm = createViewModel()
            advanceUntilIdle()
            assertThat(vm.state.value.engineReady).isFalse()
            engineRepo.setReady(true)
            advanceUntilIdle()
            assertThat(vm.state.value.engineReady).isTrue()
        }
}
