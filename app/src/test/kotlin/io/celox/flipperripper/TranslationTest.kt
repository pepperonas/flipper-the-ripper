package io.celox.flipperripper

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Keeps the German translation honest against the English base.
 *
 * The failure this guards against is silent: adding an English string and forgetting the German one
 * leaves a German phone showing one English sentence in the middle of a screen, and nothing warns
 * anybody. Format placeholders get the same treatment — a `%1$s` dropped in translation is a crash at
 * runtime, not a typo.
 */
class TranslationTest {
    private val base = File("src/main/res/values/strings.xml").readText()
    private val german = File("src/main/res/values-de/strings.xml").readText()

    /** Names of `<string>` entries, in file order. */
    private fun stringNames(xml: String): List<String> =
        Regex("""<string name="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()

    private fun pluralNames(xml: String): List<String> =
        Regex("""<plurals name="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()

    /** The body of one entry, whichever element type it is. */
    private fun bodyOf(xml: String, name: String): String =
        Regex("""<(string|plurals) name="$name">(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
            .find(xml)!!.groupValues[2]

    private fun placeholders(text: String): List<String> =
        Regex("""%\d+\$[sdf]""").findAll(text).map { it.value }.sorted().toList()

    /**
     * Strings that stay English on purpose: the product's own name and its tagline (they are the mark,
     * and appear that way in the banner and launcher). Everything else must be translated.
     */
    private val intentionallyUntranslated = setOf("app_name", "home_title", "home_tagline")

    @Test
    fun `every translatable string has a German counterpart`() {
        val missing = (stringNames(base) - intentionallyUntranslated).toSet() - stringNames(german).toSet()
        assertThat(missing).isEmpty()
    }

    @Test
    fun `every plural has a German counterpart, with the forms German needs`() {
        assertThat(pluralNames(german)).containsAtLeastElementsIn(pluralNames(base))
        pluralNames(base).forEach { name ->
            val body = bodyOf(german, name)
            // German, like English, distinguishes one from many.
            assertThat(body).contains("""quantity="one"""")
            assertThat(body).contains("""quantity="other"""")
        }
    }

    @Test
    fun `translations keep the placeholders their English original has`() {
        // A dropped or renumbered placeholder is a runtime crash on a German phone, and would never
        // show up on an English one.
        (stringNames(base) - intentionallyUntranslated)
            .filter { it in stringNames(german) }
            .forEach { name ->
                assertWithMessage(name)
                    .that(placeholders(bodyOf(german, name)))
                    .isEqualTo(placeholders(bodyOf(base, name)))
            }
        pluralNames(base).forEach { name ->
            assertWithMessage(name)
                .that(placeholders(bodyOf(german, name)))
                .isEqualTo(placeholders(bodyOf(base, name)))
        }
    }

    @Test
    fun `the German file carries no leftovers the base does not have`() {
        // A renamed or deleted English string leaves a dead German one behind; it costs nothing at
        // runtime but rots quietly and misleads the next translator.
        val orphans = stringNames(german).toSet() - stringNames(base).toSet()
        assertThat(orphans).isEmpty()
    }

    @Test
    fun `nothing was left in English by accident`() {
        // A copied-but-untranslated entry is the other silent failure: present, so no test for
        // missing keys catches it, yet still English on a German screen. Compared case-sensitively
        // against the base; short shared tokens (OK, Audio, Server…) are legitimately identical.
        val identical = (stringNames(base) - intentionallyUntranslated)
            .filter { it in stringNames(german) }
            .filter { bodyOf(base, it) == bodyOf(german, it) }
            .filter { bodyOf(base, it).length > SHARED_TOKEN_MAX }
        assertThat(identical).isEmpty()
    }

    private companion object {
        /** Up to this length, an identical string is a shared word rather than a missed translation. */
        const val SHARED_TOKEN_MAX = 12
    }
}
