package io.celox.flipperripper.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QualityPreferenceTest {
    @Test
    fun `an old audio default is read forward as audio only`() {
        // Somebody who chose Audio before 1.10 has an opinion on record; ignoring the old key would
        // quietly give them video back.
        assertThat(QualityPreference.read(storedQuality = null, storedMode = "AUDIO"))
            .isEqualTo(QualityChoice.AUDIO_ONLY)
    }

    @Test
    fun `an old video default means no opinion about resolution`() {
        assertThat(QualityPreference.read(storedQuality = null, storedMode = "VIDEO"))
            .isEqualTo(QualityChoice.BEST)
    }

    @Test
    fun `a stored quality wins over the legacy key`() {
        assertThat(QualityPreference.read(storedQuality = "P720", storedMode = "AUDIO"))
            .isEqualTo(QualityChoice.P720)
    }

    @Test
    fun `nothing stored is best`() {
        assertThat(QualityPreference.read(null, null)).isEqualTo(QualityChoice.BEST)
    }

    @Test
    fun `a value from a newer version falls back instead of throwing`() {
        // A downgrade, or a tier this build does not know. Crashing on read would lock the user out
        // of their own settings screen.
        assertThat(QualityPreference.read(storedQuality = "P4320", storedMode = null))
            .isEqualTo(QualityChoice.BEST)
    }

    @Test
    fun `an unknown value does not silently fall through to the legacy key`() {
        // Reading "P4320" then quietly answering AUDIO_ONLY because an old mode happened to be
        // stored would turn one unreadable value into a completely different decision.
        assertThat(QualityPreference.read(storedQuality = "P4320", storedMode = "AUDIO"))
            .isEqualTo(QualityChoice.BEST)
    }
}
