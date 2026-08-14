package eu.kanade.tachiyomi.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Bridges the Android WebView cookie store with OkHttp, exactly like Tachiyomi/Tadami's
 * cloudflare-bypass flow: when the user completes a Cloudflare / DDoS-Guard challenge inside the
 * in-app WebView, the resulting cookies land in the shared [CookieManager] and are then
 * automatically sent by the extension clients' OkHttp requests, so sources start loading in-app.
 *
 * CookieManager persists to disk, so a completed verification survives app restarts.
 */
class WebViewCookieJar : CookieJar {

    private val cookieManager: CookieManager
        get() = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            // CookieManager ignores cookies for hosts it hasn't seen set up properly; scoping
            // to the request URL's host (as WebView does) keeps the store consistent.
            cookieManager.setCookie(url.toString(), cookie.toString())
        }
        cookieManager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieHeader = cookieManager.getCookie(url.toString()) ?: return emptyList()
        return cookieHeader.split(";").mapNotNull { entry ->
            val eq = entry.indexOf('=')
            if (eq < 0) return@mapNotNull null
            val name = entry.substring(0, eq).trim()
            val value = entry.substring(eq + 1).trim()
            try {
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(url.host)
                    .path("/")
                    .build()
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
