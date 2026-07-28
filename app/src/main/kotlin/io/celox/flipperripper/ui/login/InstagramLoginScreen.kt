package io.celox.flipperripper.ui.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import io.celox.flipperripper.R

/**
 * A visible WebView pointing at Instagram's own login page. The password is entered on Instagram, not
 * in the app; when the login completes, Instagram sets its `sessionid` cookie in the shared cookie
 * store, which the hidden [io.celox.flipperripper.data.engine.WebViewExtractor] then reuses. As soon as
 * that cookie appears we consider the user signed in and close the screen.
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
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { context ->
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                }
                @SuppressLint("SetJavaScriptEnabled")
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient =
                        object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (isSignedIn()) {
                                    CookieManager.getInstance().flush()
                                    onDone()
                                }
                            }
                        }
                    loadUrl("https://www.instagram.com/accounts/login/")
                }
            },
        )
    }
}

private fun isSignedIn(): Boolean =
    CookieManager.getInstance().getCookie("https://www.instagram.com")?.contains("sessionid=") == true
