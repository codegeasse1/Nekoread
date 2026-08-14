package com.example.ui.screens

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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

/**
 * Fullscreen overlay used for Cloudflare / DDoS-Guard / site verification, shown as a Dialog so
 * closing it always returns exactly where the user was (the extension catalog, the reader, ...) —
 * no navigation state to lose. Cookies collected here go into the shared CookieManager, which the
 * extension clients (and the automatic CloudflareInterceptor) reuse.
 *
 * [userAgent] mirrors the extension request's User-Agent so a manually solved `cf_clearance`
 * binds to the same UA the app's requests actually send (otherwise Cloudflare still 403s them).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewDialog(
    url: String,
    userAgent: String?,
    onDismiss: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }

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
                                text = "Complete the check, then close — the source will work in-app",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF9EA6C1),
                                ),
                                maxLines = 1,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { if (canGoBack) webView?.goBack() else onDismiss() },
                            modifier = Modifier.testTag("webview_back_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { webView?.reload() },
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
                        val wv = WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.setSupportMultipleWindows(true)
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            if (userAgent?.isNotBlank() == true) {
                                settings.userAgentString = userAgent
                            }

                            // Share cookies with OkHttp (AndroidCookieJar) — the whole point.
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            webViewClient = WebViewClient()
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                        }
                        webView = wv
                        wv
                    },
                    update = { wv ->
                        webView = wv
                        canGoBack = wv.canGoBack()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("webview_container"),
                )
            }
        }
    }

    BackHandler(enabled = true) {
        if (canGoBack) webView?.goBack() else onDismiss()
    }

    LaunchedEffect(webView) {
        webView?.loadUrl(url)
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
            webView?.destroy()
        }
    }
}
