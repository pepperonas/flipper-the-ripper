package io.celox.flipperripper.ui.settings

import java.net.URLEncoder

/**
 * The facts the About section shows — kept pure so a unit test can pin them and so nothing about
 * the author, the repository or the licence is typed twice.
 */
object AboutLinks {
    const val AUTHOR = "Martin Pfeffer"
    const val WEBSITE_LABEL = "celox.io"
    const val WEBSITE_URL = "https://celox.io"
    const val REPO_URL = "https://github.com/pepperonas/flipper-the-ripper"
    const val LICENSE_NAME = "MIT License"
    const val LICENSE_URL = "$REPO_URL/blob/main/LICENSE"
    const val DONATE_EMAIL = "martin.pfeffer@celox.io"

    /**
     * PayPal's hosted donate link for a plain e-mail recipient (the same form the author's other
     * projects use), with the project name as the payment note.
     */
    fun donateUrl(itemName: String = "Flipper the Ripper", email: String = DONATE_EMAIL): String =
        "https://www.paypal.com/donate/?business=$email&currency_code=EUR&item_name=" +
            URLEncoder.encode(itemName, "UTF-8").replace("+", "%20")
}
