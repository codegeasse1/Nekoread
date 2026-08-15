package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.isOutdated
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

interface CloudflareChallengeResolver {
    fun resolve(originalRequest: Request, oldCookie: Cookie?)
}

/**
 * Resolves a Cloudflare challenge by loading the request URL in a WebView.
 *
 *  - Non-interactive interstitial challenges ("Just a moment…") are solved silently in a hidden
 *    WebView and the resulting `cf_clearance` cookie is picked up automatically.
 *  - Interactive challenges (Turnstile / "I am not a robot") surface a fullscreen dialog
 *    ([CloudflareChallengeDialog]) so the user can complete them by hand; the dialog polls the
 *    cookie store and auto-dismisses + retries the moment a fresh `cf_clearance` appears — no
 *    hunting for a "verify" button on whatever screen the request happened on.
 */
internal class WebViewCloudflareChallengeResolver(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    private val mainExecutor: Executor,
    private val createWebView: (Request) -> WebView,
    private val parseHeaders: (Headers) -> Map<String, String>,
    private val isWebViewOutdated: (WebView) -> Boolean,
) : CloudflareChallengeResolver {

    @SuppressLint("SetJavaScriptEnabled")
    override fun resolve(originalRequest: Request, oldCookie: Cookie?) {
        val latch = CountDownLatch(1)
        val origRequestUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)

        var webview: WebView? = null
        var challengeDialog: CloudflareChallengeDialog? = null
        var cloudflareBypassed = false
        var isWebViewOutdatedNow = false

        mainExecutor.execute {
            val createdWebView = createWebView(originalRequest)
            webview = createdWebView

            fun hasFreshClearance(url: okhttp3.HttpUrl): Boolean {
                val cookie = cookieManager.get(url).firstOrNull { it.name == "cf_clearance" }
                return cookie != null && (url.host != originalRequest.url.host || cookie != oldCookie)
            }

            fun isSolved(): Boolean {
                return listOfNotNull(originalRequest.url, createdWebView.url?.toHttpUrlOrNull())
                    .distinctBy { it.host }
                    .any { hasFreshClearance(it) }
            }

            fun finishSolved() {
                if (!cloudflareBypassed) {
                    cloudflareBypassed = true
                    CookieManager.getInstance().flush()
                    latch.countDown()
                }
            }

            fun probeAndShowDialog(view: WebView) {
                if (cloudflareBypassed || challengeDialog != null) return
                detectInteractiveWidget(view) { detected ->
                    if (detected && !cloudflareBypassed && challengeDialog == null) {
                        val dlg = CloudflareChallengeDialog(
                            context = context,
                            webView = createdWebView,
                            isSolved = ::isSolved,
                            onSolved = ::finishSolved,
                            onDismissed = { latch.countDown() },
                        )
                        challengeDialog = dlg
                        dlg.show()
                    }
                }
            }

            createdWebView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (isSolved()) {
                        finishSolved()
                        return
                    }

                    // Interactive widget present -> let the user complete it in a visible dialog
                    // (auto-closes the moment the challenge is solved). Turnstile renders its
                    // widget asynchronously, so re-probe for a few seconds after page finish.
                    if (challengeDialog == null) {
                        probeAndShowDialog(view)
                        view.postDelayed({ probeAndShowDialog(view) }, 1500)
                        view.postDelayed({ probeAndShowDialog(view) }, 3500)
                        view.postDelayed({ probeAndShowDialog(view) }, 6000)
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame && challengeDialog == null) {
                        latch.countDown()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    if (request.isForMainFrame && challengeDialog == null) {
                        if (errorResponse.statusCode !in ERROR_CODES) {
                            latch.countDown()
                        }
                    }
                }
            }

            createdWebView.loadUrl(origRequestUrl, headers)

            // Bail quickly (like Tadami's 30s) for challenges that never became interactive.
            Handler(Looper.getMainLooper()).postDelayed({
                if (!cloudflareBypassed && challengeDialog == null) {
                    latch.countDown()
                }
            }, SILENT_SOLVE_TIMEOUT_MS)
        }

        latch.await(120, TimeUnit.SECONDS)

        mainExecutor.execute {
            if (!cloudflareBypassed) {
                isWebViewOutdatedNow = webview?.let(isWebViewOutdated) == true
            }
            challengeDialog?.dismiss()
            webview?.run {
                stopLoading()
                destroy()
            }
        }

        if (!cloudflareBypassed) {
            if (isWebViewOutdatedNow) {
                Toast.makeText(
                    context,
                    "Your WebView is outdated — update it (Play Store → Android System WebView) to complete Cloudflare challenges",
                    Toast.LENGTH_LONG,
                ).show()
            } else if (challengeDialog != null) {
                Toast.makeText(
                    context,
                    "Cloudflare verification not completed — try again",
                    Toast.LENGTH_LONG,
                ).show()
            }
            throw CloudflareBypassException()
        }
    }

    private fun detectInteractiveWidget(webview: WebView, onResult: (Boolean) -> Unit) {
        try {
            webview.evaluateJavascript(INTERACTIVE_WIDGET_PROBE) { result ->
                onResult(result == "true")
            }
        } catch (_: Throwable) {
            onResult(false)
        }
    }
}

internal val INTERACTIVE_WIDGET_PROBE = """
    (function() {
        try {
            return document.querySelector('.cf-turnstile, [data-sitekey], iframe[src*="challenges.cloudflare.com"]') != null;
        } catch (_) {
            return false;
        }
    })();
""".trimIndent()

private const val SILENT_SOLVE_TIMEOUT_MS = 35_000L

internal open class CloudflareBypassException : Exception()
internal class CloudflareInteractiveChallengeException : CloudflareBypassException()
