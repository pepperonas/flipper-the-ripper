package io.celox.flipperripper.domain.repository

import io.celox.flipperripper.domain.model.EngineResult
import kotlinx.coroutines.flow.StateFlow

/** Lifecycle of the yt-dlp engine (one-time native init, optional self-update). */
interface EngineRepository {
    /** Emits true once the engine's native payload is unpacked and usable. */
    val isReady: StateFlow<Boolean>

    /** Idempotently initialise the engine. Safe to call repeatedly. */
    suspend fun ensureInitialized(): EngineResult<Unit>

    /** Update the bundled yt-dlp to the latest release (requires network). */
    suspend fun updateEngine(): EngineResult<String>
}
