package io.celox.flipperripper.domain.model

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.data.engine.StaleEngineRetry
import org.junit.Test

class EngineUpdateOutcomeTest {
    @Test
    fun `a real version change reads as updated`() {
        assertThat(EngineUpdateOutcome.parse("DONE")).isEqualTo(EngineUpdateOutcome.Updated)
    }

    @Test
    fun `both ways of saying nothing changed mean the same thing`() {
        // The library says ALREADY_UP_TO_DATE; the engine falls back to UP_TO_DATE when the library
        // reports nothing at all. Neither is a failure.
        listOf("ALREADY_UP_TO_DATE", "UP_TO_DATE").forEach {
            assertThat(EngineUpdateOutcome.parse(it)).isEqualTo(EngineUpdateOutcome.AlreadyCurrent)
        }
    }

    @Test
    fun `case and whitespace crossing the library boundary do not create an unknown state`() {
        listOf("done", " DONE ", "Done").forEach {
            assertThat(EngineUpdateOutcome.parse(it)).isEqualTo(EngineUpdateOutcome.Updated)
        }
        assertThat(EngineUpdateOutcome.parse(" already_up_to_date "))
            .isEqualTo(EngineUpdateOutcome.AlreadyCurrent)
    }

    @Test
    fun `an unknown status is carried through, not swallowed`() {
        // A future library version may add states; the screen should still be able to say something
        // truthful rather than silently claiming success.
        val outcome = EngineUpdateOutcome.parse("  SOME_NEW_STATE  ")
        assertThat(outcome).isEqualTo(EngineUpdateOutcome.Unrecognised("SOME_NEW_STATE"))
    }

    @Test
    fun `the retry rule and the wording agree on what counts as an update`() {
        // StaleEngineRetry only retries after a real version change. If these two ever disagreed, the
        // app would tell someone it updated while refusing to retry on that basis (or the reverse).
        assertThat(StaleEngineRetry.updateJustifiesRetry(EngineUpdateOutcome.DONE)).isTrue()
        assertThat(EngineUpdateOutcome.parse(EngineUpdateOutcome.DONE)).isEqualTo(EngineUpdateOutcome.Updated)

        listOf("ALREADY_UP_TO_DATE", "UP_TO_DATE").forEach {
            assertThat(StaleEngineRetry.updateJustifiesRetry(it)).isFalse()
            assertThat(EngineUpdateOutcome.parse(it)).isEqualTo(EngineUpdateOutcome.AlreadyCurrent)
        }
    }
}
