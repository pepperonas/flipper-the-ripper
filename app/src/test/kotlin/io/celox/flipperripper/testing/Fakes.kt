package io.celox.flipperripper.testing

import io.celox.flipperripper.data.engine.DownloadSpec
import io.celox.flipperripper.data.engine.DownloadedFile
import io.celox.flipperripper.data.engine.YtDlpEngine
import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadProgress
import io.celox.flipperripper.domain.model.DownloadRecord
import io.celox.flipperripper.domain.model.DownloadRequest
import io.celox.flipperripper.domain.model.DownloadStatus
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.domain.model.ThemeMode
import io.celox.flipperripper.domain.model.UserPreferences
import io.celox.flipperripper.domain.model.VideoInfo
import io.celox.flipperripper.domain.repository.ClipboardRepository
import io.celox.flipperripper.domain.repository.DownloadRepository
import io.celox.flipperripper.domain.repository.EngineRepository
import io.celox.flipperripper.domain.repository.SettingsRepository
import io.celox.flipperripper.domain.repository.VideoRepository
import io.celox.flipperripper.domain.util.ParsedUrl
import io.celox.flipperripper.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** Deterministic id generator for tests. */
class FakeIdGenerator(private val ids: MutableList<String> = mutableListOf("id-1", "id-2", "id-3")) : IdGenerator {
    var counter = 0
    override fun newId(): String = ids.getOrElse(counter++) { "id-$counter" }
}

class FakeEngineRepository(
    ready: Boolean = true,
    var initResult: EngineResult<Unit> = EngineResult.Success(Unit),
    var updateResult: EngineResult<String> = EngineResult.Success("DONE"),
) : EngineRepository {
    private val _isReady = MutableStateFlow(ready)
    override val isReady: StateFlow<Boolean> = _isReady
    var ensureCalls = 0

    fun setReady(value: Boolean) {
        _isReady.value = value
    }

    override suspend fun ensureInitialized(): EngineResult<Unit> {
        ensureCalls++
        return initResult
    }

    override suspend fun updateEngine(): EngineResult<String> = updateResult
}

class FakeVideoRepository(
    var result: EngineResult<VideoInfo> = EngineResult.Success(
        VideoInfo(
            sourceUrl = "https://youtu.be/x",
            platform = Platform.YOUTUBE,
            title = "Sample title",
            uploader = "Uploader",
            thumbnailUrl = "https://img/thumb.jpg",
            durationSeconds = 42,
            id = "x",
        ),
    ),
) : VideoRepository {
    var lastUrl: String? = null
    override suspend fun resolve(url: String, mode: DownloadMode): EngineResult<VideoInfo> {
        lastUrl = url
        return result
    }
}

class FakeDownloadRepository : DownloadRepository {
    val history = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val enqueued = mutableListOf<DownloadRequest>()
    val cancelled = mutableListOf<String>()
    val retried = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var cleared = false
    var nextId = "record-1"

    override suspend fun enqueue(request: DownloadRequest): String {
        enqueued += request
        return nextId
    }

    override fun observeHistory(): Flow<List<DownloadRecord>> = history

    override fun observeRecord(id: String): Flow<DownloadRecord?> =
        history.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun cancel(id: String) {
        cancelled += id
    }

    override suspend fun retry(id: String) {
        retried += id
    }

    override suspend fun delete(id: String) {
        deleted += id
    }

    override suspend fun clearHistory() {
        cleared = true
    }
}

class FakeSettingsRepository(initial: UserPreferences = UserPreferences()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val preferences: Flow<UserPreferences> = state

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        state.value = state.value.copy(useDynamicColor = enabled)
    }

    override suspend fun setAutoDownloadOnShare(enabled: Boolean) {
        state.value = state.value.copy(autoDownloadOnShare = enabled)
    }

    override suspend fun setDefaultMode(mode: DownloadMode) {
        state.value = state.value.copy(defaultMode = mode)
    }

    override suspend fun setClipboardDetection(enabled: Boolean) {
        state.value = state.value.copy(clipboardDetection = enabled)
    }

    val lastUpdate = MutableStateFlow(0L)
    override val lastEngineUpdateMs: Flow<Long> = lastUpdate

    override suspend fun setLastEngineUpdateMs(epochMs: Long) {
        lastUpdate.value = epochMs
    }
}

class FakeClipboardRepository(var suggestion: ParsedUrl? = null) : ClipboardRepository {
    override suspend fun peekSupportedUrl(): ParsedUrl? = suggestion
}

class FakeBackendConfigRepository(
    initial: io.celox.flipperripper.domain.model.BackendConfig =
        io.celox.flipperripper.domain.model.BackendConfig(
            io.celox.flipperripper.domain.model.DownloadSource.ON_DEVICE,
            "",
            "",
        ),
) : io.celox.flipperripper.domain.repository.BackendConfigRepository {
    val state = MutableStateFlow(initial)
    override val config: Flow<io.celox.flipperripper.domain.model.BackendConfig> = state

    override suspend fun setSource(source: io.celox.flipperripper.domain.model.DownloadSource) {
        state.value = state.value.copy(source = source)
    }

    override suspend fun setServer(url: String, apiKey: String) {
        state.value = state.value.copy(url = url, apiKey = apiKey)
    }
}

/** A fake engine (data-layer seam) for repository/worker-style tests. */
class FakeYtDlpEngine(
    ready: Boolean = true,
    var infoResult: EngineResult<VideoInfo> = EngineResult.Failure(DownloadError.Unknown("no info")),
    var downloadResult: EngineResult<DownloadedFile> = EngineResult.Failure(DownloadError.Unknown("no file")),
) : YtDlpEngine {
    private val _isReady = MutableStateFlow(ready)
    override val isReady: StateFlow<Boolean> = _isReady
    val cancelled = mutableListOf<String>()
    var emittedProgress: List<Float> = listOf(10f, 55f, 100f)

    override suspend fun ensureInitialized(): EngineResult<Unit> = EngineResult.Success(Unit)

    override suspend fun fetchInfo(url: String, mode: DownloadMode): EngineResult<VideoInfo> = infoResult

    override suspend fun download(
        spec: DownloadSpec,
        onProgress: (DownloadProgress) -> Unit,
    ): EngineResult<DownloadedFile> {
        emittedProgress.forEach { onProgress(DownloadProgress(it, null, "line $it")) }
        return downloadResult
    }

    override suspend fun cancel(processId: String) {
        cancelled += processId
    }

    override suspend fun update(): EngineResult<String> = EngineResult.Success("DONE")
}

fun sampleRecord(
    id: String = "record-1",
    status: DownloadStatus = DownloadStatus.COMPLETED,
): DownloadRecord =
    DownloadRecord(
        id = id,
        sourceUrl = "https://youtu.be/x",
        platform = Platform.YOUTUBE,
        title = "Sample title",
        mode = DownloadMode.VIDEO,
        thumbnailUrl = null,
        status = status,
        mediaUri = "content://media/external/video/media/1",
        fileName = "Sample title.mp4",
        sizeBytes = 2_500_000,
        errorKind = null,
        errorMessage = null,
        createdAtEpochMs = 1_000,
        updatedAtEpochMs = 2_000,
    )
