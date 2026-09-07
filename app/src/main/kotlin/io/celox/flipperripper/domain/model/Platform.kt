package io.celox.flipperripper.domain.model

/**
 * A supported source platform.
 *
 * Detection mirrors `detect_platform()` in inspector-rust (`core/rust-lib/src/social_dl.rs`):
 * a lowercase host match. The actual extraction is delegated to the download engines, so the set
 * of *downloadable* URLs is ultimately whatever those support — this enum only drives UI
 * labelling, per-platform routing and the "is this a link we offer to download" gate.
 */
enum class Platform(val displayName: String) {
    YOUTUBE("YouTube"),
    INSTAGRAM("Instagram"),
    TIKTOK("TikTok"),
    FACEBOOK("Facebook"),
    X("X"),
    DAILYMOTION("Dailymotion"),
    ;

    companion object {
        /**
         * Registrable domains that map to a platform. A host matches when it *is* the domain or a
         * subdomain of it (`m.facebook.com`, `vm.tiktok.com`, `mobile.twitter.com`) — never by mere
         * substring: `x.com` is the tail of `netflix.com` and `dropbox.com`, and `youtube.com.evil.example`
         * must not pass as YouTube.
         */
        private val HOST_MATCHERS: List<Pair<String, Platform>> =
            listOf(
                "youtube.com" to YOUTUBE,
                "youtu.be" to YOUTUBE,
                "instagram.com" to INSTAGRAM,
                "tiktok.com" to TIKTOK,
                // fb.watch (share links) + fb.com are separate hosts, not facebook.com subdomains.
                "facebook.com" to FACEBOOK,
                "fb.watch" to FACEBOOK,
                "fb.com" to FACEBOOK,
                // X still answers on its pre-rebrand host, and apps in the wild still share twitter.com links.
                "x.com" to X,
                "twitter.com" to X,
                // dai.ly is Dailymotion's own share shortener.
                "dailymotion.com" to DAILYMOTION,
                "dai.ly" to DAILYMOTION,
            )

        internal fun matchHost(host: String): Platform? {
            val h = host.lowercase().trimEnd('.')
            return HOST_MATCHERS.firstOrNull { (domain, _) -> h == domain || h.endsWith(".$domain") }?.second
        }
    }
}
