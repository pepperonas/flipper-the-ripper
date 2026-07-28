package io.celox.flipperripper.data.engine

import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's Instagram session, held in the app-global [CookieManager].
 *
 * Some reels are only viewable to a logged-in account (a private or otherwise gated post). No anonymous
 * method — on-device or server — can fetch those; that is a property of the content, not a bug. Letting
 * the user sign in to Instagram once inside the app makes the [WebViewExtractor]'s hidden WebView carry
 * that session (all WebViews share one cookie store), so it can then read anything the user's own
 * account can see. The app never handles the password — the login happens on Instagram's own page and
 * only the resulting session cookie is kept, exactly as a browser would.
 */
@Singleton
class InstagramSession
@Inject
constructor() {
    // Starts false and is filled in by refresh(); the cookie store is only touched when actually used,
    // which keeps construction free of Android dependencies.
    private val _loggedIn = MutableStateFlow(false)

    /** Observable so Settings can reflect the state. */
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    fun isLoggedIn(): Boolean = readLoggedIn()

    /** Re-read the cookie store (call after the login screen closes). */
    fun refresh() {
        _loggedIn.value = readLoggedIn()
    }

    fun signOut() {
        val cm = CookieManager.getInstance()
        // Expire the session cookies for the Instagram domains.
        for (host in HOSTS) {
            val existing = cm.getCookie(host) ?: continue
            existing.split(";").map { it.substringBefore("=").trim() }.forEach { name ->
                cm.setCookie(host, "$name=; Max-Age=0; Path=/")
            }
        }
        cm.flush()
        _loggedIn.value = false
    }

    private fun readLoggedIn(): Boolean {
        val cm = CookieManager.getInstance()
        // `sessionid` is present only for an authenticated session.
        return HOSTS.any { host -> cm.getCookie(host)?.contains("sessionid=") == true }
    }

    private companion object {
        val HOSTS = listOf("https://www.instagram.com", "https://instagram.com")
    }
}
