package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderDiagnostics
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Semaphore
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Renders a pathologically tall webtoon strip (one whose whole-strip decode would exceed the
 * reader's single-decode memory budget) as a stack of display-width chunk bitmaps decoded from the
 * page's cache file. Chunks are decoded lazily nearest-to-the-eye first, and — critically for
 * scroll smoothness — ONCE: a decoded chunk stays cached for the page's lifetime (so scrolling and
 * re-scrolling is a set of stable bitmaps the render thread has already texture-cached, with no
 * per-frame decode/upload churn). Memory is bounded by recycling only the chunks farthest from the
 * viewport once the retained total exceeds a byte budget.
 *
 * Render-path rules:
 *  - onDraw ONLY draws the chunk range overlapping the viewport (+1 margin) — never scans,
 *    recycles or launches work.
 *  - Decode/window management runs OFF the draw path, driven by the recycler's scroll listener,
 *    the view's layout pass, and the per-draw "viewport moved?" check — each coalesced into at
 *    most one posted pass.
 *  - Chunks are drawn into EQUAL contiguous display slots whose total is exactly the view height
 *    (slot step = viewHeight / partCount). Every chunk is stretched to fill its slot, so there are
 *    NEVER gaps between chunks — the old fixed-2048px-slot math left a solid black bar between
 *    every chunk whenever the decoded chunk width overshot the view width (which it almost always
 *    did, since sampling is power-of-two). Missing (not-yet-decoded) chunks just skip their slot;
 *    neighbours stay aligned.
 *  - Chunks decode as plain software ARGB_8888/RGB_565. BitmapRegionDecoder CANNOT produce
 *    hardware bitmaps (Android rejects HARDWARE config for region decode — the old code attempted
 *    it and silently fell back to software on every chunk), and that silent fallback plus the
 *    per-scroll recycling was the remaining comix jank. Software chunks are fine here because they
 *    are stable for the page's lifetime.
 *  - A single global semaphore caps how many region decodes run at once across ALL live pages.
 *
 * The view is scroll-aware: each pass reads its position in the recycler (via the holder's `top` —
 * the view itself fills the holder, so its own `top` is always 0) and decodes/recycles chunks
 * around the visible window. Touches are ignored — the reader's scroll container owns all
 * gestures. Each chunk is capped at [chunkHeight] display pixels so no single bitmap approaches
 * the 4096 GPU texture limit, and a fresh [BitmapRegionDecoder] is opened per chunk (cheap; no
 * decoder state is shared across coroutines).
 */
class WebtoonChunkedImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Target height (px) of each chunk in decode space. Keeps every bitmap comfortably under the
     *  GPU's 4096 texture limit and keeps per-chunk decode latency a few frames at most. */
    private val chunkHeight: Int = 2048

    /** Decode this many chunk-heights past the viewport edge (ahead = scroll direction). */
    private val decodeBehindChunks: Int = 1
    private val decodeAheadChunks: Int = 2

    /** Hard cap on retained decoded chunk bytes per page; beyond it the chunks farthest from the
     *  viewport are recycled (they re-decode on demand if the user scrolls back). */
    private val maxRetainedBytes: Int = 64 * 1024 * 1024

    private var scope: CoroutineScope? = null

    /** Builds the chunk layout for the current page (IO); the decode loop is a separate job so a
     *  finished/in-flight decode loop can be re-kicked independently of the layout build. */
    private var infoJob: Job? = null
    private var decodeJob: Job? = null

    /** Chunk indices currently being decoded by a worker (so two workers never decode the same
     *  chunk). Bookkeeping happens only on the main thread (workers decode on IO). */
    private val inFlight = mutableSetOf<Int>()

    /** Chunk indices whose decode already failed for this page. They are skipped so a transient
     *  failure isn't retried in a hot loop; only a failure on a chunk overlapping the actual
     *  viewport (or the failure of every chunk) fails the page. */
    private val failed = mutableSetOf<Int>()
    private var errorFired = false

    /** How many chunk-decode workers run in parallel per page. The global [decodeSemaphore] still
     *  bounds the total across all live pages, so several pages can't multiply this. */
    private val decodeWorkers = 2

    /** Bumped on every setChunkedImage/recycle/detach so stale in-flight work recognizes itself. */
    private var generation = 0L

    private class ChunkInfo(
        val file: File,
        val srcWidth: Int,
        val srcHeight: Int,
        val sample: Int,
        val partCount: Int,
        val chunkHeight: Int,
        /** Approx bytes of one full chunk bitmap (decode width x chunk height x bpp). */
        val chunkBytes: Int,
    ) {
        fun srcTop(i: Int): Int = i * chunkHeight * sample
        fun srcBottom(i: Int): Int = minOf(srcHeight, (i + 1) * chunkHeight * sample)
    }

    private var info: ChunkInfo? = null

    /** Decoded chunk bitmaps, aligned to fixed display slots (index i occupies display rows
     *  [i*step, (i+1)*step) where step = viewHeight/partCount). Null entries are not decoded (yet). */
    private val bitmaps = ArrayList<Bitmap?>(0)

    private var decodeWidth: Int = 0
    private var rgb565: Boolean = false
    private var readyFired = false

    private var decodeWindow: IntRange? = null

    /** The most recent [setChunkedImage] request, remembered in case it arrives before the view is
     *  attached to a window (a fresh bind): the per-view coroutine scope only exists while attached,
     *  so the load is started from [onAttachedToWindow] instead of being dropped. */
    private var pendingFile: File? = null

    var onReady: (() -> Unit)? = null
    var onError: (() -> Unit)? = null

    /** The recycler this page lives in (cached at attach) — drives viewport-height reads and the
     *  scroll listener that re-targets decoding as the user scrolls. */
    private var recyclerView: RecyclerView? = null
    private var scrollListener: RecyclerView.OnScrollListener? = null

    /** Viewport rows (display px) last applied by [updateVisible]. onDraw draws only the chunk
     *  range overlapping them, and any pass that sees the viewport move re-targets decoding. */
    private var lastViewportTop = 0
    private var lastViewportBottom = 0

    /** Coalesced re-target: at most one updateVisible is queued at a time, so a scroll burst (or
     *  the per-draw viewport check) never floods the main thread with redundant window passes. */
    private var visibleUpdatePosted = false
    private val visibleUpdateRunnable = Runnable {
        visibleUpdatePosted = false
        updateVisible()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        var p = parent
        while (p != null) {
            if (p is RecyclerView) {
                recyclerView = p
                break
            }
            p = p.parent
        }
        scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                scheduleUpdateVisible()
            }
        }
        recyclerView?.addOnScrollListener(scrollListener!!)
        // A setChunkedImage that landed before we were attached starts here (the scope it needed
        // didn't exist yet, and without this the first-bound page would stay blank forever).
        if (info == null) pendingFile?.let { startLoad(generation, it) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scrollListener?.let { recyclerView?.removeOnScrollListener(it) }
        scrollListener = null
        recyclerView = null
        removeCallbacks(visibleUpdateRunnable)
        visibleUpdatePosted = false
        cancelAll()
        scope?.cancel()
        scope = null
        releaseChunks()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // A page appearing at its slot (or a relayout without a scroll event) still needs its
        // viewport window targeted before the first draw.
        if (info != null) scheduleUpdateVisible()
    }

    /** Starts (re)loading [file] as display-width chunks. Any previous load is cancelled and its
     *  bitmaps recycled (holder rebound to another page). */
    fun setChunkedImage(file: File, decodeWidthPx: Int, decodeRgb565: Boolean) {
        cancelAll()
        generation++
        val gen = generation
        this.decodeWidth = decodeWidthPx.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        this.rgb565 = decodeRgb565
        readyFired = false
        invalidate()
        pendingFile = file
        startLoad(gen, file)
    }

    private fun startLoad(gen: Long, file: File) {
        val sc = scope ?: return
        infoJob = sc.launch {
            val built = try {
                withContext(Dispatchers.IO) { buildChunkInfo(file, decodeWidth) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (gen == generation) onError?.invoke()
                return@launch
            }
            if (gen != generation || !isActive) return@launch
            info = built
            bitmaps.clear()
            repeat(built.partCount) { bitmaps.add(null) }
            decodeWindow = null
            ReaderDiagnostics.log(
                "chunked info: decodeW=${built.srcWidth / built.sample} " +
                    "sample=${built.sample} partCount=${built.partCount} " +
                    "chunkBytes=${built.chunkBytes / 1024}KB",
            )
            invalidate()
            updateVisible()
        }
    }

    /** Cancels any in-flight decode and frees the decoded chunks (holder recycled / page changed). */
    fun recycle() {
        cancelAll()
        generation++
        releaseChunks()
        invalidate()
    }

    private fun cancelAll() {
        infoJob?.cancel()
        infoJob = null
        decodeJob?.cancel()
        decodeJob = null
        inFlight.clear()
        failed.clear()
        errorFired = false
        info = null
        decodeWindow = null
    }

    private fun releaseChunks() {
        for (b in bitmaps) if (b != null) recycleChunk(b)
        bitmaps.clear()
    }

    private fun recycleChunk(b: Bitmap) {
        if (!b.isRecycled) runCatching { b.recycle() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cur = info ?: return
        if (bitmaps.isEmpty()) return
        // Cheap field reads only: if the viewport moved since the last pass, queue a posted
        // re-target (the actual window scan/recycle/decode kick never runs inside draw).
        val vTop = viewportTop()
        val vBottom = vTop + viewportHeight()
        if (vTop != lastViewportTop || vBottom != lastViewportBottom) {
            lastViewportTop = vTop
            lastViewportBottom = vBottom
            scheduleUpdateVisible()
        }
        // Draw ONLY the chunk range overlapping the viewport (+1 margin). A long page can hold many
        // decoded chunks but only 2-4 are ever on screen — walking/drawing all of them per frame is
        // wasted work.
        val step = chunkStep()
        val first = ((lastViewportTop / step) - 1).coerceIn(0, bitmaps.lastIndex)
        val last = ((lastViewportBottom / step) + 1).coerceIn(0, bitmaps.lastIndex)
        val scale = width.coerceAtLeast(1).toFloat()
        val hTotal = height.toFloat()
        val slotH = hTotal / cur.partCount
        val dst = RectF()
        for (i in first..last) {
            val b = bitmaps[i] ?: continue
            // Equal contiguous slots: chunk i always fills rows [i*slotH, (i+1)*slotH) (the last
            // chunk stretches to the view's exact height). Because slotH is the REAL displayed
            // chunk height (total view height / chunk count), chunks tile the page with no gaps —
            // the old fixed-chunkHeight slots left a black bar between every chunk whenever the
            // decoded chunk width overshot the view width (which sampling makes almost certain).
            val bottom = if (i == bitmaps.lastIndex) hTotal else (i + 1) * slotH
            dst.set(0f, i * slotH, scale, bottom)
            canvas.drawBitmap(b, null, dst, null)
        }
    }

    /** Computes the decode-space row layout for [file]: power-of-two sample so the decoded width
     *  is at least [decodeWidthPx] (BitmapRegionDecoder only supports power-of-two sampling) and
     *  no dimension exceeds the GPU texture limit. Bounds-only decode; throws on unreadable files. */
    private fun buildChunkInfo(file: File, decodeWidthPx: Int): ChunkInfo {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val srcW = opts.outWidth
        val srcH = opts.outHeight
        if (srcW <= 0 || srcH <= 0) throw java.io.IOException("Couldn't read image dimensions")
        var sample = 1
        while (srcW / (sample * 2) >= decodeWidthPx || srcW / sample > 4096) sample *= 2
        val partCount = (srcH / sample + chunkHeight - 1) / chunkHeight
        val bpp = if (rgb565) 2 else 4
        val chunkBytes = (srcW / sample) * chunkHeight * bpp
        return ChunkInfo(file, srcW, srcH, sample, partCount, chunkHeight, chunkBytes)
    }

    /** The display-space height (px) of one chunk slot. Equal slots that tile the page exactly:
     *  total view height divided by the chunk count. Falls back to [chunkHeight] before the view
     *  has a measured height. */
    private fun chunkStep(): Int {
        val cur = info ?: return chunkHeight
        if (height > 0 && cur.partCount > 0) return (height / cur.partCount).coerceAtLeast(1)
        return chunkHeight
    }

    /** Re-targets the decode window to the current viewport, trims decoded chunks to the memory
     *  budget, and (re)starts the decode loop if it isn't running. Runs off the draw path, at most
     *  once per scroll burst (see [scheduleUpdateVisible]). */
    private fun updateVisible() {
        val cur = info ?: return
        if (bitmaps.isEmpty()) return
        val vTop = viewportTop()
        val vBottom = vTop + viewportHeight()
        lastViewportTop = vTop
        lastViewportBottom = vBottom
        val partCount = cur.partCount
        val step = chunkStep()
        val first = ((vTop / step) - decodeBehindChunks).coerceIn(0, partCount - 1)
        val last = ((vBottom / step) + decodeAheadChunks).coerceIn(0, partCount - 1)
        decodeWindow = first..last
        trimToBudget(vTop, vBottom, cur)
        kickDecodeLoop()
    }

    /** Decoded chunks stay cached for the page's lifetime (so scrolling and re-scrolling is a set
     *  of stable bitmaps — never re-decode/re-upload churn). Only when the retained total exceeds
     *  [maxRetainedBytes] are the chunks farthest from the viewport recycled, to bound memory on
     *  pathological mega-strips. */
    private fun trimToBudget(vTop: Int, vBottom: Int, cur: ChunkInfo) {
        val decoded = bitmaps.indices.filter { bitmaps[it] != null }
        if (decoded.size * cur.chunkBytes <= maxRetainedBytes) return
        val center = vTop + (vBottom - vTop) / 2
        val step = chunkStep()
        // Farthest from the viewport's centre first, so the visible/upcoming chunks are kept.
        val order = decoded.sortedBy { -abs(it * step + step / 2 - center) }
        var total = decoded.size * cur.chunkBytes
        for (i in order) {
            if (total <= maxRetainedBytes) break
            recycleChunk(bitmaps[i]!!)
            bitmaps[i] = null
            total -= cur.chunkBytes
        }
    }

    /** The scroll offset of this page's top edge within the recycler viewport, in display px.
     *  The chunked view fills its holder, so the holder's `top` (relative to the recycler content)
     *  is the page's position: when scrolled down by S, holder.top = itemTop - S, so the viewport
     *  covers page rows [-holder.top, -holder.top + viewportHeight). */
    private fun viewportTop(): Int {
        val holder = parent as? View ?: return 0
        return -holder.top
    }

    private fun viewportHeight(): Int {
        val rv = recyclerView
        if (rv != null) return rv.height
        var p = parent
        while (p != null) {
            if (p is RecyclerView) return p.height
            p = p.parent
        }
        return height
    }

    /** True if chunk [idx]'s display slot overlaps the viewport rows last seen by the draw pass. */
    private fun isChunkVisible(idx: Int): Boolean {
        val step = chunkStep()
        val first = lastViewportTop / step
        val last = lastViewportBottom / step
        return idx in first..last
    }

    private fun scheduleUpdateVisible() {
        if (visibleUpdatePosted) return
        visibleUpdatePosted = true
        post(visibleUpdateRunnable)
    }

    private fun kickDecodeLoop() {
        if (decodeJob?.isActive == true) return
        val gen = generation
        decodeJob = scope?.launch {
            val workers = List(decodeWorkers) { launch { decodeWorker(gen) } }
            workers.forEach { it.join() }
        }
    }

    /** One decode worker: pulls the missing chunk nearest the viewport's centre and decodes it,
     *  repeating until the window is fully decoded (or the page generation changed / cancelled).
     *  Several workers run concurrently so a fast fling fills blank regions quickly, but the global
     *  [decodeSemaphore] still bounds total concurrent decodes across all live pages. */
    private suspend fun CoroutineScope.decodeWorker(gen: Long) {
        while (isActive && gen == generation) {
            val idx = nextChunkToDecode() ?: return
            inFlight.add(idx)
            try {
                val bmp = decodeChunk(idx)
                if (bmp == null) {
                    // A failed chunk is skipped, not retried in a loop. Only fail the page when a
                    // chunk the user is actually looking at failed (fail fast — don't leave a hole
                    // at their viewport with a spinner), or when every chunk failed.
                    failed.add(idx)
                    if (!errorFired && (isChunkVisible(idx) || failed.size >= bitmaps.size)) {
                        errorFired = true
                        onError?.invoke()
                    }
                    continue
                }
                if (gen != generation) {
                    recycleChunk(bmp)
                    return
                }
                bitmaps[idx] = bmp
                invalidate()
                if (!readyFired) {
                    readyFired = true
                    onReady?.invoke()
                }
            } finally {
                inFlight.remove(idx)
            }
        }
    }

    /** The missing chunk in the decode window nearest the viewport's vertical centre (what the
     *  user is looking at decodes first). Null when the window is fully decoded. */
    private fun nextChunkToDecode(): Int? {
        val win = decodeWindow ?: return null
        var best: Int? = null
        var bestDist = Int.MAX_VALUE
        val step = chunkStep()
        val center = viewportTop() + viewportHeight() / 2
        for (i in win) {
            if (i < 0 || i >= bitmaps.size || bitmaps[i] != null) continue
            if (i in inFlight || i in failed) continue
            val dist = abs(i * step + step / 2 - center)
            if (dist < bestDist) {
                bestDist = dist
                best = i
            }
        }
        return best
    }

    /** Decodes one chunk on the IO dispatcher with a fresh decoder (never shared, so no concurrent
     *  decodeRegion hazard). Software ARGB_8888/RGB_565 only — BitmapRegionDecoder cannot produce
     *  hardware bitmaps (Android rejects HARDWARE for region decode; the old code attempted it and
     *  silently fell back to software on every chunk). Returns null on any decode failure. */
    private suspend fun decodeChunk(idx: Int): Bitmap? {
        val cur = info ?: return null
        return withContext(Dispatchers.IO) {
            withDecodePermit {
                runCatching {
                    val decoder = newRegionDecoder(cur.file)
                    try {
                        val rect = Rect(0, cur.srcTop(idx), cur.srcWidth, cur.srcBottom(idx))
                        val opts = BitmapFactory.Options().apply {
                            inSampleSize = cur.sample
                            inPreferredConfig = if (rgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                        }
                        decoder.decodeRegion(rect, opts)
                    } finally {
                        decoder.recycle()
                    }
                }.getOrNull()
            }
        }
    }

    private fun newRegionDecoder(file: File): BitmapRegionDecoder {
        val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(FileInputStream(file))
        } else {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(FileInputStream(file), false)
        }
        return decoder ?: throw java.io.IOException("Couldn't create image region decoder")
    }

    private companion object {
        /** Global cap on concurrent region decodes across ALL chunked pages. Live page holders
         *  (visible + prefetched) would otherwise multiply decodeWorkers (2 x N) and saturate the
         *  device's IO/CPU — and churn heap — during a fling. Small enough to stay out of the way,
         *  large enough that the visible page's workers (2) are never starved. */
        private val decodeSemaphore = Semaphore(3)

        private fun <T> withDecodePermit(block: () -> T): T {
            decodeSemaphore.acquire()
            try {
                return block()
            } finally {
                decodeSemaphore.release()
            }
        }
    }
}
