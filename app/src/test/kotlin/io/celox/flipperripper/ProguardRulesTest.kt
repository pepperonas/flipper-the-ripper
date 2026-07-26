package io.celox.flipperripper

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Guards the R8 keep rules that the *release* build depends on.
 *
 * These cannot be covered by ordinary unit tests: the code under test is byte-identical in debug and
 * release, and the failure only materialises after R8 has shrunk/obfuscated the app. That is exactly how
 * a shipped release could crash on launch for every user while the whole JVM suite and every debug
 * install stayed green — so the rules themselves are asserted here instead.
 *
 * The crash: youtubedl-android unpacks its Python/yt-dlp payload with Apache Commons Compress, whose
 * `ExtraFieldUtils` static initialiser *reflectively* instantiates each `ZipExtraField` implementation.
 * Nothing in the app constructs those types directly, so R8 renamed them and turned them abstract; the
 * registry then threw `class ...zip.a is not a concrete class`, `YoutubeDL.init()` died with
 * `ExceptionInInitializerError`, and the app could never start.
 */
class ProguardRulesTest {
    private val rules: String by lazy {
        val candidates =
            listOf(
                File("proguard-rules.pro"),
                File("app/proguard-rules.pro"),
                File("../app/proguard-rules.pro"),
            )
        val file =
            candidates.firstOrNull { it.exists() }
                ?: error("proguard-rules.pro not found; looked in ${candidates.map { it.absolutePath }}")
        file.readText()
    }

    @Test
    fun `keeps ZipExtraField implementations concrete and default-constructible`() {
        // Without this, R8 makes the reflectively-instantiated implementations abstract.
        assertThat(rules).contains("implements org.apache.commons.compress.archivers.zip.ZipExtraField")
        assertThat(rules).contains("<init>();")
    }

    @Test
    fun `keeps the ZipExtraField interface and the reflective registry`() {
        assertThat(rules).contains("-keep class org.apache.commons.compress.archivers.zip.ZipExtraField")
        assertThat(rules).contains("org.apache.commons.compress.archivers.zip.ExtraFieldUtils")
    }

    @Test
    fun `keeps the youtubedl-android engine classes that are reached reflectively`() {
        assertThat(rules).contains("-keep class com.yausername.youtubedl_android.** { *; }")
        assertThat(rules).contains("-keep class com.yausername.ffmpeg.** { *; }")
    }

    @Test
    fun `keeps Room database subclasses`() {
        assertThat(rules).contains("-keep class * extends androidx.room.RoomDatabase")
    }

    @Test
    fun `documents why the commons-compress rules exist`() {
        // A future reader must not "clean up" these rules without understanding the launch crash.
        assertThat(rules).contains("not a concrete class")
    }
}
