package io.celox.flipperripper.data.engine

/**
 * Which navigations the extractor's hidden WebView is allowed to follow.
 *
 * TikTok answers a mobile browser with a redirect to `snssdk1340://aweme/detail/…` — a deep link
 * meant to hand the visit over to the installed TikTok app ("click_wap_silence_awaken"). A WebView
 * cannot load that scheme: it follows the redirect anyway, fails, and ends up on
 * `chrome-error://chromewebdata` with the title "Webpage not available". The video page we were
 * reading is gone before the extraction script finds anything, and the only visible symptom is a
 * timeout that names no cause.
 *
 * So the rule is the narrow one: a hidden extractor may only ever load a web page. Anything else —
 * an app deep link, `intent://`, `market://`, a `tel:` — is refused, and the page we asked for stays
 * the page we are on. Nothing is opened on the user's behalf either; this WebView exists to read one
 * page, not to launch apps.
 */
object WebNavigation {
    private val LOADABLE = setOf("http", "https")

    /** True when [scheme] is a web page the WebView can actually render. */
    fun isLoadablePage(scheme: String?): Boolean = scheme?.lowercase() in LOADABLE
}
