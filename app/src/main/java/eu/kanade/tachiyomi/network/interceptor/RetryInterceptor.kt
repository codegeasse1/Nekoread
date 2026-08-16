package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Transparent retry for transient server-side failures on idempotent requests. Sources like
 * keiyoushi 4KHD sit behind flaky WordPress/CDN stacks that sporadically answer 5xx (HTTP 502 was
 * observed) for a few seconds and then recover — without a retry the catalog/chapter request just
 * surfaces "Couldn't reach 4KHD" even though the site is fine a moment later.
 *
 * Only 429/500/502/503/504 are retried, at most twice with a short backoff, and only for GET/HEAD
 * requests (extension traffic is all GETs). The retry count is carried on the request itself so a
 * re-entrant chain (e.g. the Cloudflare interceptor re-proceeding) can never loop forever.
 */
class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val initialBackoffMs: Long = 1000L,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET" && request.method != "HEAD") {
            return chain.proceed(request)
        }

        val attempt = request.header(RETRY_COUNT_HEADER)?.toIntOrNull() ?: 0

        var response = chain.proceed(request)
        var retryCount = attempt
        while (response.code in RETRYABLE_CODES && retryCount < maxRetries) {
            retryCount++
            val retryRequest = request.newBuilder()
                .header(RETRY_COUNT_HEADER, retryCount.toString())
                .build()
            response.close()
            Thread.sleep(initialBackoffMs * (1L shl (retryCount - 1)))
            response = chain.proceed(retryRequest)
        }
        return response
    }

    companion object {
        private const val RETRY_COUNT_HEADER = "X-Nekoread-Retry"
        private val RETRYABLE_CODES = setOf(429, 500, 502, 503, 504)
    }
}
