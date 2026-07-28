package io.celox.flipperripper.data.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Regression tests for the saved-file title.
 *
 * The app promises title-based filenames. A share-intent download starts with the raw URL as its title
 * (metadata resolves afterwards, and can fail), which produced files such as
 * `https www.youtube.com watch v=jNQXAC9IVRw.mp4`. yt-dlp already encodes the real title in the file it
 * writes, so that must win over the URL.
 */
class DownloadNamingTest {
    @Test
    fun `resolved metadata title wins`() {
        val name = DownloadNaming.preferredTitle("Me at the zoo", "Me at the zoo [jNQXAC9IVRw]", "https://youtu.be/x")

        assertThat(name).isEqualTo("Me at the zoo")
    }

    @Test
    fun `falls back to the title yt-dlp wrote when metadata failed`() {
        val name =
            DownloadNaming.preferredTitle(
                resolvedTitle = null,
                producedFileBaseName = "Me at the zoo [jNQXAC9IVRw]",
                fallbackTitle = "https://www.youtube.com/watch?v=jNQXAC9IVRw",
            )

        assertThat(name).isEqualTo("Me at the zoo")
    }

    @Test
    fun `never falls back to the raw url when the file carries a title`() {
        val url = "https://www.youtube.com/watch?v=jNQXAC9IVRw"

        val name = DownloadNaming.preferredTitle(null, "Some Video [abc123]", url)

        assertThat(name).doesNotContain("http")
        assertThat(name).isNotEqualTo(url)
    }

    @Test
    fun `blank resolved title is treated as missing`() {
        val name = DownloadNaming.preferredTitle("   ", "Real Title [id1]", "fallback")

        assertThat(name).isEqualTo("Real Title")
    }

    @Test
    fun `uses the record title only when nothing better exists`() {
        val name = DownloadNaming.preferredTitle(null, "", "https://youtu.be/x")

        assertThat(name).isEqualTo("https://youtu.be/x")
    }

    @Test
    fun `strips only a trailing id suffix`() {
        assertThat(DownloadNaming.titleFromProducedFile("Video [id]")).isEqualTo("Video")
        // Brackets that are part of the title itself must survive.
        assertThat(DownloadNaming.titleFromProducedFile("[Live] Concert [xyz]")).isEqualTo("[Live] Concert")
        assertThat(DownloadNaming.titleFromProducedFile("Plain title")).isEqualTo("Plain title")
    }

    @Test
    fun `display title drops the file extension`() {
        // The card used to read "Video by kvashenaya.mp4"; the extension is not part of a title.
        assertThat(DownloadNaming.displayTitle("Video by kvashenaya.mp4", "ignored"))
            .isEqualTo("Video by kvashenaya")
        assertThat(DownloadNaming.displayTitle("Track.m4a", "ignored")).isEqualTo("Track")
    }

    @Test
    fun `display title keeps dots that are not an extension`() {
        assertThat(DownloadNaming.displayTitle("Best of 2026. The Recap", "x"))
            .isEqualTo("Best of 2026. The Recap")
    }

    @Test
    fun `display title shortens a raw url to what identifies the video`() {
        // A failed entry keeps the source URL as its title and filled the whole card with it.
        val long = "https://www.instagram.com/reel/DbDBPYJnUMW/?igsh=MW5vNDdwbGZ4bGJ4"

        val shown = DownloadNaming.displayTitle(fileName = null, fallbackTitle = long)

        assertThat(shown).isEqualTo("reel/DbDBPYJnUMW")
        assertThat(shown).doesNotContain("igsh")
        assertThat(shown).doesNotContain("https")
    }

    @Test
    fun `display title leaves a real title untouched`() {
        assertThat(DownloadNaming.displayTitle(null, "Me at the zoo")).isEqualTo("Me at the zoo")
    }

    @Test
    fun `display title falls back to the host when a url has no path`() {
        assertThat(DownloadNaming.displayTitle(null, "https://youtu.be")).isEqualTo("youtu.be")
    }

    @Test
    fun `produced name flows into a sanitised filename`() {
        val title = DownloadNaming.preferredTitle(null, "AC/DC: Live [xyz]", "https://youtu.be/x")

        // The raw title still holds characters illegal in a filename; the sanitiser replaces them and
        // collapses the resulting whitespace runs.
        assertThat(FilenameSanitizer.sanitize(title, "mp4")).isEqualTo("AC DC Live.mp4")
    }
}
