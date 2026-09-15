package io.celox.flipperripper.release

import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs the real `scripts/release-notes.sh` against the real `CHANGELOG.md`. The script decides what
 * the release page says; if it cut the neighbouring section, or the whole file, or nothing, the
 * only place that would show is the published release — after the fact.
 */
class ReleaseNotesScriptTest {
    private val root = File("..").canonicalFile
    private val script = File(root, "scripts/release-notes.sh")

    private fun run(vararg args: String): Pair<Int, String> {
        assumeTrue("needs a POSIX shell", File("/bin/sh").exists())
        val p =
            ProcessBuilder(listOf(script.path) + args)
                .directory(root)
                .redirectErrorStream(true)
                .start()
        val out = p.inputStream.bufferedReader().readText()
        return p.waitFor() to out
    }

    @Test
    fun `cuts exactly the tagged section`() {
        val (rc, out) = run("v1.8.3")
        assertThat(rc).isEqualTo(0)
        // Content of 1.8.3 …
        assertThat(out).contains("Sharing a link now opens Home")
        // … and nothing of its neighbours on either side.
        assertThat(out).doesNotContain("## [1.8.3]")
        assertThat(out).doesNotContain("## [1.8.2]")
        assertThat(out).doesNotContain("direct share target")
        assertThat(out).doesNotContain("## [1.9.0]")
    }

    @Test
    fun `starts with the first category, not blank lines`() {
        val (_, out) = run("v1.8.3")
        assertThat(out.lines().first()).startsWith("### ")
    }

    @Test
    fun `an unknown tag is a failure, not an empty page`() {
        val (rc, out) = run("v0.0.0")
        assertThat(rc).isNotEqualTo(0)
        assertThat(out).contains("no section")
    }

    /** The section as Kotlin cuts it — an independent implementation to hold the script to. */
    private fun expectedBody(version: String): String {
        val text = File(root, "CHANGELOG.md").readText()
        val heads = Regex("""^## \[[^\]]+\] - .*$""", RegexOption.MULTILINE).findAll(text).toList()
        val i = heads.indexOfFirst { it.value.startsWith("## [$version] ") }
        check(i >= 0) { "no section $version" }
        val end = heads.getOrNull(i + 1)?.range?.first ?: text.length
        return text.substring(heads[i].range.last + 1, end).trim()
    }

    @Test
    fun `cuts the same section an independent parser finds, for versions with awkward neighbours`() {
        // 1.2.1 sits BELOW 1.2.10/11/12 in the file, so a prefix match would return 1.2.12's section;
        // 1.0.0 is the last section, so the cut must run to end-of-file; 1.8.3 is an ordinary one.
        listOf("1.2.1", "1.0.0", "1.8.3").forEach { version ->
            val (rc, out) = run("v$version")
            assertThat(rc).isEqualTo(0)
            assertThat(out.trim()).isEqualTo(expectedBody(version))
        }
    }
}
