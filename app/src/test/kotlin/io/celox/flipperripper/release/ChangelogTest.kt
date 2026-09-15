package io.celox.flipperripper.release

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * `CHANGELOG.md` is the release notes: the workflow publishes the section for the tag. So the
 * section for the version being built has to exist *before* the tag is pushed, and the file has to
 * stay in the shape the cutting script expects. A missing section used to be an empty release page;
 * now it is a failed workflow — and, one step earlier, this red test.
 */
class ChangelogTest {
    private val changelog = File("../CHANGELOG.md").readText()
    private val gradle = File("build.gradle.kts").readText()

    private data class Section(val version: String, val date: String, val body: String)

    private fun sections(): List<Section> {
        val heads = Regex("""^## \[([0-9][^\]]*)\] - (\S+)""", RegexOption.MULTILINE).findAll(changelog).toList()
        return heads.mapIndexed { i, m ->
            val end = heads.getOrNull(i + 1)?.range?.first ?: changelog.length
            Section(m.groupValues[1], m.groupValues[2], changelog.substring(m.range.last + 1, end))
        }
    }

    @Test
    fun `the version being built has a section`() {
        val version = Regex("""versionName = "([^"]+)"""").find(gradle)!!.groupValues[1]
        assertWithMessage("CHANGELOG section for $version")
            .that(sections().map { it.version })
            .contains(version)
    }

    @Test
    fun `every section carries a real date`() {
        sections().forEach { s ->
            assertWithMessage("date of ${s.version}")
                .that(runCatching { LocalDate.parse(s.date) }.isSuccess)
                .isTrue()
        }
    }

    @Test
    fun `versions descend from the top`() {
        // The script takes the first matching heading; the newest must be first for humans too.
        val parsed = sections().map { s -> s.version.split('.').map { it.toInt() } }
        parsed.zipWithNext().forEach { (a, b) ->
            assertWithMessage("$a listed above $b").that(compare(a, b) > 0).isTrue()
        }
    }

    @Test
    fun `every section says what changed`() {
        // Keep a Changelog categories. A heading with nothing under it would publish an empty page.
        val categories = listOf("### Added", "### Changed", "### Fixed", "### Removed", "### Security", "### Verified")
        sections().forEach { s ->
            assertWithMessage("section ${s.version} has a category")
                .that(categories.any { it in s.body })
                .isTrue()
        }
    }

    @Test
    fun `no two sections claim the same version`() {
        val versions = sections().map { it.version }
        assertThat(versions).containsNoDuplicates()
    }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }
}
