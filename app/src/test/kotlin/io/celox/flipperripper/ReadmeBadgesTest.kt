package io.celox.flipperripper

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * The badges at the top of the README state facts — version, test count, size of the codebase. A badge
 * that quietly goes stale is worse than no badge, so each one is measured against the thing it claims
 * to describe.
 *
 * Only the line count is given room to move: every commit changes it a little, and a badge that turns
 * the build red for one added line would just get deleted. The check is that it is still roughly true.
 */
class ReadmeBadgesTest {
    private val readme = File("../README.md").readText()
    private val buildFile = File("build.gradle.kts").readText()

    private fun badgeValue(label: String): String {
        // shields.io static badges: https://img.shields.io/badge/<label>-<value>-<colour>?…
        val m = Regex("""img\.shields\.io/badge/$label-([^-]+)-""").find(readme)
        assertThat(m).isNotNull()
        return m!!.groupValues[1]
    }

    @Test
    fun `the version badge matches the version the app is built with`() {
        val version = Regex("""versionName = "([^"]+)"""").find(buildFile)!!.groupValues[1]
        assertThat(badgeValue("version")).isEqualTo(version)
    }

    @Test
    fun `the test badge counts the tests that actually exist`() {
        val counted = countTests(File("src/test"))
        assertThat(badgeValue("unit%20tests").toInt()).isEqualTo(counted)
    }

    @Test
    fun `the instrumentation count is honest too`() {
        val counted = countTests(File("src/androidTest"))
        assertThat(badgeValue("instrumented").toInt()).isEqualTo(counted)
    }

    @Test
    fun `the lines-of-code badge is still roughly true`() {
        val actual = File("src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sumOf { it.readLines().size }
        // Badge reads like "6.5k"; allow 10% drift before it counts as a lie.
        val claimed = badgeValue("lines%20of%20code").removeSuffix("k").toDouble() * 1000
        assertThat(actual.toDouble()).isWithin(claimed * 0.10).of(claimed)
    }

    @Test
    fun `the SDK badges match the SDK levels the app is actually built for`() {
        listOf("minSdk" to "min%20SDK", "targetSdk" to "target%20SDK").forEach { (gradleKey, label) ->
            val actual = Regex("""$gradleKey = (\d+)""").find(buildFile)!!.groupValues[1]
            assertThat(badgeValue(label)).isEqualTo(actual)
        }
    }

    @Test
    fun `the donate badge points at the author's own account`() {
        assertThat(readme).contains("paypal.com/donate/?business=martin.pfeffer@celox.io")
        assertThat(readme).contains("currency_code=EUR")
    }

    @Test
    fun `the headline badges come first, and the donate badge comes last`() {
        val order = listOf("version-", "unit%20tests-", "lines%20of%20code-")
            .map { readme.indexOf(it) }
        assertThat(order.none { it < 0 }).isTrue()
        assertThat(order).isInOrder()
        // Everything the badges claim sits above the first heading, where a reader actually looks.
        assertThat(readme.indexOf("## ")).isGreaterThan(order.max())
        val donate = readme.indexOf("PayPal")
        assertThat(donate).isGreaterThan(readme.indexOf("License-MIT"))
    }

    private fun countTests(dir: File): Int =
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sumOf { file -> Regex("""^\s*@Test\b""", RegexOption.MULTILINE).findAll(file.readText()).count() }
}
