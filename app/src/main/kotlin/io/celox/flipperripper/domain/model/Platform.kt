package io.celox.flipperripper.domain.model

/**
 * A supported source platform.
 *
 * Detection mirrors `detect_platform()` in inspector-rust (`core/rust-lib/src/social_dl.rs`):
 * a lowercase host-substring match. The actual extraction is delegated to the yt-dlp engine,
 * so the set of *downloadable* URLs is ultimately whatever yt-dlp supports — this enum only
 * drives UI labelling and the "is this a link we offer to download" gate.
 */
enum class Platform(val displayName: String) {
    YOUTUBE("YouTube"),
    INSTAGRAM("Instagram"),
    TIKTOK("TikTok"),
    FACEBOOK("Facebook"),
    ;

    companion object {
        /** Host substrings that map to a platform, longest/most-specific first. */
        private val HOST_MATCHERS: List<Pair<String, Platform>> =
            listOf(
                "youtube.com" to YOUTUBE,
                "youtu.be" to YOUTUBE,
                "instagram.com" to INSTAGRAM,
                "tiktok.com" to TIKTOK,
                // m./web./www. all contain "facebook.com"; fb.watch (share links) + fb.com are separate.
                "facebook.com" to FACEBOOK,
                "fb.watch" to FACEBOOK,
                "fb.com" to FACEBOOK,
            )

        internal fun matchHost(host: String): Platform? {
            val h = host.lowercase()
            return HOST_MATCHERS.firstOrNull { (needle, _) -> h.contains(needle) }?.second
        }
    }
}
