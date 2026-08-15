package eu.kanade.tachiyomi.util.system

import java.util.Locale

private val cloudflareBlockedHeaders = setOf(
    "sec-ch-ua",
    "sec-ch-ua-full-version-list",
)

/**
 * Strips headers that fingerprint Android WebView for anti-bot services (Cloudflare).
 * Ported verbatim from Tadami: drops the `X-Requested-With` package name and the
 * `sec-ch-ua` client hints from the request headers before replaying them in a WebView.
 */
fun sanitizeCloudflareRequestHeaders(
    requestHeaders: Map<String, String>,
    contextPackageName: String,
    spoofedPackageName: String,
): Map<String, String> {
    return requestHeaders.filterNot { (name, value) ->
        when (name.lowercase(Locale.ROOT)) {
            "x-requested-with" -> value == contextPackageName || value == spoofedPackageName
            in cloudflareBlockedHeaders -> true
            else -> false
        }
    }
}
