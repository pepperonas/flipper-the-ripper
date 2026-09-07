package io.celox.flipperripper.data.engine

import io.celox.flipperripper.domain.model.Platform

/**
 * The website a platform's media is served *from*. A signed CDN URL (Instagram/TikTok/Facebook) is only
 * handed out to a request that looks like it came from the site's own player, so the download must send
 * the matching `Referer`; using the wrong site's Referer is a 403. The yt-dlp-routed platforms never reach
 * the CDN downloader, but they still map to their *own* site — a foreign Referer would be the same 403.
 */
object PlatformWeb {
    fun referer(platform: Platform): String =
        when (platform) {
            Platform.INSTAGRAM -> "https://www.instagram.com/"
            Platform.TIKTOK -> "https://www.tiktok.com/"
            Platform.FACEBOOK -> "https://www.facebook.com/"
            Platform.YOUTUBE -> "https://www.youtube.com/"
            Platform.X -> "https://x.com/"
            Platform.DAILYMOTION -> "https://www.dailymotion.com/"
        }
}
