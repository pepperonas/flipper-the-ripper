package io.celox.flipperripper.data.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EngineOutputTest {
    @get:Rule val folder = TemporaryFolder()

    private fun file(name: String, bytes: Int = 10, modified: Long? = null): File =
        folder.newFile(name).apply {
            writeBytes(ByteArray(bytes))
            modified?.let { setLastModified(it) }
        }

    @Test
    fun `a partial stream is never mistaken for the finished download`() {
        // The old rule was "the first non-empty file", which only held because the directory was
        // wiped before every run. A resumed download finds its own `.part` sitting there.
        val part = file("clip.f299.mp4.part", bytes = 5_000)
        assertThat(EngineOutput.finished(listOf(part))).isNull()
    }

    @Test
    fun `yt-dlp's bookkeeping files are ignored too`() {
        assertThat(EngineOutput.finished(listOf(file("clip.mp4.ytdl"), file("clip.mp4.tmp")))).isNull()
    }

    @Test
    fun `the newest complete file wins`() {
        // A run that fell back to another format leaves the earlier attempt behind; what was just
        // produced is what matters.
        val old = file("old.mp4", modified = 1_000_000L)
        val new = file("new.mp4", modified = 2_000_000L)
        assertThat(EngineOutput.finished(listOf(old, new))).isEqualTo(new)
    }

    @Test
    fun `an empty file is not a download`() {
        val empty = folder.newFile("clip.mp4")
        assertThat(EngineOutput.finished(listOf(empty))).isNull()
    }

    @Test
    fun `a directory with bytes is something to continue from`() {
        file("clip.f299.mp4.part", bytes = 5_000)
        assertThat(EngineOutput.hasPartialBytes(folder.root)).isTrue()
    }

    @Test
    fun `an empty or missing directory is a fresh start`() {
        val empty = folder.newFolder("nothing")
        assertThat(EngineOutput.hasPartialBytes(empty)).isFalse()
        assertThat(EngineOutput.hasPartialBytes(File(folder.root, "does-not-exist"))).isFalse()
    }

    @Test
    fun `a zero-byte leftover is not worth resuming`() {
        val dir = folder.newFolder("stub")
        File(dir, "clip.mp4.part").createNewFile()
        assertThat(EngineOutput.hasPartialBytes(dir)).isFalse()
    }
}
