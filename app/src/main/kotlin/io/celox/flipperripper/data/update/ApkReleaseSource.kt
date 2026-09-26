package io.celox.flipperripper.data.update

import io.celox.flipperripper.di.IoDispatcher
import io.celox.flipperripper.domain.model.InstallableApk
import io.celox.flipperripper.domain.repository.InstallableApkSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * The release APK for the in-app update. The product page first — it serves its own verified copy
 * (stable URL, byte ranges) and has no API rate limit — GitHub Releases as the fallback.
 */
@Singleton
class ApkReleaseSource
@Inject
constructor(@IoDispatcher private val ioDispatcher: CoroutineDispatcher) : InstallableApkSource {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    override suspend fun latest(): InstallableApk? =
        withContext(ioDispatcher) {
            get(AppUpdateChecker.SITE_LATEST_URL, null)?.let(AppReleaseParser::parseSiteApk)
                ?: get(AppUpdateChecker.RELEASES_LATEST_URL, "application/vnd.github+json")
                    ?.let(AppReleaseParser::parseGitHubApk)
        }

    override suspend fun download(apk: InstallableApk, target: File, onProgress: (Long, Long) -> Unit) =
        withContext(ioDispatcher) {
            var have = if (target.exists()) target.length() else 0L
            if (have > apk.size) {
                target.delete()
                have = 0L
            }
            if (have == apk.size) return@withContext
            val request =
                Request.Builder().url(apk.url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
            client.newCall(request).execute().use { response ->
                val body = response.body ?: throw IOException("empty response")
                // 206 = the server continues where we left off; a plain 200 means it sends everything.
                val append = have > 0 && response.code == 206
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                if (!append) have = 0L
                FileOutputStream(target, append).use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER)
                        var lastReport = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            have += n
                            if (have - lastReport >= REPORT_EVERY || have == apk.size) {
                                lastReport = have
                                onProgress(have, apk.size)
                            }
                        }
                    }
                }
            }
            if (have != apk.size) throw IOException("incomplete: $have of ${apk.size}")
        }

    private fun get(url: String, accept: String?): String? =
        runCatching {
            val request = Request.Builder().url(url).apply { accept?.let { header("Accept", it) } }.build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()

    private companion object {
        const val BUFFER = 64 * 1024
        const val REPORT_EVERY = 256 * 1024L
    }
}
