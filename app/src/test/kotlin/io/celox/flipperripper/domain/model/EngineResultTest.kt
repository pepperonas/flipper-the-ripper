package io.celox.flipperripper.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EngineResultTest {
    @Test
    fun `getOrNull returns value or null`() {
        assertThat(EngineResult.Success(5).getOrNull()).isEqualTo(5)
        val failureValue: Any? = EngineResult.Failure(DownloadError.Network()).getOrNull()
        assertThat(failureValue).isNull()
    }

    @Test
    fun `map transforms success and preserves failure`() {
        assertThat(EngineResult.Success(2).map { it * 3 }).isEqualTo(EngineResult.Success(6))
        val failure = EngineResult.Failure(DownloadError.Cancelled())
        assertThat(failure.map { it }).isEqualTo(failure)
    }

    @Test
    fun `onSuccess and onFailure run the matching branch only`() {
        var ok = 0
        var err = 0
        EngineResult.Success(1).onSuccess { ok++ }.onFailure { err++ }
        assertThat(ok).isEqualTo(1)
        assertThat(err).isEqualTo(0)

        EngineResult.Failure(DownloadError.Network()).onSuccess { ok++ }.onFailure { err++ }
        assertThat(ok).isEqualTo(1)
        assertThat(err).isEqualTo(1)
    }

    @Test
    fun `error kind is the class name`() {
        assertThat(DownloadError.PrivateVideo("x").kind).isEqualTo("PrivateVideo")
        assertThat(DownloadError.InvalidUrl().kind).isEqualTo("InvalidUrl")
    }
}
