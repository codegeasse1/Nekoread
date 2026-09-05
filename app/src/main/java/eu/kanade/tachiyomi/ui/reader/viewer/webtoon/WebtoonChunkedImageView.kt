package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileInputStream
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
 * cache file — but LAZILY: only the chunks near the viewport are decoded, and chunks that scroll
 * far out of view are recycled so a page never holds the whole strip in memory and a bind never
 * pays a full-strip decode stall.
 *
 * Decoding is a GLOBAL pipeline shared by every live page (yomi/mihon's model: one small pool of
 * dedicated decode threads instead of N pages x M workers each decoding independently). Each page
 * pushes the chunks it needs into a single priority queue ordered by (is this the page under the
 * user's finger, then distance from the viewport centre), so a fling always fills what the user is
 * looking at first and the total decode concurrency is bounded by the pool size (2 threads) no
 * matter how many pages are alive. Bitmaps decode as HARDWARE bitmaps on API 26+ (direct GPU blit
 * when drawn — no per-frame multi-MB software-bitmap texture upload / heap churn, the classic
 * reason a reader scrolls janky while yomi stays smooth), with a software fallback.
 *
 * Render-path rules: onDraw ONLY draws the chunk range overlapping the viewport (+1 margin) and a
 * cheap "did the viewport move?" check that schedules a posted re-target — window scans, recycling
 * and decode requests never run inside draw. The posted pass is coalesced (at most one queued), so
 * a scroll burst can't flood the main thread.
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
    private val decodeAheadChunks: Int = 3

    /** Recycle decoded chunks this many chunk-heights outside the viewport (they re-decode on
     *  demand if the user scrolls back). Must be >= the decode margins. */
    private val keepBehindChunks: Int = 2
    private val keepAheadChunks: Int = 4

    private var scope: CoroutineScope? = null

    /** Builds the chunk layout for the current page (IO); decode itself is global (see companion). */
    private var infoJob: Job? = null

    /** Bumped on every setChunkedImage/recycle/detach so stale queued work recognizes itself. */
    private var generation = 0L

    /** Unique identity for this view instance in the global queue. */
    private val viewId = DecodeQueue.allocateViewId()

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
        DecodeQueue.ensureStarted()
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
        DecodeQueue.cancelView(viewId)
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
        // re-target (the actual window scan/recycle/request never runs inside draw).
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
     *  the keep range, and requests the missing chunks from the global decode queue. Runs off the
     *  draw path, at most once per scroll burst (see [scheduleUpdateVisible]). */
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
        // Trim queued-but-not-yet-decoded requests that just scrolled out of the keep range (a fast
        // fling past them would otherwise waste a decode), then push what's still missing.
        DecodeQueue.trimTo(viewId, generation, keepFirst..keepLast)
        val active = isActivePage()
        val center = vTop + vBottom / 2
        for (i in first..last) {
            if (i < 0 || i >= bitmaps.size || bitmaps[i] != null) continue
            val dist = abs(i * chunkHeight + chunkHeight / 2 - center)
            DecodeQueue.request(this, viewId, generation, i, active, dist)
        }
    }

    /** True when this page's holder contains the viewport's vertical centre — the page the user is
     *  actually reading. Its chunks decode before any other page's, no matter how far ahead. */
    private fun isActivePage(): Boolean {
        val vTop = viewportTop()
        val center = vTop + viewportHeight() / 2
        return center >= vTop && center <= vTop + height
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

    /** Runs on the main thread when a decode thread has produced [bmp] for [chunk] of generation
     *  [gen] (stale results are discarded). Places the chunk if it's still wanted, else frees it. */
    private fun onChunkDecoded(gen: Long, chunk: Int, bmp: Bitmap?) {
        if (gen != generation) {
            if (bmp != null) recycleChunk(bmp)
            return
        }
        if (bmp == null) {
            if (gen == generation) onError?.invoke()
            return
        }
        if (!chunkWanted(chunk) || bitmaps.getOrNull(chunk) != null) {
            // Scrolled out of the keep range while decoding, or already replaced — free it instead
            // of caching.
            recycleChunk(bmp)
            return
        }
        bitmaps[chunk] = bmp
        invalidate()
        if (!readyFired) {
            readyFired = true
            onReady?.invoke()
        }
    }

    private fun chunkWanted(idx: Int): Boolean = keepRange?.contains(idx) == true

    /** Decodes one chunk on a global decode thread (see companion): fresh decoder per chunk (never
     *  shared, so no concurrent decodeRegion hazard). Hardware bitmaps on API 26+ draw on the
     *  hardware canvas as a direct GPU blit; the decode falls back to software if the device's
     *  region decoder rejects hardware. Returns null on any decode failure. */
    private fun decodeChunkBlocking(chunk: Int): Bitmap? {
        val cur = info ?: return null
        return runCatching {
            val decoder = newRegionDecoder(cur.file)
            try {
                val rect = Rect(0, cur.srcTop(chunk), cur.srcWidth, cur.srcBottom(chunk))
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

        /**
         * The global chunk-decode pipeline shared by every live page (yomi/mihon's model). A small
         * pool of dedicated decode threads (2) pulls the highest-priority request from all pages —
         * priority is "the page under the user's finger first, then nearest the viewport centre".
         * Bounding total decode concurrency to the pool size (instead of per-page worker counts,
         * which multiply across live pages and saturate the device during a fling) is a big part of
         * why yomi scrolls smoothly where per-page pipelines stutter.
         */
        private object DecodeQueue {

            private class Task(
                val viewRef: WeakReference<WebtoonChunkedImageView>,
                val viewId: Int,
                val gen: Long,
                val chunk: Int,
                var priority: Int,
            )

            private const val THREAD_COUNT = 2
            private const val INACTIVE_PAGE_BONUS = 1_000_000

            private val queue = PriorityBlockingQueue<Task>(64) { a, b -> a.priority.compareTo(b.priority) }
            private val pending = ConcurrentHashMap<Long, Task>()
            private val started = AtomicBoolean(false)
            private val main = Handler(Looper.getMainLooper())
            private val viewIdCounter = AtomicInteger(0)

            fun allocateViewId(): Int = viewIdCounter.incrementAndGet()

            private fun keyOf(viewId: Int, gen: Long, chunk: Int): Long {
                var key = viewId.toLong() * 1_000_003L
                key = key * 31 + gen
                key = key * 31 + chunk
                return key
            }

            fun ensureStarted() {
                if (!started.compareAndSet(false, true)) return
                repeat(THREAD_COUNT) { i ->
                    Thread {
                        while (true) {
                            val task = try {
                                queue.take()
                            } catch (e: InterruptedException) {
                                return@Thread
                            }
                            if (!pending.remove(keyOf(task.viewId, task.gen, task.chunk), task)) continue
                            val view = task.viewRef.get()
                            if (view == null || view.generation != task.gen) continue
                            val bmp = view.decodeChunkBlocking(task.chunk)
                            main.post { view.onChunkDecoded(task.gen, task.chunk, bmp) }
                        }
                    }.apply {
                        isDaemon = true
                        name = "chunk-decode-$i"
                    }.start()
                }
            }

            /** Queues a decode request if it isn't already queued for this (view, generation, chunk).
             *  Called from the main thread (updateVisible). */
            fun request(
                view: WebtoonChunkedImageView,
                viewId: Int,
                gen: Long,
                chunk: Int,
                active: Boolean,
                distance: Int,
            ) {
                val key = keyOf(viewId, gen, chunk)
                if (pending.containsKey(key)) return
                val task = Task(
                    viewRef = WeakReference(view),
                    viewId = viewId,
                    gen = gen,
                    chunk = chunk,
                    priority = if (active) distance else INACTIVE_PAGE_BONUS + distance,
                )
                if (pending.putIfAbsent(key, task) != null) return
                queue.put(task)
            }

            /** Removes queued requests whose chunk fell outside [keepRange] (called on each window
             *  pass so a fast fling doesn't decode chunks the page has already scrolled past). */
            fun trimTo(viewId: Int, gen: Long, keepRange: IntRange) {
                val it = pending.values.iterator()
                while (it.hasNext()) {
                    val t = it.next()
                    if (t.viewId == viewId && t.gen == gen && t.chunk !in keepRange) {
                        it.remove()
                        queue.remove(t)
                    }
                }
            }

            /** Drops all queued requests for a view (recycled / detached / page changed). */
            fun cancelView(viewId: Int) {
                val it = pending.values.iterator()
                while (it.hasNext()) {
                    val t = it.next()
                    if (t.viewId == viewId) {
                        it.remove()
                        queue.remove(t)
                    }
                }
            }
        }
    }
}
