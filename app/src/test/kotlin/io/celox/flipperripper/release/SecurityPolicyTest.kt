package io.celox.flipperripper.release

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.celox.flipperripper.domain.model.Platform
import org.junit.Test
import java.io.File

/**
 * `SECURITY.md` makes claims about the app. Two of them had quietly gone false — a permission was
 * missing from its list, and it said no authentication token is stored while the Instagram session
 * cookie had been stored since 1.2.11. The cookie claim is prose and cannot be held by a test; the
 * lists can, and are.
 */
class SecurityPolicyTest {
    private val security = File("../SECURITY.md").readText()
    private val contributing = File("../CONTRIBUTING.md").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun `every permission the manifest requests is named in the security policy`() {
        val requested =
            Regex("""android:name="android\.permission\.([A-Z_]+)"""").findAll(manifest)
                .map { it.groupValues[1] }
                .toSet()
        assertThat(requested).isNotEmpty()
        requested.forEach { perm ->
            assertWithMessage("SECURITY.md names $perm").that(security).contains("`$perm`")
        }
    }

    @Test
    fun `the security policy names no permission the manifest does not request`() {
        // The reverse drift: a permission removed from the app but still "declared" in the policy.
        val claimed =
            Regex("""`([A-Z_]{6,})`""").findAll(security)
                .map { it.groupValues[1] }
                .filter { it != "SHA" }
                .toSet()
        val requested =
            Regex("""android:name="android\.permission\.([A-Z_]+)"""").findAll(manifest)
                .map { it.groupValues[1] }
                .toSet()
        assertThat(claimed).containsExactlyElementsIn(requested)
    }

    @Test
    fun `both policy documents list every supported platform`() {
        // A platform the app supports but the policy does not mention is a platform whose
        // behaviour is not declared out of scope.
        val names = Platform.entries.map { it.displayName }
        listOf("SECURITY.md" to security, "CONTRIBUTING.md" to contributing).forEach { (file, text) ->
            names.forEach { name ->
                // Whole word: "X" is a platform, not a letter that any text contains.
                assertWithMessage("$file mentions $name")
                    .that(Regex("""(?<![A-Za-z])${Regex.escape(name)}(?![A-Za-z])""").containsMatchIn(text))
                    .isTrue()
            }
        }
    }
}
