package io.celox.flipperripper.domain.util

import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.Platform

/**
 * When a failure should turn into an offer to sign in to Instagram.
 *
 * Instagram hides some reels from anonymous visitors; no method can fetch those without an account.
 * The engine reports that as [DownloadError.LoginRequired]. Telling the user to go to Settings and
 * find the sign-in there, then come back and retry by hand, lost most of them — so the error itself
 * offers the sign-in, and the sign-in retries.
 *
 * Only when not signed in already: a signed-in account that still cannot see a reel will not be
 * helped by signing in again.
 */
object InstagramSignIn {
    val LOGIN_REQUIRED_KIND: String = DownloadError.LoginRequired("").kind

    fun shouldOffer(platform: Platform?, errorKind: String?, signedIn: Boolean): Boolean =
        platform == Platform.INSTAGRAM && errorKind == LOGIN_REQUIRED_KIND && !signedIn
}
