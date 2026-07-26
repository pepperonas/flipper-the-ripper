package io.celox.flipperripper.ui.history

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadStatus
import io.celox.flipperripper.domain.usecase.CancelDownloadUseCase
import io.celox.flipperripper.domain.usecase.ClearHistoryUseCase
import io.celox.flipperripper.domain.usecase.DeleteRecordUseCase
import io.celox.flipperripper.domain.usecase.ObserveHistoryUseCase
import io.celox.flipperripper.domain.usecase.RetryDownloadUseCase
import io.celox.flipperripper.testing.FakeDownloadRepository
import io.celox.flipperripper.testing.MainDispatcherRule
import io.celox.flipperripper.testing.sampleRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeDownloadRepository()

    private fun createViewModel() =
        HistoryViewModel(
            observeHistory = ObserveHistoryUseCase(repo),
            cancelDownload = CancelDownloadUseCase(repo),
            retryDownload = RetryDownloadUseCase(repo),
            deleteRecord = DeleteRecordUseCase(repo),
            clearHistory = ClearHistoryUseCase(repo),
        )

    @Test
    fun `history reflects repository`() =
        runTest {
            val vm = createViewModel()
            vm.history.test {
                assertThat(awaitItem()).isEmpty()
                repo.history.value = listOf(sampleRecord(id = "a"), sampleRecord(id = "b"))
                assertThat(awaitItem()).hasSize(2)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `actions delegate to repository`() =
        runTest {
            val vm = createViewModel()
            vm.cancel("1")
            vm.retry("2")
            vm.delete("3")
            vm.clearAll()
            advanceUntilIdle()
            assertThat(repo.cancelled).containsExactly("1")
            assertThat(repo.retried).containsExactly("2")
            assertThat(repo.deleted).containsExactly("3")
            assertThat(repo.cleared).isTrue()
        }

    @Test
    fun `running record stays observable`() =
        runTest {
            repo.history.value = listOf(sampleRecord(id = "x", status = DownloadStatus.RUNNING))
            val vm = createViewModel()
            vm.history.test {
                var list = awaitItem()
                while (list.isEmpty()) list = awaitItem() // skip the stateIn initial value
                assertThat(list.first().status).isEqualTo(DownloadStatus.RUNNING)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
