package io.celox.flipperripper.domain.model

/**
 * What came of asking the engine to update itself.
 *
 * The bundled library answers with an enum whose names used to be shown to people verbatim — the
 * Settings screen said "Engine updated: ALREADY_UP_TO_DATE", which is a machine talking. This type
 * turns the raw answer into the two things a person actually wants to know, so the wording lives in
 * string resources where it can be read and translated.
 */
sealed interface EngineUpdateOutcome {
    /** A newer yt-dlp was fetched and installed. */
    data object Updated : EngineUpdateOutcome

    /** The engine was already current — nothing to do, and that is good news, not a failure. */
    data object AlreadyCurrent : EngineUpdateOutcome

    /**
     * The library reported something this app does not know. Carries [raw] so the screen can still
     * say *something* truthful instead of swallowing it — a new library version may add states.
     */
    data class Unrecognised(val raw: String) : EngineUpdateOutcome

    companion object {
        /** The library's `UpdateStatus.DONE`: an actual version change. */
        const val DONE = "DONE"

        /** `UpdateStatus.ALREADY_UP_TO_DATE`, plus the engine's own fallback when it reports nothing. */
        private val UNCHANGED = setOf("ALREADY_UP_TO_DATE", "UP_TO_DATE")

        /**
         * Read the library's status name. Case and surrounding whitespace are ignored: this crosses a
         * library boundary, and a status that arrives as "done" should not become an unknown state.
         */
        fun parse(raw: String): EngineUpdateOutcome {
            val normalised = raw.trim().uppercase()
            return when {
                normalised == DONE -> Updated
                normalised in UNCHANGED -> AlreadyCurrent
                else -> Unrecognised(raw.trim())
            }
        }
    }
}
