package io.celox.flipperripper.domain.util

import io.celox.flipperripper.domain.model.AppUpdate

/**
 * Compares release version strings like `1.3.1`, `v1.3.2` or `1.4` (an optional leading `v` and any
 * non-numeric suffix are ignored). Pure so the "is the GitHub release newer than the installed
 * build?" decision is unit-testable.
 */
object AppVersions {
    /** True when [candidate] denotes a strictly newer version than [installed]. */
    fun isNewer(installed: String, candidate: String): Boolean {
        val a = parts(installed)
        val b = parts(candidate)
        if (a.isEmpty() || b.isEmpty()) return false
        val len = maxOf(a.size, b.size)
        for (i in 0 until len) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (y != x) return y > x
        }
        return false
    }

    /**
     * The update to surface to the user, or null: [known] must be strictly newer than the
     * [installed] build, and newer than any version the user already [dismissed] (dismissing v1.4
     * must not hide a later v1.5).
     */
    fun visibleUpdate(installed: String, known: AppUpdate?, dismissed: String?): AppUpdate? =
        known?.takeIf { update ->
            isNewer(installed, update.version) &&
                (dismissed == null || isNewer(dismissed, update.version))
        }

    private fun parts(version: String): List<Int> =
        version
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            // "1.3.2-beta1 (42)" → numeric core "1.3.2" only; a malformed segment aborts cleanly.
            .takeWhile { it.isDigit() || it == '.' }
            .split('.')
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }
}
