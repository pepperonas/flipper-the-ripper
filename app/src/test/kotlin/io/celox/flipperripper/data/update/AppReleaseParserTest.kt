package io.celox.flipperripper.data.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppReleaseParserTest {
    @Test
    fun `parses tag and url from a github release`() {
        val body =
            """
            {
              "tag_name": "v1.3.2",
              "html_url": "https://github.com/pepperonas/flipper-the-ripper/releases/tag/v1.3.2",
              "name": "Flipper the Ripper v1.3.2",
              "draft": false,
              "prerelease": false,
              "assets": [{"name": "app-arm64-v8a-release.apk"}]
            }
            """.trimIndent()
        val release = AppReleaseParser.parse(body)
        assertThat(release?.version).isEqualTo("v1.3.2")
        assertThat(release?.url)
            .isEqualTo("https://github.com/pepperonas/flipper-the-ripper/releases/tag/v1.3.2")
    }

    @Test
    fun `drafts and prereleases are never offered as updates`() {
        val draft = """{"tag_name":"v9.9.9","html_url":"https://x","draft":true,"prerelease":false}"""
        val pre = """{"tag_name":"v9.9.9","html_url":"https://x","draft":false,"prerelease":true}"""
        assertThat(AppReleaseParser.parse(draft)).isNull()
        assertThat(AppReleaseParser.parse(pre)).isNull()
    }

    @Test
    fun `missing fields and malformed json return null instead of throwing`() {
        assertThat(AppReleaseParser.parse("""{"html_url":"https://x"}""")).isNull()
        assertThat(AppReleaseParser.parse("""{"tag_name":"v1.0.0"}""")).isNull()
        assertThat(AppReleaseParser.parse("""{"tag_name":"","html_url":""}""")).isNull()
        assertThat(AppReleaseParser.parse("not json at all")).isNull()
        assertThat(AppReleaseParser.parse("""{"message":"Not Found"}""")).isNull()
    }
}
