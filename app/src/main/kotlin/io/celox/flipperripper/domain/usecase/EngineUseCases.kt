package io.celox.flipperripper.domain.usecase

import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.repository.EngineRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Idempotently initialise the yt-dlp engine. */
class InitializeEngineUseCase
@Inject
constructor(private val repository: EngineRepository) {
    suspend operator fun invoke(): EngineResult<Unit> = repository.ensureInitialized()
}

/** Observe whether the engine is ready. */
class ObserveEngineReadyUseCase
@Inject
constructor(private val repository: EngineRepository) {
    operator fun invoke(): StateFlow<Boolean> = repository.isReady
}

/** Update the bundled yt-dlp binary. */
class UpdateEngineUseCase
@Inject
constructor(private val repository: EngineRepository) {
    suspend operator fun invoke(): EngineResult<String> = repository.updateEngine()
}
