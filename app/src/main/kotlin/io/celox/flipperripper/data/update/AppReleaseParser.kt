package io.celox.flipperripper.data.update

import io.celox.flipperripper.domain.model.AppUpdate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
}
