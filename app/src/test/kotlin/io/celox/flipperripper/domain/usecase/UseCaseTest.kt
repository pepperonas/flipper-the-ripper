package io.celox.flipperripper.domain.usecase

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadRequest
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.testing.FakeDownloadRepository
import io.celox.flipperripper.testing.FakeEngineRepository
import io.celox.flipperripper.testing.FakeVideoRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class UseCaseTest {
    @Test
    fun `ResolveVideoInfo rejects unsupported url before touching engine`() =
        runTest {
            val engine = FakeEngineRepository()
            val useCase = ResolveVideoInfoUseCase(engine, FakeVideoRepository())
            val result = useCase("https://vimeo.com/1", DownloadMode.VIDEO)
            assertThat(result).isInstanceOf(EngineResult.Failure::class.java)
            assertThat((result as EngineResult.Failure).error).isInstanceOf(DownloadError.InvalidUrl::class.java)
            assertThat(engine.ensureCalls).isEqualTo(0)
        }

    @Test
    fun `ResolveVideoInfo surfaces engine init failure`() =
        runTest {
            val engine = FakeEngineRepository(initResult = EngineResult.Failure(DownloadError.EngineNotReady()))
            val useCase = ResolveVideoInfoUseCase(engine, FakeVideoRepository())
            val result = useCase("https://youtu.be/x", DownloadMode.VIDEO)
            assertThat((result as EngineResult.Failure).error).isInstanceOf(DownloadError.EngineNotReady::class.java)
        }

    @Test
    fun `ResolveVideoInfo returns info on success`() =
        runTest {
            val video = FakeVideoRepository()
            val useCase = ResolveVideoInfoUseCase(FakeEngineRepository(), video)
            val result = useCase("https://youtu.be/x", DownloadMode.VIDEO)
            assertThat(result).isInstanceOf(EngineResult.Success::class.java)
            assertThat((result as EngineResult.Success).value.title).isEqualTo("Sample title")
            assertThat(video.lastUrl).isEqualTo("https://youtu.be/x")
        }

    @Test
    fun `StartDownload rejects unsupported url`() =
        runTest {
            val repo = FakeDownloadRepository()
            val useCase = StartDownloadUseCase(repo)
            val result = useCase(DownloadRequest("not a url", Platform.YOUTUBE))
            assertThat((result as EngineResult.Failure).error).isInstanceOf(DownloadError.InvalidUrl::class.java)
            assertThat(repo.enqueued).isEmpty()
        }

    @Test
    fun `StartDownload enqueues and returns id`() =
        runTest {
            val repo = FakeDownloadRepository().apply { nextId = "abc" }
            val useCase = StartDownloadUseCase(repo)
            val result = useCase(DownloadRequest("https://youtu.be/x", Platform.YOUTUBE, DownloadMode.VIDEO))
            assertThat((result as EngineResult.Success).value).isEqualTo("abc")
            assertThat(repo.enqueued).hasSize(1)
        }

    @Test
    fun `history use cases delegate to repository`() =
        runTest {
            val repo = FakeDownloadRepository()
            CancelDownloadUseCase(repo)("1")
            RetryDownloadUseCase(repo)("2")
            DeleteRecordUseCase(repo)("3")
            ClearHistoryUseCase(repo)()
            assertThat(repo.cancelled).containsExactly("1")
            assertThat(repo.retried).containsExactly("2")
            assertThat(repo.deleted).containsExactly("3")
            assertThat(repo.cleared).isTrue()
        }
}
