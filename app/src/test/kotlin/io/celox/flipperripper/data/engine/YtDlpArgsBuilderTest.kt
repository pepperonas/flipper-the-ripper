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
    fun `youtube gets the SABR player-client workaround`() {
        val args = YtDlpArgsBuilder.build("https://youtu.be/x", Platform.YOUTUBE, DownloadMode.VIDEO, template)
        assertThat(args).containsAtLeast(
            "--extractor-args",
            "youtube:player_client=default,android_vr,tv,ios",
        ).inOrder()
    }

    @Test
    fun `non-youtube platforms omit the youtube extractor arg`() {
        val ig = YtDlpArgsBuilder.build("https://instagram.com/reel/x", Platform.INSTAGRAM, DownloadMode.VIDEO, template)
        val tt = YtDlpArgsBuilder.build("https://tiktok.com/@a/video/1", Platform.TIKTOK, DownloadMode.VIDEO, template)
        assertThat(ig).doesNotContain("--extractor-args")
        assertThat(tt).doesNotContain("--extractor-args")
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
