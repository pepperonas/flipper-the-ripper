package io.celox.flipperripper.domain.model

/**
 * What to extract. Mirrors inspector-rust's `DlMode` (`video` | `audio`).
 * YouTube offers both; other platforms are video-only by UI convention, though the engine
 * accepts either mode for any source.
 */
enum class DownloadMode { VIDEO, AUDIO }
