package io.celox.flipperripper.data.update

import io.celox.flipperripper.di.IoDispatcher
import io.celox.flipperripper.domain.model.AppUpdate
import io.celox.flipperripper.domain.repository.AppReleaseSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the newest published app release. Best-effort: any network or parse problem returns null —
 * an update notice is a convenience, never worth an error state.
 *
 * The product page's `latest.json` comes first: it is mirrored from GitHub Releases every 15 minutes
 * and, unlike the GitHub API, has no 60-requests-per-hour limit per IP — which a background check
 * on many phones behind one carrier NAT would run into. GitHub is the fallback when the site is down.
 */
@Singleton
class AppUpdateChecker
@Inject
constructor(@IoDispatcher private val ioDispatcher: CoroutineDispatcher) : AppReleaseSource {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    override suspend fun fetchLatestRelease(): AppUpdate? =
        withContext(ioDispatcher) {
            get(SITE_LATEST_URL, null)?.let(AppReleaseParser::parseSite)
                ?: get(RELEASES_LATEST_URL, "application/vnd.github+json")?.let(AppReleaseParser::parse)
        }

    private fun get(url: String, accept: String?): String? =
        runCatching {
            val request = Request.Builder().url(url).apply { accept?.let { header("Accept", it) } }.build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()

    companion object {
        const val SITE_LATEST_URL = "https://flipper-the-ripper.celox.io/latest.json"
        const val RELEASES_LATEST_URL =
            "https://api.github.com/repos/pepperonas/flipper-the-ripper/releases/latest"

        /** Always the newest APK (302 to the current release asset) — what a notification tap opens. */
        const val DOWNLOAD_URL = "https://flipper-the-ripper.celox.io/download"
    }
}
