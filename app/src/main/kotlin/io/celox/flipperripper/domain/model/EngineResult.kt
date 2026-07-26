package io.celox.flipperripper.domain.model

/** A domain-level result carrying a typed [DownloadError] on failure. */
sealed interface EngineResult<out T> {
    data class Success<T>(val value: T) : EngineResult<T>

    data class Failure(val error: DownloadError) : EngineResult<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value
}

fun <T, R> EngineResult<T>.map(transform: (T) -> R): EngineResult<R> =
    when (this) {
        is EngineResult.Success -> EngineResult.Success(transform(value))
        is EngineResult.Failure -> this
    }

inline fun <T> EngineResult<T>.onSuccess(action: (T) -> Unit): EngineResult<T> {
    if (this is EngineResult.Success) action(value)
    return this
}

inline fun <T> EngineResult<T>.onFailure(action: (DownloadError) -> Unit): EngineResult<T> {
    if (this is EngineResult.Failure) action(error)
    return this
}
