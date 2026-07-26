package io.celox.flipperripper.data.repository

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.domain.model.VideoInfo
import io.celox.flipperripper.testing.FakeYtDlpEngine
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ThinRepositoriesTest {
    private val sampleInfo =
        VideoInfo("https://youtu.be/x", Platform.YOUTUBE, "T", null, null, null, "x")

    @Test
    fun `video repository delegates resolve to engine`() =
        runTest {
            val engine = FakeYtDlpEngine(infoResult = EngineResult.Success(sampleInfo))
            val repo = VideoRepositoryImpl(engine)
            val result = repo.resolve("https://youtu.be/x", DownloadMode.VIDEO)
            assertThat((result as EngineResult.Success).value.title).isEqualTo("T")
        }

    @Test
    fun `engine repository exposes readiness and update`() =
        runTest {
            val engine = FakeYtDlpEngine(ready = true)
            val repo = EngineRepositoryImpl(engine)
            assertThat(repo.isReady.value).isTrue()
            assertThat(repo.ensureInitialized()).isInstanceOf(EngineResult.Success::class.java)
            assertThat((repo.updateEngine() as EngineResult.Success).value).isEqualTo("DONE")
        }
}
