package com.example.diagnostics

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Printer
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderDiagnostics
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.ArrayDeque
import java.util.concurrent.ArrayBlockingQueue
import kotlin.math.abs

/**
 * Whole-app scroll-jank diagnostics (the app-level counterpart to the reader's
 * [ReaderDiagnostics]). The reader lag is fixed, but the same class of stutter can hide anywhere
 * else — the chapter list on a series page, the extension catalog grid while browsing, the
 * library, history — so this tool watches the WHOLE app at once and reports, per scroll gesture,
 * how long it lasted and how many frames it dropped, plus the phase breakdown of every long frame
 * and the exact main-thread callback that blocked it.
 *
 * It is deliberately global instead of per-list: a `Window` FrameMetrics listener catches every
 * frame any screen draws (Compose lists, native RecyclerViews, WebViews alike), a `Looper` message
 * logger names the offending callback, and a touch/scroll-gesture detector groups those frames
 * into named scroll sessions. Every line is tagged with the current nav route (`[browse]`,
 * `[manga_detail/…]`, …), so one log run says which screen jittered. The reader keeps its own,
 * more specialised diagnostics; this one skips the reader route so the two never double-log, and
 * routes the shared main-thread message lines into whichever buffer is on screen.
 *
 * Like the reader tracker, [log] only touches an in-memory ring buffer and hands the line to a
 * single background writer thread, and overlay updates are coalesced, so the diagnostics can never
 * be the thing causing the jank they are measuring.
 *
 * This is a TEST-BUILD tool: it is intentionally verbose, and it stays in the shipped build only
 * until the app is confirmed smooth everywhere. Set [ENABLED] to false to compile it out.
 */
object AppDiagnostics {

    /** Set to false to compile the whole app-level tracker out of a normal build. */
    const val ENABLED = true

    private const val MAX_LINES = 400
    private const val FILE_NAME = "nekoread-app-diagnostic.log"

    /** A frame at/over this is always reported (outside the reader). */
    private const val FRAME_JANK_MS = 100L

    /** While a scroll gesture is in progress, frames at/over this are reported too. */
    private const val SCROLL_FRAME_MS = 50L

    /** A main-thread message at/over this is reported (names the blocking callback). */
    private const val MSG_JANK_MS = 60L

    /** A scroll session ends once this long passes with no new frame (covers a fling). */
    private const val SCROLL_END_QUIET_MS = 700L

    private const val NOTIFY_MS = 400L

    private const val TOUCH_LABEL = "(scroll)"

    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private var logFile: File? = null
    private val startMs = SystemClock.elapsedRealtime()

    /** Callback for the on-screen overlay (single subscriber: the app overlay). */
    @Volatile
    var onUpdate: (() -> Unit)? = null

    /** Current nav route — prefixes every line so a log says which screen jittered. */
    @Volatile
    var screen: String = "-"

    /**
     * Whether the app overlay should be drawn. Observed as Compose state so toggling it (the nav
     * host hides it inside the reader, which has its own overlay) recomposes just the overlay.
     */
    var overlayVisible by mutableStateOf(false)

    // ---- scroll session bookkeeping (main thread only) ----
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var scrollActive = false
    private var scrollLabel = TOUCH_LABEL
    private var scrollT0 = 0L
    private var scrollFrames = 0
    private var scrollJanky = 0
    private var scrollWorstMs = 0L
    private val endScrollRunnable = Runnable { endScroll(TOUCH_LABEL) }

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDragging = false
    private var touchSlop = 24

    // ---- file writer ----
    private val pending = ArrayBlockingQueue<String>(8192)

    @Volatile
    private var writer: Thread? = null

    @Volatile
    private var notifyScheduled = false

    @Volatile
    private var installed = false

    @Volatile
    private var frameListenerInstalled = false

    @Volatile
    private var messageProbeInstalled = false

    /** Idempotent init from the Activity (creates the log file + background writer). */
    fun init(context: Context) {
        if (logFile != null) return
        logFile = File(context.applicationContext.filesDir, FILE_NAME)
        runCatching { logFile?.writeText("") }
        startWriter()
    }

    /** Installs every app-wide probe. Call once from the Activity. */
    fun install(activity: Activity) {
        if (!ENABLED || installed) return
        installed = true
        init(activity)
        touchSlop = ViewConfiguration.get(activity).scaledTouchSlop.coerceAtLeast(16)
        installMessageProbe()
        attachFrameListener(activity)
        log("app diagnostics attached (touchSlop=${touchSlop}px, log=${path() ?: "?"})")
    }

