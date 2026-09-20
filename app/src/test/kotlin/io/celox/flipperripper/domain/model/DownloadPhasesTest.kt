package io.celox.flipperripper.domain.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The rule that keeps "100 %" from meaning "finished".
 *
 * yt-dlp reports 100 % when the last byte of the stream lands and then starts the ffmpeg merge,
 * which on a long video takes as long again. Without this the card showed a full bar and
 * "Downloading…" through the whole merge — which reads as finished but frozen.
 */
class DownloadPhasesTest {
    @Test
    fun `the merge that follows a finished transfer is post-processing`() {
        // The real line yt-dlp prints after a split video+audio download.
        assertThat(DownloadPhases.isPostProcessing("[Merger] Merging formats into \"video.mp4\"")).isTrue()
    }

    @Test
    fun `every post-processor yt-dlp runs for this app counts`() {
        listOf(
            "[ExtractAudio] Destination: track.m4a",
            "[ffmpeg] Correcting container",
            "[VideoConvertor] Converting video",
            "[VideoRemuxer] Remuxing video",
            "[FixupM3u8] Fixing MPEG-TS in MP4 container",
            "[FixupM4a] Correcting container",
            "[Metadata] Adding metadata to \"video.mp4\"",
        ).forEach {
            assertWithMessage(it).that(DownloadPhases.isPostProcessing(it)).isTrue()
        }
    }

    @Test
    fun `a transfer line is not post-processing`() {
        listOf(
            "[download]  47.3% of 120.00MiB at 2.00MiB/s ETA 00:31",
            "[download] Destination: video.f137.mp4",
            "[youtube] Extracting URL",
            "transfer", // what the WebView engine reports while streaming bytes
        ).forEach {
            assertWithMessage(it).that(DownloadPhases.isPostProcessing(it)).isFalse()
        }
    }

    @Test
    fun `an unknown or missing line counts as downloading, not as post-processing`() {
        // Claiming post-processing that is not happening would leave the card stuck on "Finishing…"
        // forever; the safe default is the phase that still has a percentage.
        assertThat(DownloadPhases.isPostProcessing(null)).isFalse()
        assertThat(DownloadPhases.isPostProcessing("")).isFalse()
        assertThat(DownloadPhases.isPostProcessing("something nobody has seen before")).isFalse()
    }

    @Test
    fun `the tag is matched however the engine cases it`() {
        assertThat(DownloadPhases.isPostProcessing("[MERGER] merging")).isTrue()
        assertThat(DownloadPhases.isPostProcessing("[merger] merging")).isTrue()
    }
}
