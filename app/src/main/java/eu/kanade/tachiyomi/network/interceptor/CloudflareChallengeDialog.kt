package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import eu.kanade.tachiyomi.util.system.setDefaultSettings

/**
 * Fullscreen dialog that hosts a Cloudflare challenge WebView so the user can complete an
 * interactive (Turnstile / "I am not a robot") challenge by hand. Ported behaviour from Tadami's
 * interactive WebView flow.
 *
 *  - polls the shared CookieManager for a fresh `cf_clearance` every 500ms and auto-dismisses the
 *    moment the challenge is solved — the requesting OkHttp call then retries and succeeds;
 *  - popups (some managed challenges use them) are rendered on top of the same dialog.
 */
@SuppressLint("SetJavaScriptEnabled")
class CloudflareChallengeDialog(
    context: Context,
    private val webView: WebView,
    private val isSolved: () -> Boolean,
    private val onSolved: () -> Unit,
    private val onDismissed: () -> Unit,
) : Dialog(context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)

    private var finished = false

    private val pollClearance = object : Runnable {
        override fun run() {
            if (finished) return
            if (isSolved()) {
                finished = true
                CookieManager.getInstance().flush()
                onSolved()
                dismissInternal()
                return
            }
            mainHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    init {
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setCancelable(false)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 20, 31))
        }

        // Header: title + close button.
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(8), dp(12))
            setBackgroundColor(Color.rgb(27, 30, 44))
        }
        val title = TextView(context).apply {
            text = "Cloudflare / Site Verification"
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = TextView(context).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { dismiss() }
        }
        header.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?,
            ): Boolean {
                val popup = WebView(context).apply {
                    setDefaultSettings()
                    settings.userAgentString = webView.settings.userAgentString
                }
                root.addView(
                    popup,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                popup.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        CookieManager.getInstance().flush()
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                transport.webView = popup
                resultMsg?.sendToTarget()
                return true
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
                result.confirm()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
                result.confirm()
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult,
            ): Boolean {
                result.cancel()
                return true
            }
        }

        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun show() {
        super.show()
        mainHandler.removeCallbacks(pollClearance)
        mainHandler.post(pollClearance)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        dismiss()
    }

    override fun dismiss() {
        if (finished) {
            dismissInternal()
            return
        }
        finished = true
        mainHandler.removeCallbacks(pollClearance)
        dismissInternal()
        onDismissed()
    }

    private fun dismissInternal() {
        if (isShowing) {
            super.dismiss()
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val POLL_INTERVAL_MS = 500L
    }
}
