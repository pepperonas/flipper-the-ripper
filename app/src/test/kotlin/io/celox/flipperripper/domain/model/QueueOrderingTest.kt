package io.celox.flipperripper.domain.model

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.testing.sampleRecord
import org.junit.Test

class QueueOrderingTest {
    private fun record(id: String, order: Long, status: DownloadStatus = DownloadStatus.QUEUED) =
        sampleRecord(id = id, status = status, queueOrder = order)

    @Test
    fun `next is the lowest queue order among the waiting`() {
        val next =
            QueueOrdering.next(
                listOf(record("c", 30), record("a", 10), record("b", 20)),
            )
        assertThat(next?.id).isEqualTo("a")
    }

    @Test
    fun `next skips everything that is not waiting`() {
        // Paused holds its place in the list but must not be picked up — that is the whole
        // difference between pausing and doing nothing.
        val next =
            QueueOrdering.next(
                listOf(
                    record("paused", 1, DownloadStatus.PAUSED),
                    record("running", 2, DownloadStatus.RUNNING),
                    record("done", 3, DownloadStatus.COMPLETED),
                    record("waiting", 4),
                ),
            )
        assertThat(next?.id).isEqualTo("waiting")
    }

    @Test
    fun `next is null when nothing is waiting`() {
        assertThat(QueueOrdering.next(emptyList())).isNull()
        assertThat(QueueOrdering.next(listOf(record("x", 1, DownloadStatus.PAUSED)))).isNull()
    }

    @Test
    fun `next breaks ties on id so two calls agree`() {
        // Two rows created in the same millisecond would otherwise be ordered by whatever the list
        // happened to contain, and the runner could bounce between them.
        val records = listOf(record("b", 5), record("a", 5))
        assertThat(QueueOrdering.next(records)?.id).isEqualTo("a")
        assertThat(QueueOrdering.next(records.reversed())?.id).isEqualTo("a")
    }

    @Test
    fun `a new download goes behind everything known`() {
        assertThat(QueueOrdering.nextOrder(listOf(3L, 9L, 5L))).isEqualTo(10L)
        assertThat(QueueOrdering.nextOrder(emptyList())).isEqualTo(1L)
    }

    @Test
    fun `reorder redistributes the values the rows already hold`() {
        val current = mapOf("a" to 10L, "b" to 20L, "c" to 30L)
        val result = QueueOrdering.reorder(listOf("c", "a", "b"), current)
        // The set of positions is unchanged; only who sits on which one.
        assertThat(result).isEqualTo(mapOf("c" to 10L, "a" to 20L, "b" to 30L))
    }

    @Test
    fun `reorder never invents a scale of its own`() {
        // Renumbering to 0,1,2 would put these rows on a different scale than a queue seeded from
        // creation timestamps, and the next enqueued download (max + 1) would leap past them.
        val current = mapOf("a" to 1_700_000_000_000L, "b" to 1_700_000_000_001L)
        val result = QueueOrdering.reorder(listOf("b", "a"), current)
        assertThat(result.values.sorted()).isEqualTo(current.values.sorted())
    }

    @Test
    fun `reorder ignores ids it does not know and leaves the rest alone`() {
        val current = mapOf("a" to 10L, "b" to 20L)
        val result = QueueOrdering.reorder(listOf("ghost", "b", "a"), current)
        assertThat(result.keys).containsExactly("a", "b")
        assertThat(result).isEqualTo(mapOf("b" to 10L, "a" to 20L))
    }

    @Test
    fun `reorder of nothing changes nothing`() {
        assertThat(QueueOrdering.reorder(emptyList(), mapOf("a" to 1L))).isEmpty()
        assertThat(QueueOrdering.reorder(listOf("a"), emptyMap())).isEmpty()
    }

    @Test
    fun `move puts the dragged item where it was dropped`() {
        assertThat(QueueOrdering.move(listOf("a", "b", "c"), 0, 2)).containsExactly("b", "c", "a").inOrder()
        assertThat(QueueOrdering.move(listOf("a", "b", "c"), 2, 0)).containsExactly("c", "a", "b").inOrder()
    }

    @Test
    fun `move clamps a drag that ran off the end`() {
        // A fling, or a list that shrank under the finger because a download finished.
        assertThat(QueueOrdering.move(listOf("a", "b", "c"), 0, 99)).containsExactly("b", "c", "a").inOrder()
        assertThat(QueueOrdering.move(listOf("a", "b", "c"), -5, 1)).containsExactly("b", "a", "c").inOrder()
        assertThat(QueueOrdering.move(emptyList(), 0, 1)).isEmpty()
    }

    @Test
    fun `move to the same place is not a change`() {
        val ids = listOf("a", "b", "c")
        assertThat(QueueOrdering.move(ids, 1, 1)).isSameInstanceAs(ids)
    }
}
