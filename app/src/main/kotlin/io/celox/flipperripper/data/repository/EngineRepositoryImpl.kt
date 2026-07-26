package io.celox.flipperripper.data.repository

import io.celox.flipperripper.data.engine.YtDlpEngine
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.repository.EngineRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class EngineRepositoryImpl
@Inject
constructor(private val engine: YtDlpEngine) : EngineRepository {
    override val isReady: StateFlow<Boolean> = engine.isReady

    override suspend fun ensureInitialized(): EngineResult<Unit> = engine.ensureInitialized()

    override suspend fun updateEngine(): EngineResult<String> = engine.update()
}
