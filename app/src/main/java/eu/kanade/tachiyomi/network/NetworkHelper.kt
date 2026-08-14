package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.AppInfo
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Provides the network stack that loaded extensions use. Same role as Tadami/Mihon's
 * NetworkHelper (minus DoH/Cloudflare helpers, which extensions don't require to compile).
 */
class NetworkHelper(context: Context) {

    val client: OkHttpClient = defaultClient()
    val cloudflareClient: OkHttpClient = defaultClient()

    private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Nekoread/" + AppInfo.getVersionName())
                .build()
            chain.proceed(request)
        }
        .build()

    companion object {
        @Volatile
        private var instance: NetworkHelper? = null

        fun init(context: Context) {
            AppInfo.init(context)
            if (instance == null) {
                instance = NetworkHelper(context.applicationContext)
            }
        }

        fun getInstance(): NetworkHelper =
            instance ?: throw IllegalStateException("NetworkHelper not initialized")
    }
}
