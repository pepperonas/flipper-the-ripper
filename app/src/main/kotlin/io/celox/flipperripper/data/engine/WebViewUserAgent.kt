package io.celox.flipperripper.data.engine

/**
 * A real mobile-Chrome user-agent for every WebView the app drives.
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
