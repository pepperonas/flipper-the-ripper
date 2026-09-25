package io.celox.flipperripper.ui.history

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadStatus
import io.celox.flipperripper.domain.usecase.CancelDownloadUseCase
import io.celox.flipperripper.domain.usecase.ClearHistoryUseCase
import io.celox.flipperripper.domain.usecase.DeleteRecordUseCase
import io.celox.flipperripper.domain.usecase.ObserveHistoryUseCase
import io.celox.flipperripper.domain.usecase.PauseDownloadUseCase
import io.celox.flipperripper.domain.usecase.ReorderQueueUseCase
import io.celox.flipperripper.domain.usecase.RestoreRecordUseCase
import io.celox.flipperripper.domain.usecase.ResumeDownloadUseCase
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
            pauseDownload = PauseDownloadUseCase(repo),
            resumeDownload = ResumeDownloadUseCase(repo),
            reorderQueue = ReorderQueueUseCase(repo),
            retryDownload = RetryDownloadUseCase(repo),
            deleteRecord = DeleteRecordUseCase(repo),
            restoreRecord = RestoreRecordUseCase(repo),
            clearHistory = ClearHistoryUseCase(repo),
            instagramSession = io.celox.flipperripper.data.engine.InstagramSession { false },
        )

    @Test
    fun `history reflects repository`() =
        runTest {
            val vm = createViewModel()
            vm.history.test {
                assertThat(awaitItem()).isNull()
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
    fun `history starts as not-loaded, never as empty`() =
        runTest {
            // The screen draws its empty state from an empty list. Starting there meant a user who
            // had just shared a link was told "no downloads yet" before the card arrived.
            val vm = createViewModel()
            assertThat(vm.history.value).isNull()
        }

    @Test
    fun `running record stays observable`() =
        runTest {
            repo.history.value = listOf(sampleRecord(id = "x", status = DownloadStatus.RUNNING))
            val vm = createViewModel()
            vm.history.test {
                var list = awaitItem()
                while (list.isNullOrEmpty()) list = awaitItem() // skip the "not loaded yet" value
                assertThat(list!!.first().status).isEqualTo(DownloadStatus.RUNNING)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a deleted finished entry can be put back exactly as it was`() =
        runTest {
            val done = sampleRecord(id = "a", status = DownloadStatus.COMPLETED)
            repo.history.value = listOf(done)
            val vm = createViewModel()
            vm.delete("a")
            advanceUntilIdle()
            val removed = vm.removed.value
            assertThat(removed?.record).isEqualTo(done)
            vm.undoDelete(removed!!.token)
            advanceUntilIdle()
            assertThat(repo.restored).containsExactly(done)
            assertThat(repo.history.value).containsExactly(done)
            assertThat(vm.removed.value).isNull()
        }

    @Test
    fun `a pending download offers no undo, because deleting it cancelled it`() =
        runTest {
            repo.history.value = listOf(sampleRecord(id = "q", status = DownloadStatus.QUEUED))
            val vm = createViewModel()
            vm.delete("q")
            advanceUntilIdle()
            assertThat(vm.removed.value).isNull()
        }

    @Test
    fun `undo with a stale token restores nothing`() =
        runTest {
            repo.history.value = listOf(sampleRecord(id = "a"), sampleRecord(id = "b"))
            val vm = createViewModel()
            vm.delete("a")
            advanceUntilIdle()
            val first = vm.removed.value!!.token
            vm.delete("b")
            advanceUntilIdle()
            vm.undoDelete(first)
            advanceUntilIdle()
            assertThat(repo.restored).isEmpty()
            assertThat(vm.removed.value?.record?.id).isEqualTo("b")
        }

    @Test
    fun `an expired snackbar lets the delete stand`() =
        runTest {
            repo.history.value = listOf(sampleRecord(id = "a"))
            val vm = createViewModel()
            vm.delete("a")
            advanceUntilIdle()
            vm.undoExpired(vm.removed.value!!.token)
            assertThat(vm.removed.value).isNull()
            assertThat(repo.restored).isEmpty()
        }
}
