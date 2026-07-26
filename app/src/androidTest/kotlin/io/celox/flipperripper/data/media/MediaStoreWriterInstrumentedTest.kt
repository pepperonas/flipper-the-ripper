package io.celox.flipperripper.data.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.EngineResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * Exercises the real MediaStore write path on-device (scoped storage on API 29+, legacy scan below).
 * Confirms a saved video yields a resolvable content URI in the gallery.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MediaStoreWriterInstrumentedTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var writer: MediaStoreWriter

    @Before
    fun setUp() = hiltRule.inject()

    @Test
    fun savesVideoAndReturnsContentUri() =
        runTest(Dispatchers.IO) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val source = File(context.cacheDir, "sample-${System.nanoTime()}.mp4")
            source.writeBytes(ByteArray(2048) { it.toByte() })

            val result = writer.save(source, "Flipper Test Clip.mp4", DownloadMode.VIDEO)

            assertThat(result).isInstanceOf(EngineResult.Success::class.java)
            val saved = (result as EngineResult.Success).value
            assertThat(saved.uri).isNotEmpty()
            assertThat(saved.sizeBytes).isEqualTo(2048)

            // Clean up the MediaStore entry we created.
            runCatching { context.contentResolver.delete(android.net.Uri.parse(saved.uri), null, null) }
            source.delete()
        }
}
