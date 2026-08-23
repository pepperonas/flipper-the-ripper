package io.celox.flipperripper.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delivers text shared into the app (ACTION_SEND) from the Activity to the Home ViewModel.
 *
 * A conflated [Channel] instead of a replaying SharedFlow: a link posted during a cold start is
 * buffered until the collector attaches, but each link is consumed exactly **once**. The previous
 * `SharedFlow(replay = 1)` re-delivered the last link to every new collector — an Activity or
 * ViewModel recreation replayed a share that was already downloading and enqueued it a second time.
 */
@Singleton
class IncomingLinkBus
@Inject
constructor() {
    private val channel = Channel<String>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val links: Flow<String> = channel.receiveAsFlow()

    fun post(text: String) {
        channel.trySend(text)
    }
}
