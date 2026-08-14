package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class RateLimitInterceptor(private val period: Long) : Interceptor {

    @Volatile
    private var lastRequestTime = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
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
        return chain.proceed(chain.request())
    }
}
