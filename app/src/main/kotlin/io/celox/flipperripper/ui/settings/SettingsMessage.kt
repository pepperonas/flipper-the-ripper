package io.celox.flipperripper.ui.settings

import io.celox.flipperripper.domain.model.EngineUpdateOutcome

/**
 * Something the Settings screen should say in a snackbar.
 *
 * Deliberately not a plain `String` for every case: the engine-update result is a *state*, and the
 * words for it belong in string resources, not in a ViewModel that has no business holding a Context.
 * Errors stay plain text because the error taxonomy already phrases them for a person to read.
 */
sealed interface SettingsMessage {
    /** Text that is already human-readable — engine errors phrase themselves. */
    data class Plain(val text: String) : SettingsMessage

    /** How an engine update turned out; the screen picks the wording. */
    data class EngineUpdate(val outcome: EngineUpdateOutcome) : SettingsMessage
}
