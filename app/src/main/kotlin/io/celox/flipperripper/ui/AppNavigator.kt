package io.celox.flipperripper.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/** App-level navigation requests, independent of which screen is currently composed. */
enum class AppNavTarget { HOME, HISTORY }

/**
 * Routes navigation triggered by background flows (a shared link auto-starting a download) to the
 * root NavHost.
 *
 * The old wiring navigated from inside `HomeScreen`'s event collector — which only runs while Home
 * is composed. A link shared while the app sat on the Settings or History tab started the download
 * invisibly, and the buffered "download started" event then fired a surprise navigation the next
 * time Home was opened. The user-visible symptom: "I shared a link and had to navigate around
 * before I could see the download running."
 *
 * `FlipperApp` (always composed) is the single consumer; a conflated buffer keeps an event posted
 * during a cold start until the collector attaches, and each event navigates exactly once.
 */
@Singleton
class AppNavigator
@Inject
constructor() {
    private val channel = Channel<AppNavTarget>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: Flow<AppNavTarget> = channel.receiveAsFlow()

    fun navigateTo(target: AppNavTarget) {
        channel.trySend(target)
    }
}
