package io.celox.flipperripper.data.engine

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.BackendConfig
import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadSource
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.testing.FakeBackendConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Regression tests for the server-backend engine.
 *
 * The engine is reached during application start-up (the warm-up calls [YtDlpEngine.ensureInitialized]),
 * so anything it *throws* rather than returns becomes a launch crash. A user who saved a server address
 * without a scheme used to put the app into an unrecoverable crash loop — it died before any UI existed,
 * so Settings could never be reached to correct the address. Every failure mode must therefore come back
 * as a typed [EngineResult.Failure].
 */
// Robolectric supplies a working org.json; the plain JVM stubs return null from every call.
@RunWith(RobolectricTestRunner::class)
class RemoteYtDlpEngineTest {
    private fun engine(url: String, key: String = "k") =
        RemoteYtDlpEngine(
            configRepository =
            FakeBackendConfigRepository(
                BackendConfig(DownloadSource.SERVER, url, key),
            ),
            // A real dispatcher: these cases exercise genuine URL parsing and socket behaviour, so
            // there is no virtual time to control.
            ioDispatcher = Dispatchers.IO,
        )

    @Test
    fun `ensureInitialized reports a malformed server url instead of throwing`() =
        runTest {
            // No scheme — OkHttp rejects this by throwing IllegalArgumentException.
            val result = engine("flipper.example.com").ensureInitialized()

            assertThat(result).isInstanceOf(EngineResult.Failure::class.java)
            val error = (result as EngineResult.Failure).error
            assertThat(error).isInstanceOf(DownloadError.EngineNotReady::class.java)
            assertThat(error.message).contains("not a valid URL")
        }

    @Test
    fun `a malformed url leaves the engine not ready rather than crashing`() =
        runTest {
            val engine = engine("nonsense::/")

            engine.ensureInitialized()

            assertThat(engine.isReady.value).isFalse()
        }

    @Test
    fun `fetchInfo reports a malformed server url instead of throwing`() =
        runTest {
            val result = engine("flipper.example.com").fetchInfo("https://youtu.be/x", DownloadMode.VIDEO)

            assertThat(result).isInstanceOf(EngineResult.Failure::class.java)
            assertThat((result as EngineResult.Failure).error)
                .isInstanceOf(DownloadError.EngineNotReady::class.java)
        }

    @Test
    fun `download reports a malformed server url instead of throwing`() =
        runTest {
            val spec =
                DownloadSpec(
                    url = "https://youtu.be/x",
                    platform = io.celox.flipperripper.domain.model.Platform.YOUTUBE,
                    mode = DownloadMode.VIDEO,
                    workingDir = File.createTempFile("flipper", "dir").apply { delete() },
                    processId = "p1",
                )

            val result = engine("flipper.example.com").download(spec) { }

            assertThat(result).isInstanceOf(EngineResult.Failure::class.java)
            assertThat((result as EngineResult.Failure).error)
                .isInstanceOf(DownloadError.EngineNotReady::class.java)
        }

    @Test
    fun `an unreachable server surfaces a network error, not an exception`() =
        runTest {
            // Loopback port 1: nothing listens, so the connection is refused immediately (fast and
            // deterministic, unlike an unroutable address that would sit out the connect timeout).
            val result = engine("http://127.0.0.1:1").ensureInitialized()

            assertThat(result).isInstanceOf(EngineResult.Failure::class.java)
            assertThat((result as EngineResult.Failure).error)
                .isInstanceOf(DownloadError.Network::class.java)
        }

    @Test
    fun `update is a no-op handled by the server`() =
        runTest {
            val result = engine("https://flipper.example.com").update()

            assertThat(result).isInstanceOf(EngineResult.Success::class.java)
            assertThat((result as EngineResult.Success).value).isEqualTo("SERVER")
        }

    @Test
    fun `cancel without a running job does not throw`() =
        runTest {
            engine("flipper.example.com").cancel("unknown-process")
        }
}
