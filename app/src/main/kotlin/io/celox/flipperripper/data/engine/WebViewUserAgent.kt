package io.celox.flipperripper.data.engine

import io.celox.flipperripper.domain.model.Platform

/**
 * A real mobile-Chrome user-agent for the WebViews the app drives.
 *
 * The Android System WebView's default UA carries a `; wv)` token that identifies it as an embedded
 * browser. Instagram detects that and serves embedded WebViews a degraded, often blank page — both the
 * hidden media extractor and the visible login page come back empty (a black screen) unless the UA looks
 * like an ordinary Chrome. Presenting the genuine Chrome fingerprint is also what lets the extractor
 * clear Instagram's browser check in the first place.
 */
const val MOBILE_CHROME_UA: String =
    "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36"

/**
 * Desktop Chrome. Facebook only embeds the direct `browser_native_*_url` in its **desktop** page — its
 * mobile page (what the mobile UA gets) omits it — so Facebook extraction must present a desktop UA.
 */
const val DESKTOP_CHROME_UA: String =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Safari/537.36"

/** The UA to drive the extractor WebView with: desktop for Facebook (see above), mobile otherwise. */
fun webViewUserAgent(platform: Platform): String =
    if (platform == Platform.FACEBOOK) DESKTOP_CHROME_UA else MOBILE_CHROME_UA
