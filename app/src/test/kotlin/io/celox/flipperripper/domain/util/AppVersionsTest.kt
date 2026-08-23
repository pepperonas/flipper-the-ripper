package io.celox.flipperripper.domain.util

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.AppUpdate
import org.junit.Test

class AppVersionsTest {
    @Test
    fun `newer patch, minor and major versions are detected`() {
        assertThat(AppVersions.isNewer("1.3.1", "1.3.2")).isTrue()
        assertThat(AppVersions.isNewer("1.3.1", "1.4.0")).isTrue()
        assertThat(AppVersions.isNewer("1.3.1", "2.0.0")).isTrue()
    }

    @Test
    fun `equal and older versions are not updates`() {
        assertThat(AppVersions.isNewer("1.3.1", "1.3.1")).isFalse()
        assertThat(AppVersions.isNewer("1.3.1", "1.3.0")).isFalse()
        assertThat(AppVersions.isNewer("2.0.0", "1.9.9")).isFalse()
    }

    @Test
    fun `the release tag's v prefix is ignored`() {
        assertThat(AppVersions.isNewer("1.3.1", "v1.3.2")).isTrue()
        assertThat(AppVersions.isNewer("v1.3.1", "1.3.1")).isFalse()
    }

    @Test
    fun `a shorter version is padded, not truncated`() {
        // 1.4 == 1.4.0 and 1.4 > 1.3.9 — numeric comparison, not string comparison.
        assertThat(AppVersions.isNewer("1.3.9", "1.4")).isTrue()
        assertThat(AppVersions.isNewer("1.4", "1.4.0")).isFalse()
        assertThat(AppVersions.isNewer("1.4", "1.4.1")).isTrue()
    }

    @Test
    fun `two-digit segments compare numerically`() {
        assertThat(AppVersions.isNewer("1.9.0", "1.10.0")).isTrue()
    }

    @Test
    fun `suffixes after the numeric core are ignored`() {
        assertThat(AppVersions.isNewer("1.3.1", "1.3.2-beta")).isTrue()
        assertThat(AppVersions.isNewer("1.3.2 (19)", "1.3.2")).isFalse()
    }

    @Test
    fun `garbage never counts as an update`() {
        assertThat(AppVersions.isNewer("1.3.1", "")).isFalse()
        assertThat(AppVersions.isNewer("1.3.1", "latest")).isFalse()
        assertThat(AppVersions.isNewer("", "1.3.2")).isFalse()
    }

    @Test
    fun `visibleUpdate requires the release to beat the installed build`() {
        val update = AppUpdate("v1.4.0", "https://example.com")
        assertThat(AppVersions.visibleUpdate("1.3.1", update, dismissed = null)).isEqualTo(update)
        assertThat(AppVersions.visibleUpdate("1.4.0", update, dismissed = null)).isNull()
        assertThat(AppVersions.visibleUpdate("1.3.1", null, dismissed = null)).isNull()
    }

    @Test
    fun `a dismissed version stays hidden but anything newer re-appears`() {
        val dismissedUpdate = AppUpdate("v1.4.0", "https://example.com")
        assertThat(AppVersions.visibleUpdate("1.3.1", dismissedUpdate, dismissed = "v1.4.0")).isNull()
        val newer = AppUpdate("v1.5.0", "https://example.com")
        assertThat(AppVersions.visibleUpdate("1.3.1", newer, dismissed = "v1.4.0")).isEqualTo(newer)
    }
}
