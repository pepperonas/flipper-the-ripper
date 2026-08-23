package io.celox.flipperripper.data.update

import io.celox.flipperripper.di.IoDispatcher
import io.celox.flipperripper.domain.model.AppUpdate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the newest published app release from GitHub. Best-effort: any network or parse problem
 * returns null — an update notice is a convenience, never worth an error state.
 */
@Singleton
class AppUpdateChecker
@Inject
constructor(@IoDispatcher private val ioDispatcher: CoroutineDispatcher) {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    suspend fun fetchLatestRelease(): AppUpdate? =
        withContext(ioDispatcher) {
            runCatching {
                val request =
                    Request.Builder()
                        .url(RELEASES_LATEST_URL)
                        .header("Accept", "application/vnd.github+json")
                        .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    response.body?.string()?.let(AppReleaseParser::parse)
                }
            }.getOrNull()
        }

    private companion object {
        const val RELEASES_LATEST_URL =
            "https://api.github.com/repos/pepperonas/flipper-the-ripper/releases/latest"
    }
}
