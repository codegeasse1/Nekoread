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
 * Renders a tall webtoon strip as a stack of display-width chunk bitmaps decoded from the page's
 * cache file — but LAZILY: only the chunks near the viewport are decoded (nearest-to-the-eye
 * first), and chunks that scroll far out of view are recycled so a page never holds the whole
 * strip in memory and a bind never pays a full-strip decode stall. That is the difference from
 * the previous version (which decoded every chunk at bind time — a several-hundred-ms stall per
 * page entering the viewport during a fling, plus full-strip memory for every live page).
 *
 * Render-path rules (the difference that keeps a fling smooth):
 *  - onDraw ONLY draws the chunk range overlapping the viewport (+1 margin) — never scans,
 *    recycles or launches work. A long page can hold 20+ decoded chunks but only 2-4 are on screen.
 *  - Decode/window management runs OFF the draw path, driven by the recycler's scroll listener,
 *    the view's layout pass, and the per-draw "viewport moved?" check — each coalesced into at
 *    most one posted pass, so a scroll burst can't flood the main thread with redundant scans.
 *  - Chunks decode as HARDWARE bitmaps on API 26+ (with a software fallback), so drawing them on
 *    the hardware canvas is a direct GPU blit instead of a multi-MB software-bitmap texture upload
 *    (with its heap churn) every frame. That is the classic reason a reader scrolls janky while
 *    yomi/mihon (hardware SSIV tiles) stays smooth.
 *  - A single global semaphore caps how many region decodes run at once across ALL live pages —
 *    otherwise each page's decode workers multiply (2 workers x N live holders) and saturate the
 *    device's IO/CPU during a fling.
 *
 * The view is scroll-aware: each pass reads its position in the recycler (via the holder's `top` —
 * the view itself fills the holder, so its own `top` is always 0) and decodes/recycles chunks
 * around the visible window, mihon's model. Touches are ignored — the reader's scroll container
 * owns all gestures. Each chunk is capped at [chunkHeight] display pixels so no single bitmap
 * approaches the 4096 GPU texture limit, and a fresh [BitmapRegionDecoder] is opened per chunk
 * (cheap; mihon does the same) so no decoder state is shared across coroutines.
 */
class WebtoonChunkedImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Target height (px) of each chunk in display space. Keeps every bitmap comfortably under the
     *  GPU's 4096 texture limit and keeps per-chunk decode latency a few frames at most. */
    private val chunkHeight: Int = 2048

    /** Decode this many chunk-heights past the viewport edge (ahead = scroll direction). */
    private val decodeBehindChunks: Int = 1
    private val decodeAheadChunks: Int = 2

    /** Recycle decoded chunks this many chunk-heights outside the viewport (they re-decode on
     *  demand if the user scrolls back). Must be >= the decode margins. */
    private val keepBehindChunks: Int = 2
    private val keepAheadChunks: Int = 3

    private var scope: CoroutineScope? = null

    /** Builds the chunk layout for the current page (IO); the decode loop is a separate job so a
     *  finished/in-flight decode loop can be re-kicked independently of the layout build. */
    private var infoJob: Job? = null
    private var decodeJob: Job? = null

    /** Chunk indices currently being decoded by a worker (so two workers never decode the same
     *  chunk). Bookkeeping happens only on the main thread (workers decode on IO). */
    private val inFlight = mutableSetOf<Int>()

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
    ) {
        fun srcTop(i: Int): Int = i * chunkHeight * sample
        fun srcBottom(i: Int): Int = minOf(srcHeight, (i + 1) * chunkHeight * sample)
    }

    private var info: ChunkInfo? = null

    /** Decoded chunk bitmaps, aligned to fixed display slots (index i occupies display rows
     *  [i*chunkHeight, ...)). Null entries are not decoded (yet). */
    private val bitmaps = ArrayList<Bitmap?>(0)

    private var decodeWidth: Int = 0
    private var rgb565: Boolean = false
    private var readyFired = false

    private var decodeWindow: IntRange? = null
    private var keepRange: IntRange? = null

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
        infoJob = scope?.launch {
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
            keepRange = null
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
        info = null
        decodeWindow = null
        keepRange = null
    }

    private fun releaseChunks() {
        for (b in bitmaps) if (b != null) recycleChunk(b)
        bitmaps.clear()
    }

    /** Hardware bitmaps (API 26+) must not be recycled (they throw) — they're GPU-owned and freed
     *  by GC. Software bitmaps still recycle() to release native memory promptly. */
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
        // Draw ONLY the chunk range overlapping the viewport (+1 margin). A long page can hold 20+
        // decoded chunks but only 2-4 are ever on screen — walking/drawing all of them per frame is
        // wasted work.
        val first = ((lastViewportTop / chunkHeight) - 1).coerceIn(0, bitmaps.lastIndex)
        val last = ((lastViewportBottom / chunkHeight) + 1).coerceIn(0, bitmaps.lastIndex)
        val scale = width.coerceAtLeast(1).toFloat()
        val hTotal = height.toFloat()
        val dst = RectF()
        for (i in first..last) {
            val b = bitmaps[i] ?: continue
            // Fixed display slots — chunk i always sits at rows [i*chunkHeight, i*chunkHeight+h),
            // so partially decoded pages draw in the right place (the old cumulative-y draw broke
            // once chunks could be missing).
            val h = b.height * (scale / b.width)
            val bottom = if (i == bitmaps.lastIndex) hTotal else i * chunkHeight + h
            dst.set(0f, i * chunkHeight.toFloat(), scale, bottom)
            canvas.drawBitmap(b, null, dst, null)
        }
    }

    /** Computes the display-space row layout for [file]: power-of-two sample so the decoded width
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
        return ChunkInfo(file, srcW, srcH, sample, partCount, chunkHeight)
    }

    /** Re-targets the decode window to the current viewport, recycles chunks that scrolled out of
     *  the keep range, and (re)starts the decode loop if it isn't running. Runs off the draw path,
     *  at most once per scroll burst (see [scheduleUpdateVisible]). */
    private fun updateVisible() {
        val cur = info ?: return
        if (bitmaps.isEmpty()) return
        val vTop = viewportTop()
        val vBottom = vTop + viewportHeight()
        lastViewportTop = vTop
        lastViewportBottom = vBottom
        val partCount = cur.partCount
        val first = ((vTop / chunkHeight) - decodeBehindChunks).coerceIn(0, partCount - 1)
        val last = ((vBottom / chunkHeight) + decodeAheadChunks).coerceIn(0, partCount - 1)
        val keepFirst = ((vTop / chunkHeight) - keepBehindChunks).coerceAtLeast(0)
        val keepLast = ((vBottom / chunkHeight) + keepAheadChunks).coerceAtMost(partCount - 1)
        decodeWindow = first..last
        keepRange = keepFirst..keepLast
        for (i in bitmaps.indices) {
            if (bitmaps[i] != null && (i < keepFirst || i > keepLast)) {
                recycleChunk(bitmaps[i]!!)
                bitmaps[i] = null
            }
        }
        kickDecodeLoop()
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
                val bmp = decodeChunk(idx) ?: run {
                    if (gen == generation) onError?.invoke()
                    return
                }
                if (gen != generation) {
                    recycleChunk(bmp)
                    return
                }
                if (!chunkWanted(idx)) {
                    // Scrolled out of the keep range while decoding — free it instead of caching.
                    recycleChunk(bmp)
                    continue
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
        val center = viewportTop() + viewportHeight() / 2
        for (i in win) {
            if (i < 0 || i >= bitmaps.size || bitmaps[i] != null) continue
            if (i in inFlight) continue
            val dist = abs(i * chunkHeight + chunkHeight / 2 - center)
            if (dist < bestDist) {
                bestDist = dist
                best = i
            }
        }
        return best
    }

    private fun chunkWanted(idx: Int): Boolean = keepRange?.contains(idx) == true

    /** Decodes one chunk on the IO dispatcher with a fresh decoder (never shared, so no concurrent
     *  decodeRegion hazard). Hardware bitmaps on API 26+ draw on the hardware canvas as a direct
     *  GPU blit (no per-frame software-bitmap texture upload); the decode falls back to software
     *  if the device's region decoder rejects hardware. Returns null on any decode failure. */
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
                            inPreferredConfig = preferredConfig()
                        }
                        var bmp = try {
                            decoder.decodeRegion(rect, opts)
                        } catch (e: Throwable) {
                            null
                        }
                        if (bmp == null && opts.inPreferredConfig == Bitmap.Config.HARDWARE) {
                            opts.inPreferredConfig = Bitmap.Config.ARGB_8888
                            bmp = try {
                                decoder.decodeRegion(rect, opts)
                            } catch (e: Throwable) {
                                null
                            }
                        }
                        bmp
                    } finally {
                        decoder.recycle()
                    }
                }.getOrNull()
            }
        }
    }

    private fun preferredConfig(): Bitmap.Config =
        when {
            rgb565 -> Bitmap.Config.RGB_565
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> Bitmap.Config.HARDWARE
            else -> Bitmap.Config.ARGB_8888
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
