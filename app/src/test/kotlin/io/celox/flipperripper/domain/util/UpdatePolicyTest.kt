package io.celox.flipperripper.domain.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Releases ship for one ABI since 1.9.0. A device that cannot install them must not be nagged
 * about them — that nag would be permanent and could never be satisfied.
 */
class UpdatePolicyTest {
    @Test
    fun `a modern phone can install releases`() {
        // Android lists ABIs in preference order; a 64-bit phone leads with arm64-v8a.
        assertThat(UpdatePolicy.releasesInstallOn(listOf("arm64-v8a", "armeabi-v7a", "armeabi"))).isTrue()
    }

    @Test
    fun `a 64-bit-only device can too`() {
        // Pixel 7+ on Android 15 no longer lists any 32-bit ABI.
        assertThat(UpdatePolicy.releasesInstallOn(listOf("arm64-v8a"))).isTrue()
    }

    @Test
    fun `a 32-bit-only device is left alone`() {
        // The 1.8.3 audience that stays on 1.8.3: no notice, no fetch.
        assertThat(UpdatePolicy.releasesInstallOn(listOf("armeabi-v7a", "armeabi"))).isFalse()
    }

    @Test
    fun `an x86 device is left alone as well`() {
        // yt-dlp's native libraries ship for ARM only; there is no release such a device could run.
        assertThat(UpdatePolicy.releasesInstallOn(listOf("x86_64", "x86"))).isFalse()
        assertThat(UpdatePolicy.releasesInstallOn(emptyList())).isFalse()
    }

    @Test
    fun `the ABI the policy names is the one the build produces`() {
        // Property, not prose: if the split in build.gradle.kts moves to another ABI, this fails.
        val gradle = java.io.File("build.gradle.kts").readText()
        val included = Regex("""include\(([^)]*)\)""").find(gradle)!!.groupValues[1]
        assertThat(included).contains("\"${UpdatePolicy.RELEASE_ABI}\"")
    }
}
