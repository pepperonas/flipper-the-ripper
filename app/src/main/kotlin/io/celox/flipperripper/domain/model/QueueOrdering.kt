package io.celox.flipperripper.domain.model

/**
 * The order the queue actually runs in.
 *
 * Until 1.10.0 "queued" was a label with nothing behind it: every download was its own WorkManager
 * job and they all ran at once, so what started next was whatever the scheduler felt like. One
 * runner now drains [DownloadRecord.queueOrder] ascending, and these are the rules it follows.
 *
 * Pure on purpose — no database, no Android — so the awkward cases (an empty queue, a drag that
 * lands outside the list, a paused item that must keep its place) are decided by tests rather than
 * by what the UI happens to hand over.
 */
object QueueOrdering {
    /**
     * The next download to run: the lowest [DownloadRecord.queueOrder] among those merely waiting.
     *
     * Ties break on [DownloadRecord.id] so the answer is stable — two rows created in the same
     * millisecond must not swap places between two calls, or the runner could bounce between them.
     */
    fun next(records: List<DownloadRecord>): DownloadRecord? =
        records
            .filter { it.status == DownloadStatus.QUEUED }
            .minWithOrNull(compareBy({ it.queueOrder }, { it.id }))

    /** The order value a newly enqueued download gets: behind everything already known. */
    fun nextOrder(existing: List<Long>): Long = (existing.maxOrNull() ?: 0L) + 1L

    /**
     * Assigns positions for a drag, by **permuting the values the affected rows already hold**.
     *
     * Renumbering to 0, 1, 2… would have been simpler and wrong: a queue seeded from creation
     * timestamps (hundreds of trillions) sits on a different scale than a freshly renumbered one,
     * and the first new download after a drag — which takes `max + 1` — would leap to the front of
     * everything that had been renumbered. Redistributing the existing values keeps every row's
     * relation to rows that were not dragged.
     *
     * Ids not present in [current] are ignored rather than invented, and ids in [current] that the
     * caller left out keep their value untouched.
     */
    fun reorder(newOrder: List<String>, current: Map<String, Long>): Map<String, Long> {
        val affected = newOrder.filter { current.containsKey(it) }.distinct()
        if (affected.isEmpty()) return emptyMap()
        val slots = affected.mapNotNull { current[it] }.sorted()
        return affected.withIndex().associate { (index, id) -> id to slots[index] }
    }

    /**
     * Where a drag from [from] to [to] actually lands, clamped into the list.
     *
     * A drag can report an index past either end (a fling, or a list that shrank under the finger
     * while a download finished). Clamping here means the UI cannot hand the repository a position
     * that does not exist.
     */
    fun move(ids: List<String>, from: Int, to: Int): List<String> {
        if (ids.isEmpty()) return ids
        val source = from.coerceIn(ids.indices)
        val target = to.coerceIn(ids.indices)
        if (source == target) return ids
        return ids.toMutableList().apply { add(target, removeAt(source)) }
    }
}
