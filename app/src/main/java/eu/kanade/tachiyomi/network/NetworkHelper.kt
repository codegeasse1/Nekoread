package eu.kanade.tachiyomi.network

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.interceptor.ApexWwwRetryInterceptor
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import eu.kanade.tachiyomi.util.defaultJson
import okhttp3.Cache
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Provides the network stack that loaded extensions use — ported to match Tadami's
 * NetworkHelper exactly, so the client looks like the one Mihon/Tadami hand to extensions:
 *
 *  - [UncaughtExceptionInterceptor] first in the chain (and required *by name* by extension-lib
 *    1.6 sources — missing it is what made some extensions fail with
 *    "UncaughtExceptionInterceptor must be present in default client");
 *  - [UserAgentInterceptor] to set the default UA (also required *by name*);
 *  - [CloudflareInterceptor] as the innermost application interceptor (also required *by name*).
 *
 * Registered into the global injekt scope so extension code can `by injectLazy()` it, exactly like
 * Tadami.
 */
class NetworkHelper(context: Context) {

    /** Shared WebView cookie store; extensions' OkHttp clients and the Cloudflare WebView both use it. */
    val cookieJar = AndroidCookieJar()

    private val clientBuilder: OkHttpClient.Builder = run {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .dispatcher(
                okhttp3.Dispatcher().apply {
                    maxRequests = 64
                    maxRequestsPerHost = 8
                },
            )
            .connectionPool(
                okhttp3.ConnectionPool(
                    maxIdleConnections = 15,
                    keepAliveDuration = 5,
                    timeUnit = TimeUnit.MINUTES,
                ),
            )
            .cache(
                Cache(
                    directory = File(context.cacheDir, "network_cache"),
                    maxSize = 64L * 1024 * 1024,
                ),
            )
            .addInterceptor(UncaughtExceptionInterceptor())
            .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
            .addInterceptor(ApexWwwRetryInterceptor())
    }

    /** The one client extensions use. Automatically solves Cloudflare challenges via a hidden WebView. */
    val client: OkHttpClient = clientBuilder
        .addInterceptor(
            CloudflareInterceptor(
                context = context,
                cookieManager = cookieJar,
                defaultUserAgentProvider = ::defaultUserAgentProvider,
            ),
        )
        .build()

    /** @deprecated — the regular client handles Cloudflare by default. */
    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient = client

    /**
     * The default User-Agent for extension requests and the Cloudflare WebView. Same as Tadami's:
     * a real Chrome-on-Android UA. Cloudflare's Turnstile treats non-browser UAs as bots and
     * loops the challenge forever — the old "Nekoread/x" default is why verification never
     * completed.
     */
    fun defaultUserAgentProvider(): String =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"

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

                // Old-style extensions (extension-lib ≤1.3, e.g. keiyoushi ComicLand v1.4) pull app
                // singletons out of the GLOBAL injekt scope through their generated InjektFactory —
                // `Injekt.get<Json>()` via a FullTypeReference. Without a Json (and OkHttpClient)
                // registered here, loading their catalog crashed with
                // "InjektionException: No registered instance or factory for type Json".
                Injekt.addSingleton(defaultJson)
                Injekt.addSingletonFactory<OkHttpClient> { nh.client }
            }
        }

        fun getInstance(): NetworkHelper =
            instance ?: throw IllegalStateException("NetworkHelper not initialized")
    }
}
