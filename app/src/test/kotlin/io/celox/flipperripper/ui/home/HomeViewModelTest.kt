package io.celox.flipperripper.ui.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.MediaFormat
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.domain.model.QualityChoice
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
import io.celox.flipperripper.testing.sampleVideoInfo
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
            // Audio is a quality tier now, so "is audio offered" is a question about the picker.
            assertThat(vm.state.value.availableQualities).contains(QualityChoice.AUDIO_ONLY)
        }

    @Test
    fun `the picked quality is what gets enqueued`() =
        runTest {
            val vm = createViewModel()
            vm.onUrlChange("https://youtu.be/abc")
            vm.setQuality(QualityChoice.P720)
            vm.download()
            advanceUntilIdle()
            assertThat(downloadRepo.enqueued.single().quality).isEqualTo(QualityChoice.P720)
        }

    @Test
    fun `the mode follows the quality instead of being set beside it`() =
        runTest {
            // The two used to be separate controls and could disagree; a record claiming to be
            // audio at 720p is a contradiction nobody should be able to create.
            val vm = createViewModel()
            vm.onUrlChange("https://youtu.be/abc")
            vm.setQuality(QualityChoice.AUDIO_ONLY)
            vm.download()
            advanceUntilIdle()
            assertThat(downloadRepo.enqueued.single().mode).isEqualTo(DownloadMode.AUDIO)
        }

    @Test
    fun `a fresh screen starts on the remembered default`() =
        runTest {
            settings.state.value = UserPreferences(defaultQuality = QualityChoice.P480)
            val vm = createViewModel()
            advanceUntilIdle()
            assertThat(vm.state.value.quality).isEqualTo(QualityChoice.P480)
        }

    @Test
    fun `a deliberate choice survives the preferences arriving late`() =
        runTest {
            // Preferences load asynchronously. Overwriting a choice the user just made because the
            // stored default turned up a moment later is exactly the surprise the picker avoids.
            val vm = createViewModel()
            vm.setQuality(QualityChoice.P720)
            settings.state.value = UserPreferences(defaultQuality = QualityChoice.AUDIO_ONLY)
            advanceUntilIdle()
            assertThat(vm.state.value.quality).isEqualTo(QualityChoice.P720)
        }

    @Test
    fun `a choice the resolved video cannot deliver falls back to best`() =
        runTest {
            // Measured on the device: picking 720p and then loading a 240p video left "720p" under
            // the button while the tier itself was greyed out — a label for something that would
            // not happen.
            videoRepo.result =
                EngineResult.Success(
                    sampleVideoInfo(
                        formats = listOf(MediaFormat(height = 240, hasVideo = true, hasAudio = true)),
                    ),
                )
            val vm = createViewModel()
            vm.onUrlChange("https://youtu.be/abc")
            vm.setQuality(QualityChoice.P720)
            vm.resolve()
            advanceUntilIdle()
            assertThat(vm.state.value.quality).isEqualTo(QualityChoice.BEST)
        }

    @Test
    fun `a choice the resolved video can deliver is kept`() =
        runTest {
            videoRepo.result =
                EngineResult.Success(
                    sampleVideoInfo(
                        formats = listOf(MediaFormat(height = 1080, hasVideo = true, hasAudio = true)),
                    ),
                )
            val vm = createViewModel()
            vm.onUrlChange("https://youtu.be/abc")
            vm.setQuality(QualityChoice.P720)
            vm.resolve()
            advanceUntilIdle()
            assertThat(vm.state.value.quality).isEqualTo(QualityChoice.P720)
        }

    @Test
    fun `nothing resolved yet offers every tier rather than none`() =
        runTest {
            val vm = createViewModel()
            vm.onUrlChange("https://youtu.be/abc")
            assertThat(vm.state.value.availableQualities).containsExactlyElementsIn(QualityChoice.entries)
        }

    @Test
    fun `a webview platform offers no choice at all`() =
        runTest {
            // Instagram hands back one file; showing tiers that do nothing would be a lie the sheet
            // then has to explain.
            val vm = createViewModel()
            vm.onUrlChange("https://www.instagram.com/reel/DbDBPYJnUMW/")
            assertThat(vm.state.value.availableQualities).containsExactly(QualityChoice.BEST)
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
                vm.download()
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
                vm.download()
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
