package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class SpecificHostRateLimitInterceptor(
    private val period: Long,
    private val host: String
) : Interceptor {

    @Volatile
    private var lastRequestTime = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.request().url.host == host) {
            val now = System.currentTimeMillis()
            val wait = period - (now - lastRequestTime)
            if (wait > 0) {
                try {
                    Thread.sleep(wait)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            lastRequestTime = System.currentTimeMillis()
        }
        return chain.proceed(chain.request())
    }
}
