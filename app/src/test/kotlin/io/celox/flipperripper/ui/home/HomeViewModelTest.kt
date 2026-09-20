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
import io.celox.flipperripper.ui.AppNavTarget
import io.celox.flipperripper.ui.AppNavigator
import io.celox.flipperripper.ui.IncomingLinkBus
import io.celox.flipperripper.ui.ShareLinkHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
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
    private val navigator = AppNavigator()
    private var handler: ShareLinkHandler? = null

    private val handlerScopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = handlerScopes.forEach { it.cancel() }

    /**
     * One handler per test, on a scope the scheduler drives — `backgroundScope` would leave its
     * collectors un-advanced by `advanceUntilIdle()` (see ShareLinkHandlerTest).
     */
    private fun TestScope.shareHandler(): ShareLinkHandler =
        handler ?: ShareLinkHandler(
            incomingLinkBus = bus,
            settingsRepository = settings,
            appNavigator = navigator,
            startDownload = StartDownloadUseCase(downloadRepo),
            resolveUrl = ResolveUrlUseCase(),
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)).also { handlerScopes += it },
        ).also { handler = it }

    private fun TestScope.createViewModel() =
        HomeViewModel(
            resolveUrl = ResolveUrlUseCase(),
            resolveVideoInfo = ResolveVideoInfoUseCase(engineRepo, videoRepo),
            startDownload = StartDownloadUseCase(downloadRepo),
            peekClipboardUrl = PeekClipboardUrlUseCase(clipboardRepo),
            observeEngineReady = ObserveEngineReadyUseCase(engineRepo),
            settingsRepository = settings,
            shareLinkHandler = shareHandler(),
            appNavigator = navigator,
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
    fun `download navigates to History app-wide and resets input`() =
        runTest {
            downloadRepo.nextId = "rec-9"
            val vm = createViewModel()
            vm.onUrlChange("https://youtu.be/abc")
            navigator.events.test {
                vm.download(DownloadMode.VIDEO)
                advanceUntilIdle()
                // Through AppNavigator, NOT a Home-screen event: the jump to History must also
                // happen when Home is not composed (a link shared while another tab was open).
                assertThat(awaitItem()).isEqualTo(AppNavTarget.HISTORY)
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
            // The clipboard read is dispatched off the caller's thread on purpose, so it settles later.
            advanceUntilIdle()
            assertThat(vm.state.value.clipboardSuggestion).isNotNull()
            vm.acceptClipboardSuggestion()
            advanceUntilIdle()
            assertThat(vm.state.value.clipboardSuggestion).isNull()
            assertThat(vm.state.value.urlInput).isEqualTo("https://www.tiktok.com/@a/video/1")
            assertThat(vm.state.value.detectedPlatform).isEqualTo(Platform.TIKTOK)
        }

    @Test
    fun `a download the user starts here still jumps to History`() =
        runTest {
            // The share path changed, the button did not: tapping Download is a deliberate act, and
            // showing it running is what the user asked for by tapping.
            val vm = createViewModel()
            vm.onUrlChange("https://youtu.be/abc")
            navigator.events.test {
                vm.download(DownloadMode.VIDEO)
                advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(AppNavTarget.HISTORY)
            }
        }

    @Test
    fun `a link shared with auto-download off arrives in the form and resolves`() =
        runTest {
            settings.state.value = UserPreferences(autoDownloadOnShare = false)
            val vm = createViewModel()
            advanceUntilIdle()
            bus.post("https://www.instagram.com/reel/abc/")
            advanceUntilIdle()
            assertThat(vm.state.value.urlInput).isEqualTo("https://www.instagram.com/reel/abc/")
            assertThat(vm.state.value.videoInfo).isNotNull()
            // Consumed, so reopening Home later does not re-fill a link the user has moved on from.
            assertThat(handler!!.prefilledLink.value).isNull()
        }

    @Test
    fun `update notice appears for a newer release and dismiss hides it`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()
            assertThat(vm.state.value.updateNotice).isNull()
            settings.knownUpdate.value =
                io.celox.flipperripper.domain.model.AppUpdate("v99.0.0", "https://example.com/rel")
            advanceUntilIdle()
            assertThat(vm.state.value.updateNotice?.version).isEqualTo("v99.0.0")
            vm.dismissUpdateNotice()
            advanceUntilIdle()
            assertThat(vm.state.value.updateNotice).isNull()
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
