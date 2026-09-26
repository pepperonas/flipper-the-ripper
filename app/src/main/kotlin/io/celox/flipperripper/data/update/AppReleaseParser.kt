package io.celox.flipperripper.data.update

import io.celox.flipperripper.domain.model.AppUpdate
import io.celox.flipperripper.domain.model.InstallableApk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Parses the GitHub `releases/latest` API response. Pure (string in, value out) so the shape
 * assumptions are pinned by unit tests instead of failing silently in the background checker.
 */
object AppReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): AppUpdate? =
        runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            // Drafts and prereleases are not updates we should push at users.
            if (obj["draft"]?.jsonPrimitive?.booleanOrNull == true) return@runCatching null
            if (obj["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return@runCatching null
            val tag = obj["tag_name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val url = obj["html_url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            if (tag == null || url == null) null else AppUpdate(tag, url)
        }.getOrNull()

    /**
     * Parses the product page's `latest.json` (written from GitHub Releases by the site's timer):
     * `{ "version": "v1.12.0", "notes": "https://github.com/…/releases/tag/v1.12.0", … }`. Only
     * published releases ever reach that file, so there are no draft/prerelease flags to check.
     */
    fun parseSite(body: String): AppUpdate? =
        runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            val version = obj["version"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val notes = obj["notes"]?.jsonPrimitive?.content?.takeIf { it.startsWith("https://") }
            if (version == null || notes == null) null else AppUpdate(version, notes)
        }.getOrNull()

    /** The release file name the workflow produces. Anything else is not the app. */
    private val APK_NAME = Regex("^flipper-the-ripper-v[0-9][0-9A-Za-z.\\-]*\\.apk$")
    private val SHA256 = Regex("^[0-9a-f]{64}$")

    /** The only places an APK may be fetched from: the product page's own copy and GitHub Releases. */
    private val APK_URL_PREFIXES =
        listOf(
            "https://flipper-the-ripper.celox.io/apk/",
            "https://github.com/pepperonas/flipper-the-ripper/releases/download/",
        )

    /** The installable APK from the product page's `latest.json` (`url`, `name`, `size`, `sha256`). */
    fun parseSiteApk(body: String): InstallableApk? =
        runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            apkOf(obj.text("version"), obj.text("name"), obj.text("url"), obj.size(), obj.text("sha256"))
        }.getOrNull()

    /**
     * The installable APK from the GitHub `releases/latest` API: the one asset named like a release APK,
     * with the SHA-256 GitHub computes itself (`digest: "sha256:…"`). No digest means nothing to verify
     * against, and an unverified file is never installed.
     */
    fun parseGitHubApk(body: String): InstallableApk? =
        runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            if (obj["draft"]?.jsonPrimitive?.booleanOrNull == true) return@runCatching null
            if (obj["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return@runCatching null
            val asset =
                obj["assets"]?.jsonArray?.map { it.jsonObject }
                    ?.singleOrNull { APK_NAME.matches(it.text("name")) }
                    ?: return@runCatching null
            val digest = asset.text("digest")
            if (!digest.startsWith("sha256:")) return@runCatching null
            apkOf(
                obj.text("tag_name"),
                asset.text("name"),
                asset.text("browser_download_url"),
                asset.size(),
                digest.removePrefix("sha256:"),
            )
        }.getOrNull()

    private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.content.orEmpty()

    private fun JsonObject.size(): Long = this["size"]?.jsonPrimitive?.longOrNull ?: 0L

    private fun apkOf(version: String, name: String, url: String, size: Long, sha256: String): InstallableApk? {
        val sha = sha256.lowercase()
        val ok =
            version.isNotBlank() && APK_NAME.matches(name) && size > 0 && SHA256.matches(sha) &&
                APK_URL_PREFIXES.any { url.startsWith(it) } && url.endsWith("/$name")
        return if (ok) InstallableApk(version, name, url, size, sha) else null
    }
}
