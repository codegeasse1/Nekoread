package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Transparent retry for transient server-side failures on idempotent requests. Sources like
 * keiyoushi 4KHD sit behind flaky WordPress/CDN stacks that sporadically answer 5xx (HTTP 502 was
 * observed) for a few seconds and then recover — without a retry the catalog/chapter request just
 * surfaces "Couldn't reach 4KHD" even though the site is fine a moment later.
 *
 * Only 429/500/502/503/504 are retried, plus dropped connections / read timeouts (IOException),
 * at most three times with a doubling backoff, and only for GET/HEAD requests (extension traffic
 * is all GETs). The retry count is carried on the request itself so a re-entrant chain (e.g. the
 * Cloudflare interceptor re-proceeding) can never loop forever.
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val initialBackoffMs: Long = 1200L,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.method != "GET" && original.method != "HEAD") {
            return chain.proceed(original)
        }

        var attempt = original.header(RETRY_COUNT_HEADER)?.toIntOrNull() ?: 0
        var request = original
        while (true) {
            try {
                val response = chain.proceed(request)
                if (response.code !in RETRYABLE_CODES || attempt >= maxRetries) {
                    return response
                }
                response.close()
            } catch (e: IOException) {
                // Connection reset/timeout mid-flight — the site may just have been busy. Only
                // surface it after the retries are exhausted.
                if (attempt >= maxRetries) throw e
            }
            attempt++
            request = original.newBuilder()
                .header(RETRY_COUNT_HEADER, attempt.toString())
                .build()
            Thread.sleep(initialBackoffMs * (1L shl (attempt - 1)))
        }
    }

    companion object {
        private const val RETRY_COUNT_HEADER = "X-Nekoread-Retry"
        private val RETRYABLE_CODES = setOf(429, 500, 502, 503, 504)
    }
}
