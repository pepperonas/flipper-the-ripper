package io.celox.flipperripper.data.share

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Guards the wiring that decides whether the app can be picked when someone shares a link.
 *
 * Every failure this covers is silent. A share-target whose category no shortcut carries is never
 * offered; a shortcut whose category no share-target declares is never a share target; a MIME type
 * declared on one side and not the other narrows the app out of the sheet. Nothing logs a word about
 * any of it — the only symptom is a user reporting the app "isn't there", which is exactly how this
 * was found.
 */
class ShareTargetRegistrationTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val shortcuts = File("src/main/res/xml/shortcuts.xml").readText()

    /** The `<intent-filter>` blocks of the main activity, as raw text. */
    private fun intentFilters(): List<String> =
        Regex("""<intent-filter>(.*?)</intent-filter>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(manifest)
            .map { it.groupValues[1] }
            .toList()

    private fun sendFilter(): String =
        intentFilters().single { it.contains("android.intent.action.SEND") }

    private fun mimeTypes(block: String): Set<String> =
        Regex("""android:mimeType="([^"]+)"""").findAll(block).map { it.groupValues[1] }.toSet()

    @Test
    fun `the activity accepts a shared plain-text link`() {
        // text/plain is what a link is shared as; without it the app is in no share sheet at all.
        val filter = sendFilter()
        assertThat(filter).contains("android.intent.category.DEFAULT")
        assertThat(mimeTypes(filter)).contains("text/plain")
    }

    @Test
    fun `the activity that receives shares is exported`() {
        // An un-exported activity cannot be started by the share sheet, whatever it declares.
        val activity =
            Regex("""<activity(.*?)</activity>""", RegexOption.DOT_MATCHES_ALL)
                .find(manifest)!!
                .groupValues[1]
        assertThat(activity).contains("""android:exported="true"""")
    }

    @Test
    fun `the filter is not narrowed to one exact subtype`() {
        // Senders do not all label shared text as text/plain. Accepting the wildcard as well costs
        // nothing and is the difference between being offered and being invisible.
        assertThat(mimeTypes(sendFilter())).contains("text/*")
    }

    @Test
    fun `the manifest points at the shortcut definitions`() {
        // Without this meta-data the share-target file is never read, and no amount of correct XML
        // inside it has any effect.
        assertThat(manifest).contains("""android:name="android.app.shortcuts"""")
        assertThat(manifest).contains("""android:resource="@xml/shortcuts"""")
    }

    @Test
    fun `the share-target and the published shortcut agree on the category`() {
        // The pairing is by string. A typo on either side silently removes the app from the
        // suggested row, with no error anywhere.
        val declared =
            Regex("""<category android:name="([^"]+)"""").findAll(shortcuts)
                .map { it.groupValues[1] }
                .toSet()
        assertWithMessage("categories declared in shortcuts.xml")
            .that(declared)
            .contains(ShareShortcut.CATEGORY)
    }

    @Test
    fun `the share-target aims at the activity that can actually receive the share`() {
        val targetClass =
            Regex("""android:targetClass="([^"]+)"""").find(shortcuts)!!.groupValues[1]
        // The manifest names the activity relatively; compare on the class name.
        assertThat(targetClass).endsWith(".MainActivity")
        assertThat(manifest).contains(""".ui.MainActivity""")
    }

    @Test
    fun `the share-target claims only a type the activity accepts`() {
        // A share-target for a MIME type the intent filter rejects is a target that resolves to
        // nothing — the sheet offers it and the tap goes nowhere.
        val accepted = mimeTypes(sendFilter())
        mimeTypes(shortcuts).forEach { declared ->
            val covered =
                declared in accepted ||
                    accepted.any { it.endsWith("/*") && declared.startsWith(it.dropLast(1)) }
            assertWithMessage("share-target type $declared is accepted by the intent filter")
                .that(covered)
                .isTrue()
        }
    }

    @Test
    fun `the shortcut id is stable`() {
        // Publishing under a fresh id every start would add shortcuts rather than update the one,
        // and the launcher caps how many an app may have.
        assertThat(ShareShortcut.ID).isNotEmpty()
        assertThat(ShareShortcut.ID).doesNotContain(" ")
    }
}
