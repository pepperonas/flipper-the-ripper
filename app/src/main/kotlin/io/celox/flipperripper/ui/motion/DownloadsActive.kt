package io.celox.flipperripper.ui.motion

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether a download is currently queued or running, provided once at the app root.
 *
 * Continuous motion lives in small leaf components (shape motifs) scattered across several screens and
 * inside list items. Threading a flag through every call site would touch a lot of unrelated signatures,
 * so the state is published here and read where the motion actually happens. Defaults to `false`, which
 * means "no looping motion" — the safe answer for previews and tests.
 */
val LocalDownloadsActive = compositionLocalOf { false }