    private fun startWriter() {
        if (writer != null) return
        synchronized(lock) {
            if (writer != null) return
            val t = Thread({
                val file = logFile
                if (file != null) {
                    var out: BufferedWriter? = null
                    try {
                        val w = BufferedWriter(FileWriter(file, true), 64 * 1024)
                        out = w
                        while (true) {
                            val first = pending.take()
                            w.write(first)
                            w.write("\n")
                            // Drain the burst before flushing: a busy moment costs one write().
                            var drained = 0
                            while (drained < 512) {
                                val more = pending.poll() ?: break
                                w.write(more)
                                w.write("\n")
                                drained++
                            }
                            w.flush()
                        }
                    } catch (e: Throwable) {
                        // Interrupted / file gone — the in-memory buffer still works.
                    } finally {
                        runCatching { out?.close() }
                    }
                }
            }, "nekoread-appdiag-writer")
            t.isDaemon = true
            t.start()
            writer = t
        }
    }

    fun clear() {
        synchronized(lock) { lines.clear() }
        pending.clear()
        logFile?.let { f -> runCatching { f.writeText("") } }
        notifyNow()
    }

    fun log(msg: String) {
        if (!ENABLED) return
        val since = SystemClock.elapsedRealtime() - startMs
        val line = "+${since}ms [${screen}] $msg"
        synchronized(lock) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
        if (writer != null && !pending.offer(line)) {
            pending.poll()
            pending.offer(line)
        }
        scheduleNotify()
    }

    private fun scheduleNotify() {
        if (onUpdate == null || notifyScheduled) return
        notifyScheduled = true
        mainHandler.postDelayed({
            notifyScheduled = false
            runCatching { onUpdate?.invoke() }
        }, NOTIFY_MS)
    }

    private fun notifyNow() {
        notifyScheduled = false
        runCatching { onUpdate?.invoke() }
    }

    /** Full diagnostic text for the copy button / log file. */
    fun fullText(): String {
        val header = "Nekoread APP diagnostics\n" +
            "log: ${logFile?.absolutePath ?: "not-yet-initialized"}\n" +
            "copy captures the whole app's scroll/frame history\n"
        val body = synchronized(lock) { lines.joinToString("\n") }
        return header + body
    }

    fun path(): String? = logFile?.absolutePath

    /** Latest lines (for the overlay), most recent last. */
    fun text(): String = synchronized(lock) { lines.joinToString("\n") }

