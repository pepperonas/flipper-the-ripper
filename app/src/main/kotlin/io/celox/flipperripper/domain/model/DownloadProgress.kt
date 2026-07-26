package io.celox.flipperripper.domain.model

/**
 * In-flight progress emitted by the engine while a download runs.
 *
 * @param percent 0..100, or null when the engine cannot determine a total.
 * @param etaSeconds estimated seconds remaining, when known.
 * @param line the most recent raw status line from the engine (useful for a "details" view).
 */
data class DownloadProgress(val percent: Float?, val etaSeconds: Long?, val line: String?)
