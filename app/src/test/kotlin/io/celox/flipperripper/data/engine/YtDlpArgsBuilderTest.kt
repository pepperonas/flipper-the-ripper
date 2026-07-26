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
    fun `youtube gets the player-client workaround`() {
        val args = YtDlpArgsBuilder.build("https://youtu.be/x", Platform.YOUTUBE, DownloadMode.VIDEO, template)
        assertThat(args).containsAtLeast(
            "--extractor-args",
            "youtube:player_client=${YtDlpArgsBuilder.YOUTUBE_PLAYER_CLIENTS}",
        ).inOrder()
    }

    @Test
    fun `youtube uses only a client that needs neither a JS runtime nor a PO token`() {
        // On-device there is no JS runtime and no PO-token provider, so a client failing either gate
        // yields no downloadable format even though extraction succeeded. This asserts the intersection
        // of both constraints, which is what actually makes YouTube downloads work on a stock device.
        val clients = YtDlpArgsBuilder.YOUTUBE_PLAYER_CLIENTS.split(",").map { it.trim() }

        assertThat(clients).containsExactly("android_vr")
        // Need the n-sig JavaScript challenge -> "n challenge solving failed".
        assertThat(clients).containsNoneOf("web", "web_safari", "web_embedded", "mweb")
        // Require a GVS PO token -> formats skipped, download fails.
        assertThat(clients).containsNoneOf("ios", "android", "tv_simply")
        // `tv` is DRM without cookies; `default` resolves into the gated clients above.
        assertThat(clients).containsNoneOf("tv", "default")
    }

    @Test
    fun `the youtube extractor arg is a single token yt-dlp can parse`() {
        val args = YtDlpArgsBuilder.build("https://youtu.be/x", Platform.YOUTUBE, DownloadMode.VIDEO, template)
        val value = args[args.indexOf("--extractor-args") + 1]

        assertThat(value).startsWith("youtube:player_client=")
        assertThat(value).doesNotContain(" ")
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
