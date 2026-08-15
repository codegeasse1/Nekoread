package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.content.pm.PackageManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Base interceptor that spins up a hidden WebView to solve anti-bot challenges (Cloudflare and
 * friends), ported from Tadami.
 *
 * The critical detail: the WebView is created with the **same User-Agent the original request
 * used** and loads the URL with the request's (sanitized) headers — so the resulting
 * `cf_clearance` cookie is bound to a UA the app's OkHttp requests actually send, which is why
 * the naive "open a browser tab and verify" approach keeps failing with 403.
 */
abstract class WebViewInterceptor(
    private val context: Context,
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {

    /** Touch the WebView init lazily off the request path where possible. */
    private val initWebView by lazy {
        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Exception) {
            // Avoid crashes while Chrome/WebView is being updated.
        }
    }

    abstract fun shouldIntercept(response: Response): Boolean

    abstract fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!shouldIntercept(response)) {
            return response
        }

        if (!supportsWebView()) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "WebView is required to bypass Cloudflare protection",
                    Toast.LENGTH_LONG,
                ).show()
            }
            return response
        }
        initWebView

        return intercept(chain, request, response)
    }

    private fun supportsWebView(): Boolean {
        try {
            CookieManager.getInstance() // throws if WebView is missing
        } catch (e: Throwable) {
            return false
        }
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
    }

    fun parseHeaders(headers: Headers): Map<String, String> {
        val safeHeaders = headers
            // Keeping unsafe headers makes the WebView throw net::ERR_INVALID_ARGUMENT.
            .filter { (name, value) ->
                isRequestHeaderSafe(name, value)
            }
            .groupBy(keySelector = { (name, _) -> name }) { (_, value) -> value }
            .mapValues { it.value.getOrNull(0).orEmpty() }
        // Strip headers that fingerprint Android WebView for anti-bot services like
        // Cloudflare (sec-ch-ua client hints + the X-Requested-With package name).
        return sanitizeCloudflareRequestHeaders(safeHeaders)
    }

    fun CountDownLatch.awaitFor30Seconds() {
        await(30, TimeUnit.SECONDS)
    }

    fun createWebView(request: Request): WebView {
        return WebView(context).apply {
            setDefaultSettings()
            // Avoid sending an empty User-Agent, Chromium resets to the default if empty.
            settings.userAgentString = request.header("User-Agent") ?: defaultUserAgentProvider()
        }
    }

    private fun sanitizeCloudflareRequestHeaders(requestHeaders: Map<String, String>): Map<String, String> {
        val packageName = context.packageName
        val spoofedPackageName = runCatching { context.packageManager.getPackageInfo("com.android.chrome", 0) }
            .recoverCatching { context.packageManager.getPackageInfo("com.android.settings", 0) }
            .getOrNull()?.packageName ?: "com.android.settings"

        return requestHeaders.filterNot { (name, value) ->
            when (name.lowercase(Locale.ROOT)) {
                "x-requested-with" -> value == packageName || value == spoofedPackageName
                in cloudflareBlockedHeaders -> true
                else -> false
            }
        }
    }
}

private fun WebView.setDefaultSettings() {
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT

        // Handle popups properly (needed for Cloudflare Turnstile).
        setSupportMultipleWindows(true)

        // Allow zooming.
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }

    // Don't send X-Requested-With: <package> — Cloudflare treats it as a WebView fingerprint
    // and keeps looping the Turnstile challenge for WebViews that leak it.
    if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
        WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
    }

    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@setDefaultSettings, true)
    }
}

// Based on IsRequestHeaderSafe in https://source.chromium.org/chromium/chromium/src/+/main:services/network/public/cpp/header_util.cc
private fun isRequestHeaderSafe(_name: String, _value: String): Boolean {
    val name = _name.lowercase(Locale.ENGLISH)
    val value = _value.lowercase(Locale.ENGLISH)
    if (name in unsafeHeaderNames || name.startsWith("proxy-")) return false
    if (name == "connection" && value == "upgrade") return false
    return true
}

private val unsafeHeaderNames =
    listOf(
        "content-length",
        "host",
        "trailer",
        "te",
        "upgrade",
        "cookie2",
        "keep-alive",
        "transfer-encoding",
        "set-cookie",
    )

private val cloudflareBlockedHeaders = setOf(
    "sec-ch-ua",
    "sec-ch-ua-full-version-list",
)
