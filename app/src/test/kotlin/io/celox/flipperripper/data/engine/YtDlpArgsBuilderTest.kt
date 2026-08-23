package io.celox.flipperripper.data.engine

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.Platform
import org.junit.Test

class YtDlpArgsBuilderTest {
    private val template = "/data/work/%(title).100B [%(id)s].%(ext)s"

    @Test
    fun `video mode selects h264 and mp4 muxing`() {
        val args = YtDlpArgsBuilder.build("https://youtu.be/x", Platform.YOUTUBE, DownloadMode.VIDEO, template)
        assertThat(args).containsAtLeast("-S", "vcodec:h264,res,acodec:m4a").inOrder()
        assertThat(args).containsAtLeast("--merge-output-format", "mp4").inOrder()
    }

    @Test
    fun `audio mode extracts m4a at best quality`() {
        val args = YtDlpArgsBuilder.build("https://youtu.be/x", Platform.YOUTUBE, DownloadMode.AUDIO, template)
        assertThat(args).contains("-x")
        assertThat(args).containsAtLeast("--audio-format", "m4a").inOrder()
        assertThat(args).containsAtLeast("--audio-quality", "0").inOrder()
        assertThat(args).doesNotContain("--merge-output-format")
    }

    @Test
    fun `no platform pins a youtube player client`() {
        // The android_vr pin died in 2026-08 (YouTube extended PO-token enforcement to it — media
        // fetches 403 mid-stream). With QuickJS bundled (youtubedl-android >= 0.18), yt-dlp's own
        // maintained default rotation is the thing that keeps working, refreshed by the auto-update.
        // Pinning any client re-creates the rot, so no build may emit --extractor-args for YouTube.
        listOf(Platform.YOUTUBE, Platform.INSTAGRAM, Platform.TIKTOK, Platform.FACEBOOK).forEach { p ->
            val args = YtDlpArgsBuilder.build("https://example.com/x", p, DownloadMode.VIDEO, template)
            assertThat(args).doesNotContain("--extractor-args")
        }
    }

    @Test
    fun `always sets no-playlist and no-mtime`() {
        val args = YtDlpArgsBuilder.build("https://tiktok.com/@a/video/1", Platform.TIKTOK, DownloadMode.VIDEO, template)
        assertThat(args).contains("--no-playlist")
        assertThat(args).contains("--no-mtime")
    }

    @Test
    fun `omits no-progress so android can parse progress`() {
        val args = YtDlpArgsBuilder.build("https://youtu.be/x", Platform.YOUTUBE, DownloadMode.VIDEO, template)
        assertThat(args).doesNotContain("--no-progress")
    }

    @Test
    fun `never passes desktop cookie flags`() {
        val args = YtDlpArgsBuilder.build("https://youtu.be/x", Platform.YOUTUBE, DownloadMode.VIDEO, template)
        assertThat(args).doesNotContain("--cookies-from-browser")
        assertThat(args).doesNotContain("--cookies")
    }

    @Test
    fun `url is placed after a flag-injection guard`() {
        val url = "-https://youtu.be/malicious"
        val args = YtDlpArgsBuilder.build(url, Platform.YOUTUBE, DownloadMode.VIDEO, template)
        val guardIndex = args.indexOf("--")
        assertThat(guardIndex).isGreaterThan(-1)
        assertThat(args.last()).isEqualTo(url)
        assertThat(args.indexOf(url)).isGreaterThan(guardIndex)
    }

    @Test
    fun `engine options never contain an end-of-options guard`() {
        // youtubedl-android appends its own flags (notably `--ffmpeg-location <path>`) AFTER these
        // options. A `--` anywhere in here demotes those flags to positional arguments, so yt-dlp tried
        // to download the ffmpeg binary path as a URL and every download ended with
        // "ERROR: [generic] '…/libffmpeg.so' is not a valid URL" — after visibly downloading the real
        // video first. Nothing ever reached the gallery.
        val combos =
            listOf(
                Triple(Platform.YOUTUBE, DownloadMode.VIDEO, false),
                Triple(Platform.YOUTUBE, DownloadMode.AUDIO, false),
                Triple(Platform.YOUTUBE, DownloadMode.VIDEO, true),
                Triple(Platform.YOUTUBE, DownloadMode.AUDIO, true),
                Triple(Platform.INSTAGRAM, DownloadMode.VIDEO, false),
                Triple(Platform.TIKTOK, DownloadMode.VIDEO, true),
            )

        combos.forEach { (platform, mode, progressive) ->
            val args = YtDlpArgsBuilder.buildOptions(platform, mode, template, progressive)
            assertThat(args).doesNotContain("--")
        }
    }

    @Test
    fun `engine options end with the output template so appended flags stay flags`() {
        val args = YtDlpArgsBuilder.buildOptions(Platform.YOUTUBE, DownloadMode.VIDEO, template)

        assertThat(args.last()).isEqualTo(template)
        assertThat(args[args.size - 2]).isEqualTo("-o")
    }

    @Test
    fun `progressive video mode uses a single pre-muxed format and no merge`() {
        val args = YtDlpArgsBuilder.buildOptions(Platform.YOUTUBE, DownloadMode.VIDEO, template, preferProgressive = true)
        assertThat(args).containsAtLeast("-f", "best[ext=mp4]/best").inOrder()
        assertThat(args).doesNotContain("--merge-output-format")
        assertThat(args).doesNotContain("-S")
    }

    @Test
    fun `progressive audio mode grabs the m4a stream without ffmpeg extraction`() {
        val args = YtDlpArgsBuilder.buildOptions(Platform.YOUTUBE, DownloadMode.AUDIO, template, preferProgressive = true)
        assertThat(args).containsAtLeast("-f", "ba[ext=m4a]/ba").inOrder()
        assertThat(args).doesNotContain("-x")
    }

    @Test
    fun `output template is passed to -o`() {
        val args = YtDlpArgsBuilder.build("https://youtu.be/x", Platform.YOUTUBE, DownloadMode.VIDEO, template)
        assertThat(args).containsAtLeast("-o", template).inOrder()
    }
}
