package io.celox.flipperripper.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.domain.model.UserPreferences
import io.celox.flipperripper.domain.usecase.ResolveUrlUseCase
import io.celox.flipperripper.domain.usecase.StartDownloadUseCase
import io.celox.flipperripper.testing.FakeDownloadRepository
import io.celox.flipperripper.testing.FakeSettingsRepository
import io.celox.flipperripper.testing.MainDispatcherRule
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
import java.io.File

/**
 * What happens to a link shared into the app.
 *
 * This used to live in `HomeViewModel`, and the consequence was measured rather than argued: after
 * the system reclaimed the process, a share was **silently dropped** whenever the restored tab was
 * not Home — 5 runs out of 5, because the ViewModel that held the only consumer was never built.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShareLinkHandlerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val bus = IncomingLinkBus()
    private val navigator = AppNavigator()
    private val settings = FakeSettingsRepository()
    private val downloads = FakeDownloadRepository()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    /**
     * A scope the test scheduler actually drives.
     *
     * Deliberately **not** `backgroundScope`: `advanceUntilIdle()` stops as soon as only background
     * work is left, so the handler's collectors ran somewhere after the assertions instead of before
     * them — three tests here failed for that reason and none of them was about the handler.
     * The handler lives on the application scope in production, which this mirrors.
     */
    private fun TestScope.handler(): ShareLinkHandler {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        scopes += scope
        return ShareLinkHandler(
            incomingLinkBus = bus,
            settingsRepository = settings,
            appNavigator = navigator,
            startDownload = StartDownloadUseCase(downloads),
            resolveUrl = ResolveUrlUseCase(),
            scope = scope,
        )
    }

    @Test
    fun `with auto-download on, the share enqueues and shows History`() =
        runTest {
            settings.state.value = UserPreferences(autoDownloadOnShare = true)
            handler()
            navigator.events.test {
                bus.post("Watch this https://www.instagram.com/reel/abc/")
                advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(AppNavTarget.HISTORY)
                // Exactly one navigation — no detour via Home that History then overrides.
                expectNoEvents()
            }
            assertThat(downloads.enqueued).hasSize(1)
            assertThat(downloads.enqueued.first().platform).isEqualTo(Platform.INSTAGRAM)
        }

    @Test
    fun `with auto-download off, nothing is enqueued and the link waits for Home`() =
        runTest {
            settings.state.value = UserPreferences(autoDownloadOnShare = false)
            val handler = handler()
            navigator.events.test {
                bus.post("https://www.instagram.com/reel/abc/")
                advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(AppNavTarget.HOME)
            }
            assertThat(downloads.enqueued).isEmpty()
            assertThat(handler.prefilledLink.value).isEqualTo("https://www.instagram.com/reel/abc/")
        }

    @Test
    fun `a waiting link is held until it is consumed, not fired once and lost`() =
        runTest {
            // The whole failure mode this class exists for: a one-shot hand-off is lost whenever the
            // receiver is not there yet. The waiting link must survive until Home actually takes it.
            settings.state.value = UserPreferences(autoDownloadOnShare = false)
            val handler = handler()
            bus.post("https://www.instagram.com/reel/abc/")
            advanceUntilIdle()
            assertThat(handler.prefilledLink.value).isNotNull()
            assertThat(handler.prefilledLink.value).isNotNull() // still there on a second look
            handler.consumePrefilledLink()
            assertThat(handler.prefilledLink.value).isNull()
        }

    @Test
    fun `the stored preference decides, not the placeholder default`() =
        runTest {
            // Preferences load asynchronously and the *default* is auto-download ON. Handing that
            // default out before the stored value arrives would download for someone who switched
            // it off — so the handler waits for the real value.
            settings.state.value = UserPreferences(autoDownloadOnShare = false)
            handler()
            bus.post("https://www.instagram.com/reel/abc/")
            advanceUntilIdle()
            assertThat(downloads.enqueued).isEmpty()
        }

    @Test
    fun `the default download mode from settings is used`() =
        runTest {
            settings.state.value =
                UserPreferences(autoDownloadOnShare = true, defaultMode = DownloadMode.AUDIO)
            handler()
            bus.post("https://youtu.be/abc")
            advanceUntilIdle()
            assertThat(downloads.enqueued.single().mode).isEqualTo(DownloadMode.AUDIO)
        }

    @Test
    fun `a link that is not from a supported platform does nothing at all`() =
        runTest {
            handler()
            navigator.events.test {
                bus.post("just some text with no link in it")
                advanceUntilIdle()
                expectNoEvents()
            }
            assertThat(downloads.enqueued).isEmpty()
        }

    @Test
    fun `a link shared before anything is listening is still delivered exactly once`() =
        runTest {
            // Cold start: the Activity posts the share while the graph is still being built.
            settings.state.value = UserPreferences(autoDownloadOnShare = true)
            bus.post("https://www.instagram.com/reel/abc/")
            handler()
            advanceUntilIdle()
            assertThat(downloads.enqueued).hasSize(1)
        }

    @Test
    fun `the shared-link bus has exactly one consumer, and it is not a ViewModel`() {
        // The regression this guards is invisible at runtime: move the consumer back into a screen's
        // ViewModel and shares are dropped whenever that screen is not composed — no crash, no log,
        // no record. Pinned on the property (who reads the bus) rather than on any wording.
        val main = File("src/main/kotlin/io/celox/flipperripper")
        val readers =
            main.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { "incomingLinkBus.links" in it.readText() }
                .map { it.name }
                .toList()
        assertThat(readers).containsExactly("ShareLinkHandler.kt")

        val handlerSource = File(main, "ui/ShareLinkHandler.kt").readText()
        assertThat(handlerSource).contains("@Singleton")
        assertThat(handlerSource).doesNotContain(": ViewModel")
    }
}
