package eu.kanade.tachiyomi.network

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.AppInfo
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.util.concurrent.TimeUnit

/**
 * Provides the network stack that loaded extensions use — same role as Tadami's NetworkHelper
 * (minus DoH/Cloudflare helpers, which extensions don't require to compile). Registered into the
 * global injekt scope so extension code can `by injectLazy()` it, exactly like Tadami.
 */
class NetworkHelper(context: Context) {

    val client: OkHttpClient = defaultClient()
    val cloudflareClient: OkHttpClient = client

    private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        // Shared cookie store with the in-app Cloudflare-verification WebView.
        .cookieJar(WebViewCookieJar())
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", defaultUserAgentProvider())
                .build()
            chain.proceed(request)
        }
        .build()

    fun defaultUserAgentProvider(): String = "Nekoread/" + AppInfo.getVersionName()

    companion object {
        @Volatile
        private var instance: NetworkHelper? = null

        fun init(context: Context) {
            AppInfo.init(context)
            if (instance == null) {
                val app = context.applicationContext
                val nh = NetworkHelper(app)
                instance = nh
                // Extensions resolve NetworkHelper (and the Application for source preferences)
                // through the global injekt registry, exactly like Tadami does.
                Injekt.addSingleton(nh)
                Injekt.addSingleton(app as Application)
            }
        }

        fun getInstance(): NetworkHelper =
            instance ?: throw IllegalStateException("NetworkHelper not initialized")
    }
}
