package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import java.io.File
import java.util.ArrayDeque

/**
 * Test-build diagnostic tracker for the reader. Every page-render decision (path chosen, image
 * dimensions, decode timings, chunk counts) is logged here so a comix lag report can be diagnosed
 * from the phone instead of logcat. Lines accumulate in memory (ring buffer), are mirrored to a log
 * file in the app's files dir (`nekoread-diagnostic.log` — reachable with root), and the latest
 * buffer is pushed to the on-screen overlay in the reader via [onUpdate].
 *
 * This whole file is TEMPORARY: it exists to nail the comix long-strip scroll lag. It is removed
 * once the fix is confirmed.
 */
object ReaderDiagnostics {

    /** Set to false to compile it out of a normal build. */
    const val ENABLED = true

    private const val MAX_LINES = 120

    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private var logFile: File? = null
    private val startMs = SystemClock.elapsedRealtime()

    /** Callback for the on-screen overlay (single subscriber: the reader screen). */
    @Volatile
    var onUpdate: (() -> Unit)? = null

    /** Page label for the next [log] lines (set by the page holder before rendering a page). */
    @Volatile
    var currentLabel: String = "-"

    /** One-time init from any view that has a Context (idempotent). */
    fun init(context: Context) {
        if (logFile != null) return
        logFile = File(context.applicationContext.filesDir, "nekoread-diagnostic.log")
    }

    fun clear() {
        synchronized(lock) { lines.clear() }
        logFile?.let { f -> runCatching { f.writeText("") } }
        onUpdate?.invoke()
    }

    fun log(msg: String) {
        if (!ENABLED) return
        val since = SystemClock.elapsedRealtime() - startMs
        val line = String.format("+%05dms [%s] %s", since, currentLabel, msg)
        synchronized(lock) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
        logFile?.let { f ->
            runCatching { f.appendText(line + "\n") }
        }
        onUpdate?.invoke()
    }

    /** Full diagnostic text for the copy button / log file. */
    fun fullText(): String {
        val header = "Nekoread reader diagnostics\n" +
            "log: ${logFile?.absolutePath ?: "not-yet-initialized"}\n"
        val body = synchronized(lock) { lines.joinToString("\n") }
        return header + body
    }

    /** Absolute path of the log file on disk (for the overlay / root pulls). */
    fun path(): String? = logFile?.absolutePath

    /** Latest lines (for the overlay), most recent last. */
    fun text(): String = synchronized(lock) { lines.joinToString("\n") }

    /** Copies the full diagnostic text to the clipboard. */
    fun copy(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("nekoread-diagnostic", fullText()))
    }
}
