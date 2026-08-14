package eu.kanade.tachiyomi.network

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.util.concurrent.TimeUnit

/**
 * Provides the network stack that loaded extensions use — same role as Tadami's NetworkHelper.
 * Registered into the global injekt scope so extension code can `by injectLazy()` it, exactly like
 * Tadami.
 */
class NetworkHelper(context: Context) {

    /** Shared WebView cookie store; extensions' OkHttp clients and the Cloudflare WebView both use it. */
    val cookieJar = AndroidCookieJar()

    private val clientBuilder: OkHttpClient.Builder = run {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                // Like Tadami: only set the app UA when the extension didn't supply its own, so the
                // WebView (which mirrors the request's UA) binds cf_clearance to the real UA.
                val request = chain.request()
                val ua = request.header("User-Agent") ?: defaultUserAgentProvider()
                chain.proceed(request.newBuilder().header("User-Agent", ua).build())
            }
    }

    /** The one client extensions use. Automatically solves Cloudflare challenges via a hidden WebView. */
    val client: OkHttpClient = clientBuilder
        .addInterceptor(
            CloudflareInterceptor(
                context = context,
                cookieManager = cookieJar,
                defaultUserAgentProvider = { defaultUserAgentProvider() },
            ),
        )
        .build()

    /** @deprecated — the regular client handles Cloudflare by default. */
    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient = client

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
