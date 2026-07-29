package io.celox.flipperripper.data.engine

import io.celox.flipperripper.domain.model.Platform

/**
 * The website a platform's media is served *from*. A signed CDN URL (Instagram/TikTok/Facebook) is only
 * handed out to a request that looks like it came from the site's own player, so the download must send
 * the matching `Referer`; using the wrong site's Referer is a 403. Only the WebView-routed platforms are
 * mapped — YouTube never reaches the CDN downloader.
 */
object PlatformWeb {
    fun referer(platform: Platform): String =
        when (platform) {
            Platform.TIKTOK -> "https://www.tiktok.com/"
            Platform.FACEBOOK -> "https://www.facebook.com/"
            Platform.INSTAGRAM, Platform.YOUTUBE -> "https://www.instagram.com/"
        }
}
