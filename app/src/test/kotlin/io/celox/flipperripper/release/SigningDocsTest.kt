package io.celox.flipperripper.release

import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

/**
 * The signing certificate's SHA-256 is the one string a user verifies a download against, and it is
 * written in three places by hand: README, SECURITY.md and the release workflow (which refuses to
 * publish on a mismatch). Those three must agree with each other — and, on a machine that has the
 * keystore, with the keystore.
 */
class SigningDocsTest {
    private val root = File("..")
    private val readme = File(root, "README.md").readText()
    private val security = File(root, "SECURITY.md").readText()
    private val workflow = File(root, ".github/workflows/release.yml").readText()

    private val sha256 = Regex("""\b[0-9a-f]{64}\b""")

    private fun digestsIn(text: String) = sha256.findAll(text).map { it.value }.toSet()

    @Test
    fun `the three documents publish one and the same digest`() {
        val all = listOf(readme, security, workflow).map(::digestsIn)
        all.forEach { assertThat(it).hasSize(1) }
        assertThat(all.toSet()).hasSize(1)
    }

    @Test
    fun `the digest is the release keystore's, where the keystore is present`() {
        val props = File(root, "keystore.properties")
        assumeTrue("release keystore is only present on the maintainer's machine", props.exists())
        val p = Properties().apply { props.inputStream().use(::load) }
        val ks = KeyStore.getInstance("JKS") /* compat mode reads PKCS12 too */
        File(root, p.getProperty("storeFile")).inputStream().use { ks.load(it, p.getProperty("storePassword").toCharArray()) }
        val cert = ks.getCertificate(p.getProperty("keyAlias"))
        val actual = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
        assertThat(digestsIn(readme).single()).isEqualTo(actual)
    }
}
