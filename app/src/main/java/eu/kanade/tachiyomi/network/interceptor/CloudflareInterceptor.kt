package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.isOutdated
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Application interceptor on the shared network client that automatically bypasses Cloudflare /
 * DDoS-Guard challenges, ported from Tadami:
 *
 *  1. a response with 403/503 + `Server: cloudflare` (or `cf-mitigated`/challenge markers in the
 *     body) is treated as a challenge;
 *  2. per-host lock so concurrent requests share one WebView solve;
 *  3. if a `cf_clearance` already exists, an immediate retry is tried first;
 *  4. otherwise a hidden WebView loads the original URL with the original request's User-Agent and
 *     headers, waits for Cloudflare to issue a fresh `cf_clearance`, then retries.
 *
 * Because the WebView solves with the same UA the OkHttp requests send, the cookie is valid for
 * them — this is what makes in-app loading work after verification.
 */
class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
    private val challengeResolver: CloudflareChallengeResolver? = null,
) : WebViewInterceptor(context, defaultUserAgentProvider) {

    private val challengeLockByHost = ConcurrentHashMap<String, Any>()
    private val webViewChallengeResolver = challengeResolver ?: WebViewCloudflareChallengeResolver(
        context = context,
        cookieManager = cookieManager,
        mainExecutor = ContextCompat.getMainExecutor(context),
        createWebView = this::createWebView,
        parseHeaders = this::parseHeaders,
        isWebViewOutdated = { it.isOutdated() },
    )

    override fun shouldIntercept(response: Response): Boolean {
        if (response.code !in ERROR_CODES ||
            !CloudflareChallengeDetector.isCloudflareServer(response.header("Server"))
        ) {
            return false
        }
        // The cf-mitigated header is authoritative for managed/interactive challenges.
        if (CloudflareChallengeDetector.isManagedChallenge(response.header("cf-mitigated"))) {
            return true
        }
        // Limit body inspection to a small prefix; challenge markup is at the top of the
        // document and full body parsing wastes memory on large pages.
        val bodyPeek = response.peekBody(CHALLENGE_PEEK_BYTES).string()
        return CloudflareChallengeDetector.hasChallengeMarkers(bodyPeek)
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        val host = request.url.host
        try {
            response.close()
            // One lock object per host is kept for the process lifetime so concurrent requests to
            // the same host coalesce onto a single WebView solve and then reuse the resulting
            // cf_clearance.
            val hostLock = challengeLockByHost.getOrPut(host) { Any() }
            synchronized(hostLock) {
                val oldCookie = cookieManager.get(request.url)
                    .firstOrNull { it.name == "cf_clearance" }

                // Only pay for an immediate network retry when there is a clearance to try.
                if (oldCookie != null) {
                    val immediateRetry = chain.proceed(request)
                    if (!shouldIntercept(immediateRetry)) {
                        return immediateRetry
                    }
                    immediateRetry.close()
                    cookieManager.remove(request.url, COOKIE_NAMES, 0)
                }

                webViewChallengeResolver.resolve(request, oldCookie)

                val firstAttempt = chain.proceed(request)
                if (!shouldIntercept(firstAttempt)) {
                    return firstAttempt
                }
                // The cookie set on CookieManager may not have propagated to OkHttp's CookieJar
                // yet for the in-flight connection; close and retry once.
                firstAttempt.close()
                return chain.proceed(request)
            }
        } catch (e: CloudflareInteractiveChallengeException) {
            throw IOException(
                "Cloudflare interactive challenge — open the source in WebView to verify manually",
                e,
            )
        } catch (e: CloudflareBypassException) {
            throw IOException("Couldn't bypass Cloudflare protection", e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }
}

private val COOKIE_NAMES = listOf("cf_clearance")

// Just enough to capture the challenge headers/markers; the page body is larger but the
// challenge identifiers always appear near the top.
private const val CHALLENGE_PEEK_BYTES = 8L * 1024L
