package io.celox.flipperripper.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delivers text shared into the app (ACTION_SEND) from the Activity to the Home ViewModel.
 * Replay = 1 so a share that arrives during a cold start is not lost before the collector attaches.
 */
@Singleton
class IncomingLinkBus
@Inject
constructor() {
    private val _links = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)
    val links: SharedFlow<String> = _links.asSharedFlow()

    fun post(text: String) {
        _links.tryEmit(text)
    }
}
