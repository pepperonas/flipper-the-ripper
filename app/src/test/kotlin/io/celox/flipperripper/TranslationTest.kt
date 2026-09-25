package io.celox.flipperripper

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Keeps every translation honest against the English base.
 *
 * The failure this guards against is silent: adding an English string and forgetting a translation
 * leaves a German, Spanish, Italian or French phone showing one English sentence in the middle of a
 * screen, and nothing warns anybody. Format placeholders get the same treatment — a `%1$s` dropped in
 * translation is a crash at runtime, not a typo.
 */
class TranslationTest {
    private val base = File("src/main/res/values/strings.xml").readText()

    /** Every shipped translation, by resource qualifier. */
    private val locales = listOf("de", "es", "it", "fr")

    private fun translation(locale: String) = File("src/main/res/values-$locale/strings.xml").readText()

    /** Names of `<string>` entries, in file order. */
    private fun stringNames(xml: String): List<String> =
        Regex("""<string name="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()

    private fun pluralNames(xml: String): List<String> =
        Regex("""<plurals name="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()

    /** The body of one entry, whichever element type it is. */
    private fun bodyOf(xml: String, name: String): String =
        Regex("""<(string|plurals) name="$name">(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
            .find(xml)!!.groupValues[2]

    /** Distinct placeholders: a plural repeats them once per quantity form, and languages differ in forms. */
    private fun placeholders(text: String): List<String> =
        Regex("""%\d+\$[sdf]""").findAll(text).map { it.value }.distinct().sorted().toList()

    /**
     * Strings that stay English on purpose: the product's own name and its tagline (they are the mark,
     * and appear that way in the banner and launcher). Everything else must be translated.
     */
    private val intentionallyUntranslated = setOf("app_name", "home_title", "home_tagline")

    /**
     * The quantity forms each language needs (CLDR). Spanish, Italian and French have a separate
     * "many" form for very large numbers; without it Android falls back to "other", but lint flags it.
     */
    private val pluralForms =
        mapOf(
            "de" to listOf("one", "other"),
            "es" to listOf("one", "many", "other"),
            "it" to listOf("one", "many", "other"),
            "fr" to listOf("one", "many", "other"),
        )

    @Test
    fun `every shipped locale has a strings file`() {
        val dirs = File("src/main/res").listFiles()!!.map { it.name }
            .filter { it.startsWith("values-") && File("src/main/res/$it/strings.xml").exists() }
            .map { it.removePrefix("values-") }
        assertThat(dirs).containsExactlyElementsIn(locales)
    }

    @Test
    fun `every translatable string has a counterpart in every language`() {
        locales.forEach { locale ->
            val missing = (stringNames(base) - intentionallyUntranslated).toSet() - stringNames(translation(locale)).toSet()
            assertWithMessage(locale).that(missing).isEmpty()
        }
    }

    @Test
    fun `every plural has the forms its language needs`() {
        locales.forEach { locale ->
            val xml = translation(locale)
            assertWithMessage(locale).that(pluralNames(xml)).containsAtLeastElementsIn(pluralNames(base))
            pluralNames(base).forEach { name ->
                val body = bodyOf(xml, name)
                pluralForms.getValue(locale).forEach { form ->
                    assertWithMessage("$locale/$name/$form").that(body).contains("""quantity="$form"""")
                }
            }
        }
    }

    @Test
    fun `translations keep the placeholders their English original has`() {
        // A dropped or renumbered placeholder is a runtime crash on that phone, and would never show
        // up on an English one.
        locales.forEach { locale ->
            val xml = translation(locale)
            (stringNames(base) - intentionallyUntranslated)
                .filter { it in stringNames(xml) }
                .forEach { name ->
                    assertWithMessage("$locale/$name")
                        .that(placeholders(bodyOf(xml, name)))
                        .isEqualTo(placeholders(bodyOf(base, name)))
                }
            pluralNames(base).forEach { name ->
                assertWithMessage("$locale/$name")
                    .that(placeholders(bodyOf(xml, name)))
                    .isEqualTo(placeholders(bodyOf(base, name)))
            }
        }
    }

    @Test
    fun `no translation carries leftovers the base does not have`() {
        // A renamed or deleted English string leaves a dead translation behind; it costs nothing at
        // runtime but rots quietly and misleads the next translator.
        locales.forEach { locale ->
            val orphans = stringNames(translation(locale)).toSet() - stringNames(base).toSet()
            assertWithMessage(locale).that(orphans).isEmpty()
        }
    }

    @Test
    fun `nothing was left in English by accident`() {
        // A copied-but-untranslated entry is the other silent failure: present, so no test for
        // missing keys catches it, yet still English on a translated screen. Compared
        // case-sensitively against the base; short shared tokens (OK, Audio, Server…) are
        // legitimately identical.
        locales.forEach { locale ->
            val xml = translation(locale)
            val identical = (stringNames(base) - intentionallyUntranslated)
                .filter { it in stringNames(xml) }
                .filter { bodyOf(base, it) == bodyOf(xml, it) }
                .filter { bodyOf(base, it).length > SHARED_TOKEN_MAX }
            assertWithMessage(locale).that(identical).isEmpty()
        }
    }

    @Test
    fun `no translation uses a straight apostrophe Android would swallow`() {
        // An unescaped ' in a resource is dropped at build time ("l'app" → "lapp"); the translations
        // use the typographic ’ instead.
        locales.forEach { locale ->
            val bare = Regex("""(?<!\\)'""").findAll(translation(locale).substringAfter("<resources>")).count()
            assertWithMessage(locale).that(bare).isEqualTo(0)
        }
    }

    private companion object {
        /** Up to this length, an identical string is a shared word rather than a missed translation. */
        const val SHARED_TOKEN_MAX = 12
    }
}