    fun copy(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("nekoread-app-diagnostic", fullText()))
    }

    // ---------------------------------------------------------------------------------------------
    // Probes
    // ---------------------------------------------------------------------------------------------

    /**
     * Single global main-thread message logger. It is installed ONCE and routes each slow message
     * to whichever diagnostics is on screen (the reader's while a reader route is active, this one
     * otherwise), so the reader's own specialised probe and this one can never fight over the
     * process-wide `Looper.setMessageLogging` slot.
     */
    private fun installMessageProbe() {
        if (messageProbeInstalled) return
        messageProbeInstalled = true
        val mainLooper = Looper.getMainLooper()
        var msgStart = 0L
        var msgDesc = ""
        val printer = Printer { s ->
            if (s.startsWith(">>>>>")) {
                msgStart = SystemClock.elapsedRealtime()
                msgDesc = s.substringAfter("Dispatching to ", s).trim().take(150)
            } else if (s.startsWith("<<<<<")) {
                val dt = SystemClock.elapsedRealtime() - msgStart
                if (dt >= MSG_JANK_MS) {
                    val line = "msg ${dt}ms $msgDesc"
                    if (screen.startsWith("reader/")) ReaderDiagnostics.log(line) else log(line)
                }
            }
        }
        mainLooper.setMessageLogging(printer)
    }

    private fun attachFrameListener(activity: Activity) {
        if (frameListenerInstalled) return
        val window = activity.window
        frameListenerInstalled = true
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ -> onFrame(metrics) }
        window.addOnFrameMetricsAvailableListener(listener, mainHandler)
    }

    private fun onFrame(metrics: FrameMetrics) {
        // The reader has its own (more detailed) frame probe — don't double-report its frames.
        if (screen.startsWith("reader/")) return
        val totalMs = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000
        if (scrollActive) {
            scrollFrames++
            if (totalMs >= SCROLL_FRAME_MS) {
                scrollJanky++
                if (totalMs > scrollWorstMs) scrollWorstMs = totalMs
            }
            // Every frame during a session keeps it alive; a fling outlives the touch-up.
            mainHandler.removeCallbacks(endScrollRunnable)
            mainHandler.postDelayed(endScrollRunnable, SCROLL_END_QUIET_MS)
        }
        val report = totalMs >= FRAME_JANK_MS || (scrollActive && totalMs >= SCROLL_FRAME_MS)
        if (!report) return
        fun ms(metric: Int) = metrics.getMetric(metric) / 1_000_000
        val tag = if (scrollActive) " [scroll $scrollLabel]" else ""
        log(
            "frame$tag total=${totalMs}ms " +
                "delay=${ms(FrameMetrics.UNKNOWN_DELAY_DURATION)} " +
                "input=${ms(FrameMetrics.INPUT_HANDLING_DURATION)} " +
                "anim=${ms(FrameMetrics.ANIMATION_DURATION)} " +
                "layout=${ms(FrameMetrics.LAYOUT_MEASURE_DURATION)} " +
                "draw=${ms(FrameMetrics.DRAW_DURATION)} " +
                "sync=${ms(FrameMetrics.SYNC_DURATION)} " +
                "cmd=${ms(FrameMetrics.COMMAND_ISSUE_DURATION)} " +
                "swap=${ms(FrameMetrics.SWAP_BUFFERS_DURATION)}",
        )
    }

    /**
     * Start a named scroll session. The first caller wins, except that a real list label
     * ([AppScrollProbe]) upgrades the generic touch label mid-gesture.
     */
    fun beginScroll(label: String) {
        if (!ENABLED) return
        if (scrollActive) {
            if (label != TOUCH_LABEL) scrollLabel = label
            return
        }
        scrollActive = true
        scrollLabel = label
        scrollT0 = SystemClock.elapsedRealtime()
        scrollFrames = 0
        scrollJanky = 0
        scrollWorstMs = 0L
        log("scroll $label start")
        mainHandler.removeCallbacks(endScrollRunnable)
        mainHandler.postDelayed(endScrollRunnable, SCROLL_END_QUIET_MS)
    }

    /** End a scroll session. A named session can only be ended by its own label/probe. */
    fun endScroll(label: String) {
        if (!ENABLED || !scrollActive) return
        if (label == TOUCH_LABEL && scrollLabel != TOUCH_LABEL) return
        mainHandler.removeCallbacks(endScrollRunnable)
        val dur = SystemClock.elapsedRealtime() - scrollT0
        log(
            "scroll $scrollLabel end ${dur}ms frames=$scrollFrames " +
                "janky=$scrollJanky worst=${scrollWorstMs}ms",
        )
        scrollActive = false
    }

    // ---------------------------------------------------------------------------------------------
    // Gesture detection (fed by the Activity's dispatchTouchEvent / dispatchGenericMotionEvent)
    // ---------------------------------------------------------------------------------------------

    /** Feed every touch event here; drags open a scroll session, flings keep it alive via frames. */
    fun onTouchEvent(ev: MotionEvent) {
        if (!ENABLED) return
        if (screen.startsWith("reader/")) return
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = ev.rawX
                touchDownY = ev.rawY
                touchDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchDragging) {
                    val dx = abs(ev.rawX - touchDownX)
                    val dy = abs(ev.rawY - touchDownY)
                    if (dx + dy > touchSlop) {
                        touchDragging = true
                        beginScroll(TOUCH_LABEL)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Don't end here — a fling continues after the finger lifts, and the session's
                // watchdog only ends once the frames stop arriving.
                touchDragging = false
            }
        }
    }

    /** Mouse-wheel / trackpad / dpad scrolling (no touch stream) also opens a session. */
    fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ENABLED && !screen.startsWith("reader/") && ev.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
            if (ev.actionMasked == MotionEvent.ACTION_SCROLL) {
                beginScroll(TOUCH_LABEL)
                mainHandler.removeCallbacks(endScrollRunnable)
                mainHandler.postDelayed(endScrollRunnable, SCROLL_END_QUIET_MS)
                return true
            }
        }
        return false
    }
}
