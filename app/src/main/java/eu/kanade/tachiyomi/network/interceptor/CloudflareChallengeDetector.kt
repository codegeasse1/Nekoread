package eu.kanade.tachiyomi.network.interceptor

import java.util.Locale

/**
 * Pure (no Android/WebView dependencies) classifier of Cloudflare responses, ported from Tadami.
 *
 * Used both by [CloudflareInterceptor] and the challenge resolver so the same marker set is used
 * everywhere. Distinguishes interactive challenges (Turnstile/managed widget — needs a human)
 * from automatic interstitial challenges ("Just a moment…" — solved silently in a WebView).
 */
object CloudflareChallengeDetector {

    private val cloudflareServers = setOf("cloudflare", "cloudflare-nginx")

    /** Interactive challenge (Turnstile / managed challenge with a widget): needs human action. */
    val interactiveMarkers = listOf(
        "cf-turnstile",
        "challenges.cloudflare.com",
        "data-sitekey",
    )

    /** Non-interactive interstitial ("Just a moment…", JS challenge): passes automatically. */
    val interstitialMarkers = listOf(
        "_cf_chl_opt",
        "cf_chl_opt",
        "/cdn-cgi/challenge-platform/",
        "challenge-platform",
        "cf-browser-verification",
        "cf-challenge-running",
        "just a moment",
    )

    /** Challenge error page (challenge failed/broken). */
    val errorMarkers = listOf(
        "challenge-error-title",
        "challenge-error-text",
    )

    private val allMarkers = interactiveMarkers + interstitialMarkers + errorMarkers

    fun isCloudflareServer(server: String?): Boolean =
        server != null && server.lowercase(Locale.ROOT) in cloudflareServers

    /** `cf-mitigated: challenge` — authoritative marker of a managed challenge. */
    fun isManagedChallenge(cfMitigated: String?): Boolean =
        cfMitigated?.trim()?.equals("challenge", ignoreCase = true) == true

    /** Any challenge marker in the body (interactive/interstitial/error). */
    fun hasChallengeMarkers(body: String): Boolean {
        val lower = body.lowercase(Locale.ROOT)
        return allMarkers.any { it in lower }
    }

    /** Is there a marker of an interactive ("human") challenge in the body. */
    fun hasInteractiveMarkers(body: String): Boolean {
        val lower = body.lowercase(Locale.ROOT)
        return interactiveMarkers.any { it in lower }
    }

    /**
     * Full classification of a response. `bodyPeek` is the first kilobyte(s) of the body
     * (challenge markers always appear near the top of the document).
     */
    fun classify(
        code: Int,
        server: String?,
        cfMitigated: String?,
        bodyPeek: String,
    ): CloudflareChallengeType {
        if (code !in ERROR_CODES || !isCloudflareServer(server)) {
            return CloudflareChallengeType.NONE
        }
        val lower = bodyPeek.lowercase(Locale.ROOT)
        if (interactiveMarkers.any { it in lower }) {
            return CloudflareChallengeType.INTERACTIVE
        }
        if (isManagedChallenge(cfMitigated)) {
            return CloudflareChallengeType.MANAGED
        }
        if (interstitialMarkers.any { it in lower }) {
            return CloudflareChallengeType.INTERSTITIAL
        }
        if (errorMarkers.any { it in lower }) {
            return CloudflareChallengeType.ERROR
        }
        return CloudflareChallengeType.NONE
    }
}

enum class CloudflareChallengeType {
    /** Not a challenge (normal response or not a Cloudflare server). */
    NONE,

    /** JS interstitial ("Just a moment…") — passes automatically. */
    INTERSTITIAL,

    /** Managed challenge (`cf-mitigated: challenge`) without an explicit widget. */
    MANAGED,

    /** Turnstile / interactive widget — needs a human. */
    INTERACTIVE,

    /** Challenge error page. */
    ERROR,
}

internal val ERROR_CODES = listOf(403, 503)
