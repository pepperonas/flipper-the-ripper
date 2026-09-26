package io.celox.flipperripper.data.update

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.AppUpdate
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

    @Test
    fun `the product page's latest json gives version and release notes`() {
        val body = """{ "version": "v1.12.0", "published": "2026-09-26T00:00:00Z",
            "notes": "https://github.com/pepperonas/flipper-the-ripper/releases/tag/v1.12.0",
            "assets": [] }"""
        assertThat(AppReleaseParser.parseSite(body))
            .isEqualTo(AppUpdate("v1.12.0", "https://github.com/pepperonas/flipper-the-ripper/releases/tag/v1.12.0"))
    }

    @Test
    fun `a latest json without an https notes link is not trusted`() {
        assertThat(AppReleaseParser.parseSite("""{ "version": "v1.12.0", "notes": "http://evil.example" }""")).isNull()
        assertThat(AppReleaseParser.parseSite("""{ "version": "v1.12.0" }""")).isNull()
        assertThat(AppReleaseParser.parseSite("""{ "notes": "https://github.com/x" }""")).isNull()
        assertThat(AppReleaseParser.parseSite("<html>502 Bad Gateway</html>")).isNull()
    }

    private val sha = "ed7e6b718067f1309b063b6b4320ce4b55aa68efa81e23948ecd1a26de2ad27f"

    private fun site(url: String = "https://flipper-the-ripper.celox.io/apk/flipper-the-ripper-v1.13.0.apk", sha256: String = sha) =
        """
        {"version":"v1.13.0","name":"flipper-the-ripper-v1.13.0.apk","url":"$url","size":56308305,
         "sha256":"$sha256","notes":"https://github.com/pepperonas/flipper-the-ripper/releases/tag/v1.13.0"}
        """.trimIndent()

    @Test
    fun `the site's latest json yields the installable apk`() {
        val apk = AppReleaseParser.parseSiteApk(site())!!
        assertThat(apk.version).isEqualTo("v1.13.0")
        assertThat(apk.size).isEqualTo(56_308_305L)
        assertThat(apk.sha256).isEqualTo(sha)
        assertThat(apk.url).endsWith("/apk/flipper-the-ripper-v1.13.0.apk")
    }

    @Test
    fun `the GitHub asset url is accepted as well`() {
        val gh = "$rel/flipper-the-ripper-v1.13.0.apk"
        assertThat(AppReleaseParser.parseSiteApk(site(url = gh))).isNotNull()
    }

    @Test
    fun `an apk from anywhere else is never offered for install`() {
        val foreign = listOf(
            "https://evil.example/apk/flipper-the-ripper-v1.13.0.apk",
            "http://flipper-the-ripper.celox.io/apk/flipper-the-ripper-v1.13.0.apk",
            "https://flipper-the-ripper.celox.io.evil.example/apk/flipper-the-ripper-v1.13.0.apk",
            "https://github.com/someone-else/flipper-the-ripper/releases/download/v1/flipper-the-ripper-v1.13.0.apk",
            // The URL must name the file it claims to be.
            "https://flipper-the-ripper.celox.io/apk/other.apk",
        )
        foreign.forEach { assertThat(AppReleaseParser.parseSiteApk(site(url = it))).isNull() }
    }

    @Test
    fun `without a well-formed sha256 there is nothing to verify, so nothing to install`() {
        assertThat(AppReleaseParser.parseSiteApk(site(sha256 = ""))).isNull()
        assertThat(AppReleaseParser.parseSiteApk(site(sha256 = "abc"))).isNull()
        assertThat(AppReleaseParser.parseSiteApk(site(sha256 = "z".repeat(64)))).isNull()
        // Upper case from some tool is still the same digest.
        assertThat(AppReleaseParser.parseSiteApk(site(sha256 = sha.uppercase()))?.sha256).isEqualTo(sha)
    }

    private val rel = "https://github.com/pepperonas/flipper-the-ripper/releases/download/v1.13.0"

    private fun github(digest: String?, extraAsset: String = "") =
        """
        {"tag_name":"v1.13.0","draft":false,"prerelease":false,"assets":[
          {"name":"SHA256SUMS.txt","size":90,"browser_download_url":"$rel/SHA256SUMS.txt"},
          {"name":"flipper-the-ripper-v1.13.0.apk","size":56308305,
           ${digest?.let { "\"digest\":\"$it\"," } ?: ""}
           "browser_download_url":"$rel/flipper-the-ripper-v1.13.0.apk"}
          $extraAsset]}
        """.trimIndent()

    @Test
    fun `the GitHub API fallback uses the digest GitHub computed`() {
        val apk = AppReleaseParser.parseGitHubApk(github("sha256:$sha"))!!
        assertThat(apk.sha256).isEqualTo(sha)
        assertThat(apk.name).isEqualTo("flipper-the-ripper-v1.13.0.apk")
    }

    @Test
    fun `a GitHub release without a digest or with two apks is not installable`() {
        assertThat(AppReleaseParser.parseGitHubApk(github(null))).isNull()
        assertThat(AppReleaseParser.parseGitHubApk(github("md5:abc"))).isNull()
        val second =
            """,{"name":"flipper-the-ripper-v1.13.0-x.apk","size":1,"digest":"sha256:$sha",
              "browser_download_url":"$rel/flipper-the-ripper-v1.13.0-x.apk"}"""
        assertThat(AppReleaseParser.parseGitHubApk(github("sha256:$sha", second))).isNull()
    }
}
