package io.celox.flipperripper.release

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * The ABI the build produces, the file the release workflow publishes and the file the README tells
 * people to download are three statements of one fact, in three files nobody edits together. When
 * the 32-bit build was dropped in 1.9.0 all three had to move; this is what makes a future move of
 * any one of them a red test instead of a release that ships the wrong file or documents a file that
 * does not exist.
 */
class ReleaseArtifactsTest {
    private val gradle = File("build.gradle.kts").readText()
    private val workflow = File("../.github/workflows/release.yml").readText()
    private val readme = File("../README.md").readText()

    /** The ABIs the split actually builds. */
    private fun builtAbis(): Set<String> {
        val include = Regex("""include\(([^)]*)\)""").find(gradle)!!.groupValues[1]
        return Regex(""""([^"]+)"""").findAll(include).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun `exactly one ABI is built, so there is nothing for a user to choose`() {
        // The whole point of 1.9.0. A second ABI means a second file, and a second file means the
        // wrong one gets installed — that is the measured history, not a guess.
        assertThat(builtAbis()).containsExactly("arm64-v8a")
    }

    @Test
    fun `the workflow copies the APK the build produces`() {
        // The split names its output app-<abi>-release.apk; the workflow must pick up that name.
        builtAbis().forEach { abi ->
            assertWithMessage("workflow references app-$abi-release.apk")
                .that(workflow)
                .contains("app-$abi-release.apk")
        }
    }

    @Test
    fun `the workflow publishes no ABI it does not build`() {
        // A leftover reference to a dropped ABI would fail the job on a missing file — or, worse,
        // silently publish a stale artefact if one were lying around.
        val referenced =
            Regex("""app-([a-z0-9_-]+)-release\.apk""").findAll(workflow).map { it.groupValues[1] }.toSet()
        assertThat(referenced).isEqualTo(builtAbis())
    }

    /** The line that names the published file — the one place the name is decided. */
    private fun publishedName(): String =
        Regex("""APK="([^"]+)"""").find(workflow)?.groupValues?.get(1)
            ?: error("release.yml no longer assigns APK=")

    @Test
    fun `the published file carries no architecture in its name`() {
        // With one file the ABI suffix is noise that invites the old question ("which one?"). Pinned
        // on the assignment, not on the whole file — the name is also quoted in the notes text.
        assertThat(publishedName()).isEqualTo("flipper-the-ripper-\${TAG}.apk")
    }

    @Test
    fun `the README tells people to download the file the workflow publishes`() {
        // The download section names the file pattern; it must be the single-file pattern and must
        // not still offer the per-ABI names.
        val download = readme.substringAfter("## 📥 Download").substringBefore("\n## ")
        assertThat(download).contains("flipper-the-ripper-<version>.apk")
        assertThat(download).doesNotContain("armeabi-v7a.apk")
        assertThat(download).doesNotContain("arm64-v8a.apk")
    }

    @Test
    fun `the version code scheme that keeps installs monotonic is still applied`() {
        // 1.8.3 shipped as 282. Removing the multiplier would make every later release a downgrade.
        assertThat(gradle).contains("base * 10 + offset")
        assertThat(gradle).contains("\"arm64-v8a\" to 2")
    }

    @Test
    fun `a checksum file is produced and published alongside the APK`() {
        // Produced: the line that writes it. Mentioning the file in the notes is not producing it.
        val produces = workflow.lines().any { it.trim() == "sha256sum \"\$APK\" > SHA256SUMS.txt" }
        assertThat(produces).isTrue()
        // Published: listed among the release files.
        val files = workflow.substringAfter("files: |").substringBefore("- name:")
        assertThat(files).contains("SHA256SUMS.txt")
    }
}
