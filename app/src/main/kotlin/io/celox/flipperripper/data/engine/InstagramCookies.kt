package io.celox.flipperripper.data.engine

/**
 * Builds the `Cookie` header sent with a CDN media download.
 *
 * A browser would send only the cookies scoped to the CDN host, but Instagram gates some signed-in media
 * on the *account* cookies (`sessionid`, `ds_user_id`, `csrftoken`). Those live on the `instagram.com`
 * domain, so they are absent from the CDN host's own jar and a bare CDN request 403s. [merge] adds them —
 * but only when actually signed in (the instagram.com jar carries a `sessionid`), so the logged-out public
 * request is byte-identical to sending the CDN cookies alone.
 */
object InstagramCookies {
    private val SESSION_COOKIE_NAMES = setOf("sessionid", "ds_user_id", "csrftoken")

    /**
     * @param cdnCookie the CDN host's own cookie string (`CookieManager.getCookie(mediaUrl)`)
     * @param instagramCookie the instagram.com jar (`CookieManager.getCookie("https://www.instagram.com")`)
     */
    fun merge(cdnCookie: String?, instagramCookie: String?): String {
        val cdnPairs = pairsOf(cdnCookie)
        val cdnNames = cdnPairs.map { it.substringBefore('=') }.toSet()
        val jar = instagramCookie.orEmpty()
        val sessionPairs =
            if (!jar.contains("sessionid=")) {
                emptyList()
            } else {
                pairsOf(jar).filter {
                    it.substringBefore('=') in SESSION_COOKIE_NAMES && it.substringBefore('=') !in cdnNames
                }
            }
        return (cdnPairs + sessionPairs).joinToString("; ")
    }

    private fun pairsOf(cookie: String?): List<String> =
        cookie.orEmpty().split(";").map { it.trim() }.filter { it.isNotBlank() }
}
