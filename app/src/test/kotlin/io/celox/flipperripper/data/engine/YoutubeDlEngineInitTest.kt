package io.celox.flipperripper.data.engine

import com.google.common.truth.Truth.assertThat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.EngineResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Regression tests for engine initialisation failure handling.
 *
 * `ensureInitialized()` is called from the application-scope warm-up during start-up, so whatever it
 * lets escape kills the process before any UI exists. It used to catch only [YoutubeDLException] — but
 * `YoutubeDL.init()` unpacks a native payload through reflection-heavy third-party code and can fail
 * with anything, including an [Error]. In the shipped release build it raised
 * `ExceptionInInitializerError`, which sailed straight past the catch and put the app in a permanent
 * launch-crash loop.
 *
 * A broken engine has to become a typed [DownloadError.EngineNotReady] the UI can explain — never a
 * crash.
 */
@RunWith(RobolectricTestRunner::class)
class YoutubeDlEngineInitTest {
    private fun engine() =
        YoutubeDlEngine(
            appContext = RuntimeEnvironment.getApplication(),
            ioDispatcher = Dispatchers.IO,
        )

    @After
    fun tearDown() = unmockkAll()

    private fun stubInitToThrow(throwable: Throwable) {
        mockkStatic(YoutubeDL::class)
        val instance = mockk<YoutubeDL>(relaxed = true)
        every { YoutubeDL.getInstance() } returns instance
        every { instance.init(any()) } throws throwable
    }

    @Test
    fun `an Error during init becomes a typed failure instead of propagating`() =
        runTest {
            // The exact failure that shipped: commons-compress's static registry blew up under R8.
            stubInitToThrow(
                ExceptionInInitializerError(
                    RuntimeException("class org.apache.commons.compress.archivers.zip.a is not a concrete class"),
                ),
            )

            val result = engine().ensureInitialized()

            assertThat(result).isInstanceOf(EngineResult.Failure::class.java)
            assertThat((result as EngineResult.Failure).error)
                .isInstanceOf(DownloadError.EngineNotReady::class.java)
        }

    @Test
    fun `a YoutubeDLException during init becomes a typed failure`() =
        runTest {
            stubInitToThrow(YoutubeDLException("failed to initialize"))

            val result = engine().ensureInitialized()

            assertThat(result).isInstanceOf(EngineResult.Failure::class.java)
            val error = (result as EngineResult.Failure).error
            assertThat(error).isInstanceOf(DownloadError.EngineNotReady::class.java)
            assertThat(error.message).contains("failed to initialize")
        }

    @Test
    fun `an UnsatisfiedLinkError during init becomes a typed failure`() =
        runTest {
            stubInitToThrow(UnsatisfiedLinkError("libpython not found"))

            val result = engine().ensureInitialized()

            assertThat(result).isInstanceOf(EngineResult.Failure::class.java)
            assertThat((result as EngineResult.Failure).error)
                .isInstanceOf(DownloadError.EngineNotReady::class.java)
        }

    @Test
    fun `a failed init leaves the engine not ready`() =
        runTest {
            stubInitToThrow(IllegalStateException("boom"))
            val engine = engine()

            engine.ensureInitialized()

            assertThat(engine.isReady.value).isFalse()
        }

    @Test
    fun `a failed init still reports a message when the throwable has none`() =
        runTest {
            stubInitToThrow(NullPointerException())

            val result = engine().ensureInitialized()

            val error = (result as EngineResult.Failure).error
            // Falls back to the throwable's type so the user is not shown an empty reason.
            assertThat(error.message).contains("NullPointerException")
        }

    @Test
    fun `operations short-circuit with the init failure rather than running`() =
        runTest {
            stubInitToThrow(YoutubeDLException("failed to initialize"))

            val result =
                engine().fetchInfo("https://youtu.be/x", io.celox.flipperripper.domain.model.DownloadMode.VIDEO)

            assertThat(result).isInstanceOf(EngineResult.Failure::class.java)
            assertThat((result as EngineResult.Failure).error)
                .isInstanceOf(DownloadError.EngineNotReady::class.java)
        }
}
