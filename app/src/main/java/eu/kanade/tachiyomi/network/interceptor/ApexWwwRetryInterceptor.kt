package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Belt-and-suspenders for sites whose `www.` and apex hosts route differently per network/ISP.
 * Observed on keiyoushi 4KHD: its baseUrl is "https://www.4khd.com", and from some networks that
 * host answers HTTP 400 for the WordPress REST API while the apex "https://4khd.com" serves the
 * exact same URL fine (the reverse can also happen). When a request fails with HTTP 400 we retry
 * once on the sibling host (strip or add "www."). Only affects 400s on single-subdomain hosts, so
 * legitimate 4xx/5xx errors and multi-label hosts (api.example.com) are never touched.
 */
class ApexWwwRetryInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != 400) return response

        val host = request.url.host
        if (host.isBlank() || host.startsWith("127.") || ":" in host) return response
        val labels = host.split('.')
        if (labels.size < 2) return response

        val sibling = if (host.startsWith("www.")) {
            if (labels.size == 2) null else host.removePrefix("www.")
        } else {
            if (labels.size == 2) "www.$host" else null
        } ?: return response

        val retried = try {
            chain.proceed(
                request.newBuilder()
                    .url(request.url.newBuilder().host(sibling).build())
                    .build(),
            )
        } catch (e: Exception) {
            // Sibling host doesn't exist / isn't reachable — keep the original 400 response.
            return response
        }
        response.close()
        return retried
    }
}
