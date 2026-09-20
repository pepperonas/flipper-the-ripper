package io.celox.flipperripper.domain.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * The quality rule, checked against **real** format lists captured from yt-dlp rather than invented
 * ones. The first version of `availableTiers` looked right and was wrong, and it was a real fixture
 * that said so: "Me at the zoo" exists only in 144p and 240p, and the rule happily offered 480p.
 */
class FormatSelectionTest {
    private fun fixture(name: String): List<MediaFormat> {
        val text = requireNotNull(javaClass.getResourceAsStream("/formats/$name.json")) {
            "missing fixture $name"
        }.bufferedReader().readText()
        return Json.parseToJsonElement(text).jsonObject["formats"]!!.jsonArray.map { element ->
            val f = element.jsonObject
            fun str(key: String) = f[key]?.jsonPrimitive?.contentOrNull
            MediaFormat(
                height = f["height"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                hasVideo = str("vcodec").let { it != null && it != "none" },
                hasAudio = str("acodec").let { it != null && it != "none" },
            )
        }
    }

    // --- availability ----------------------------------------------------------------------

    @Test
    fun `a video that only exists small does not offer the big tiers`() {
        // Me at the zoo: 144p and 240p, nothing else. Offering "720p" would promise pixels that do
        // not exist anywhere in the response.
        val tiers = FormatSelection.availableTiers(fixture("youtube"))
        assertThat(tiers).containsExactly(QualityChoice.BEST, QualityChoice.AUDIO_ONLY)
    }

    @Test
    fun `a full resolution ladder offers every tier`() {
        val tiers = FormatSelection.availableTiers(fixture("youtube-4k"))
        assertThat(tiers).containsExactly(
            QualityChoice.BEST,
            QualityChoice.P1080,
            QualityChoice.P720,
            QualityChoice.P480,
            QualityChoice.AUDIO_ONLY,
        )
    }

    @Test
    fun `the highest rendition is what the hint can quote`() {
        assertThat(FormatSelection.bestHeight(fixture("youtube"))).isEqualTo(240)
        assertThat(FormatSelection.bestHeight(fixture("youtube-4k"))).isEqualTo(2160)
    }

    @Test
    fun `nothing known still offers the default`() {
        // A shared link downloaded without resolving metadata first: offer Best rather than nothing.
        assertThat(FormatSelection.availableTiers(emptyList())).containsExactly(QualityChoice.BEST)
        assertThat(FormatSelection.bestHeight(emptyList())).isNull()
    }

    @Test
    fun `a source without an audio track does not offer audio only`() {
        val silent = listOf(MediaFormat(height = 720, hasVideo = true, hasAudio = false))
        assertThat(FormatSelection.availableTiers(silent)).doesNotContain(QualityChoice.AUDIO_ONLY)
    }

    @Test
    fun `an audio-only source offers audio and nothing about pixels`() {
        val audio = listOf(MediaFormat(height = null, hasVideo = false, hasAudio = true))
        assertThat(FormatSelection.availableTiers(audio))
            .containsExactly(QualityChoice.BEST, QualityChoice.AUDIO_ONLY)
    }

    // --- arguments -------------------------------------------------------------------------

    @Test
    fun `best keeps the sort-only form the app has always used`() {
        assertThat(FormatSelection.videoFormatArgs(QualityChoice.BEST, preferProgressive = false))
            .containsExactly("-S", "vcodec:h264,res,acodec:m4a").inOrder()
    }

    @Test
    fun `a tier caps the height instead of merely preferring it`() {
        // `-S res:720` only prefers something near 720 and will return 1080p when that is closest.
        val args = FormatSelection.videoFormatArgs(QualityChoice.P720, preferProgressive = false)
        assertThat(args.joinToString(" ")).contains("height<=720")
        assertThat(args.joinToString(" ")).doesNotContain("res:720")
    }

    @Test
    fun `every tier caps at its own height`() {
        mapOf(QualityChoice.P1080 to 1080, QualityChoice.P720 to 720, QualityChoice.P480 to 480)
            .forEach { (tier, height) ->
                val args = FormatSelection.videoFormatArgs(tier, preferProgressive = false).joinToString(" ")
                assertWithMessage(tier.name).that(args).contains("height<=$height")
                assertWithMessage("${tier.name} must not cap at another tier")
                    .that(Regex("""height<=(\d+)""").findAll(args).map { it.groupValues[1] }.toSet())
                    .containsExactly(height.toString())
            }
    }

    @Test
    fun `a capped download still happens when the video exists only above the cap`() {
        // The picker hides tiers a video does not reach, but a shared link is downloaded without
        // resolving metadata. Failing outright would be the purer reading and the worse product.
        val args = FormatSelection.videoFormatArgs(QualityChoice.P480, preferProgressive = false)
        assertThat(args.joinToString(" ")).contains("/b")
    }

    @Test
    fun `the progressive fallback keeps the cap`() {
        // This is the retry after an ffmpeg merge failure; dropping the cap there would silently
        // hand back a bigger file than the one that was asked for.
        val args = FormatSelection.videoFormatArgs(QualityChoice.P720, preferProgressive = true).joinToString(" ")
        assertThat(args).contains("height<=720")
        assertThat(args).doesNotContain("+ba")
    }

    @Test
    fun `the progressive fallback for best is the single pre-muxed stream`() {
        assertThat(FormatSelection.videoFormatArgs(QualityChoice.BEST, preferProgressive = true))
            .containsExactly("-f", "best[ext=mp4]/best").inOrder()
    }

    // --- mode ------------------------------------------------------------------------------

    @Test
    fun `only audio only saves into the music collection`() {
        assertThat(FormatSelection.mode(QualityChoice.AUDIO_ONLY)).isEqualTo(DownloadMode.AUDIO)
        QualityChoice.entries.filterNot { it.isAudioOnly }.forEach {
            assertWithMessage(it.name).that(FormatSelection.mode(it)).isEqualTo(DownloadMode.VIDEO)
        }
    }
}
