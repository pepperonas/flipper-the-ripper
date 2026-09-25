package io.celox.flipperripper.ui.login

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import io.celox.flipperripper.R
import io.celox.flipperripper.data.engine.MOBILE_CHROME_UA

/**
 * A visible WebView pointing at Instagram's own login page. The password is entered on Instagram, not
 * in the app; when the login completes, Instagram sets its `sessionid` cookie in the shared cookie
 * store, which the hidden [io.celox.flipperripper.data.engine.WebViewExtractor] then reuses. As soon as
 * that cookie appears we consider the user signed in and close the screen.
 *
 * The WebView must present a real mobile-Chrome UA ([MOBILE_CHROME_UA]): Instagram serves the default
 * WebView UA (the `wv` token) a blank page — that was the "black screen". A white background and a
 * loading spinner cover the gap before the page paints, and a render-process guard keeps a dead renderer
 * from crashing the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramLoginScreen(onDone: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.login_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.login_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        InstagramLoginPage(onSignedIn = onDone, modifier = Modifier.fillMaxSize().padding(padding))
    }
}

/**
 * Instagram's own login page in a WebView — shared by Settings and the sign-in wizard. [onSignedIn]
 * fires once, as soon as Instagram's `sessionid` cookie exists.
 */
@Composable
fun InstagramLoginPage(onSignedIn: () -> Unit, modifier: Modifier = Modifier) {
    var loading by remember { mutableStateOf(true) }
    var reported by remember { mutableStateOf(false) }
    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                CookieManager.getInstance().setAcceptCookie(true)
                @SuppressLint("SetJavaScriptEnabled")
                WebView(context).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    // Unpainted WebView area is black by default; a light backdrop stops the
                    // "black screen" look while Instagram's heavy login page hydrates.
                    setBackgroundColor(Color.WHITE)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Instagram blocks the default WebView UA; look like an ordinary mobile Chrome.
                    settings.userAgentString = MOBILE_CHROME_UA
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient =
                        object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                if (!reported && isSignedIn()) {
                                    reported = true
                                    CookieManager.getInstance().flush()
                                    onSignedIn()
                                }
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?,
                            ): Boolean {
                                // The renderer died (e.g. low memory). Reload rather than let the
                                // whole app be torn down (the default when we return false).
                                view?.reload()
                                return true
                            }
                        }
                    loadUrl(LOGIN_URL)
                }
            },
        )
        if (loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

private const val LOGIN_URL = "https://www.instagram.com/accounts/login/"

private fun isSignedIn(): Boolean =
    CookieManager.getInstance().getCookie("https://www.instagram.com")?.contains("sessionid=") == true
