package eu.kanade.tachiyomi.network

import android.content.Context
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Minimal JS evaluation for extensions that need it (WebView-backed).
 */
class JavaScriptEngine(context: Context) {

    private var webView: WebView? = null
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> evaluate(script: String): T = withContext(Dispatchers.Main) {
        val wv = webView ?: WebView(appContext).apply {
            settings.javaScriptEnabled = true
        }.also { webView = it }
        suspendCancellableCoroutine { continuation ->
            wv.evaluateJavascript("(function() { return $script })()") { value ->
                continuation.resume(value as T)
            }
        }
    }
}
