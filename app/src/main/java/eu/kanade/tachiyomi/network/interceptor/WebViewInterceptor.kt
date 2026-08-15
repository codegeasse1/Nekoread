package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.sanitizeCloudflareRequestHeaders
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Base interceptor that spins up a hidden WebView to solve anti-bot challenges (Cloudflare and
 * friends). Ported verbatim from Tadami.
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

    /**
     * When this is called, it initializes the WebView if it wasn't already. We use this to avoid
     * blocking the main thread too much.
     */
    private val initWebView by lazy {
        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Exception) {
            // Avoid some crashes like when Chrome/WebView is being updated.
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

        if (!WebViewUtil.supportsWebView(context)) {
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

    fun parseHeaders(headers: Headers): Map<String, String> {
        val safeHeaders = headers
            // Keeping unsafe headers makes the webview throw [net::ERR_INVALID_ARGUMENT]
            .filter { (name, value) ->
                isRequestHeaderSafe(name, value)
            }
            .groupBy(keySelector = { (name, _) -> name }) { (_, value) -> value }
            .mapValues { it.value.getOrNull(0).orEmpty() }
        // Strip headers that fingerprint Android WebView for anti-bot services like
        // Cloudflare (sec-ch-ua client hints + the X-Requested-With package name).
        return sanitizeCloudflareRequestHeaders(
            requestHeaders = safeHeaders,
            contextPackageName = context.packageName,
            spoofedPackageName = WebViewUtil.spoofedPackageName(context),
        )
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
