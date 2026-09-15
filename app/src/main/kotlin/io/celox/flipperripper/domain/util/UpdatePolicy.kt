package io.celox.flipperripper.domain.util

/**
 * Whether the releases published on GitHub can be installed on this device at all.
 *
 * From 1.9.0 every release is a single 64-bit ARM build; the 32-bit variant was dropped. A device
 * without that ABI would still be told "update available" on every start and could never act on it
 * — a permanent, useless nag. So on such a device the check is skipped and 1.8.3 simply remains the
 * last version. Pure, so the rule is testable without a device.
 */
object UpdatePolicy {
    /** The one ABI the releases are built for. */
    const val RELEASE_ABI = "arm64-v8a"

    /** True when a device reporting [supportedAbis] (in preference order) can install a release. */
    fun releasesInstallOn(supportedAbis: List<String>): Boolean = RELEASE_ABI in supportedAbis
}
