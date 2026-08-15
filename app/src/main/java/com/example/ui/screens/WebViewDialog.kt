package com.example.ui.screens

import android.os.Message
import android.view.ViewGroup
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Fullscreen overlay used for Cloudflare / DDoS-Guard / site verification, shown as a Dialog so
 * closing it always returns exactly where the user was (the extension catalog, the reader, ...) —
 * no navigation state to lose. Cookies collected here go into the shared CookieManager, which the
 * extension clients (and the automatic CloudflareInterceptor) reuse.
 *
 * [userAgent] mirrors the extension request's User-Agent so a manually solved `cf_clearance`
 * binds to the same UA the app's requests actually send (otherwise Cloudflare still 403s them).
 *
 * Behaviour mirrors Tadami's webview screen:
 *  - the `X-Requested-With: <package>` WebView fingerprint is cleared, otherwise Cloudflare keeps
 *    looping the Turnstile challenge forever;
 *  - main-frame GET navigations (the challenge's post-solve redirect/reload) are re-issued with the
 *    source headers so the cookies/UA survive the flow;
 *  - popups (Cloudflare's managed challenge can use them) are rendered in their own WebView.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewDialog(
    url: String,
    userAgent: String?,
    onDismiss: () -> Unit,
) {
    var container by remember { mutableStateOf<FrameLayout?>(null) }
    var mainWebView by remember { mutableStateOf<WebView?>(null) }
    var popups by remember { mutableStateOf<List<WebView>>(emptyList()) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(url) }

    val headers = remember(userAgent) {
        if (userAgent.isNotBlank()) mapOf("User-Agent" to userAgent) else emptyMap()
    }

    fun configureWebView(wv: WebView) {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // Handle popups properly (needed for Cloudflare Turnstile).
            setSupportMultipleWindows(true)
            // Allow zooming.
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        if (userAgent?.isNotBlank() == true) {
            wv.settings.userAgentString = userAgent
        }
        // Don't send X-Requested-With: <package> — Cloudflare uses it as a WebView fingerprint
        // and loops the challenge for WebViews that leak it.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(wv.settings, emptySet())
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }
    }

    fun newPopupWebView(): WebView? {
        val ctx = container?.context ?: return null
        val popup = WebView(ctx)
        configureWebView(popup)
        popup.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, finishedUrl: String?) {
                super.onPageFinished(view, finishedUrl)
                CookieManager.getInstance().flush()
            }
        }
        return popup
    }

    val chromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            progress = newProgress
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            // Render popups (used by Cloudflare's managed challenge) as their own WebView on top.
            val popup = newPopupWebView() ?: return false
            container?.addView(
                popup,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            popups = popups + popup
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            transport.webView = popup
            resultMsg.sendToTarget()
            return true
        }

        override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            result.confirm()
            return true
        }

        override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            result.confirm()
            return true
        }

        override fun onJsPrompt(
            view: WebView,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult,
        ): Boolean {
            result.cancel()
            return true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF12141F),
            contentColor = Color.White,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    windowInsets = WindowInsets(0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1B1E2C),
                    ),
                    title = {
                        Column {
                            Text(
                                text = "Cloudflare / Site Verification",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = currentUrl,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF9EA6C1),
                                ),
                                maxLines = 1,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                when {
                                    popups.isNotEmpty() -> {
                                        val p = popups.last()
                                        popups = popups.dropLast(1)
                                        container?.removeView(p)
                                        p.destroy()
                                    }
                                    canGoBack -> mainWebView?.goBack()
                                    else -> onDismiss()
                                }
                            },
                            modifier = Modifier.testTag("webview_back_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { mainWebView?.reload() },
                            modifier = Modifier.testTag("webview_reload_button"),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload")
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("webview_close_button"),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Done")
                        }
                    },
                )
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("webview_progress"),
                )

                AndroidView(
                    factory = { ctx ->
                        val frame = FrameLayout(ctx)
                        container = frame
                        val main = WebView(ctx).apply {
                            configureWebView(this)

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView, startedUrl: String?, favicon: Bitmap?) {
                                    currentUrl = startedUrl ?: currentUrl
                                }

                                override fun onPageFinished(view: WebView, finishedUrl: String?) {
                                    currentUrl = finishedUrl ?: currentUrl
                                    canGoBack = view.canGoBack()
                                    CookieManager.getInstance().flush()
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    val reqUrl = request.url.toString()
                                    // Preserve the source headers (User-Agent etc.) on main-frame
                                    // GET navigations like the challenge's post-solve redirect,
                                    // otherwise Cloudflare re-issues the challenge forever.
                                    if (request.isForMainFrame &&
                                        request.method.equals("GET", ignoreCase = true) &&
                                        (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) &&
                                        reqUrl != view.url
                                    ) {
                                        view.loadUrl(reqUrl, headers)
                                        return true
                                    }
                                    return false
                                }
                            }
                            webChromeClient = chromeClient
                        }
                        mainWebView = main
                        frame.addView(
                            main,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        frame
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("webview_container"),
                )
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            popups.isNotEmpty() -> {
                val p = popups.last()
                popups = popups.dropLast(1)
                container?.removeView(p)
                p.destroy()
            }
            canGoBack -> mainWebView?.goBack()
            else -> onDismiss()
        }
    }

    LaunchedEffect(mainWebView) {
        mainWebView?.loadUrl(url, headers)
    }

    DisposableEffect(Unit) {
        onDispose {
            mainWebView?.stopLoading()
            mainWebView?.loadUrl("about:blank")
            mainWebView?.destroy()
            for (p in popups) {
                p.stopLoading()
                p.loadUrl("about:blank")
                p.destroy()
            }
        }
    }
}
