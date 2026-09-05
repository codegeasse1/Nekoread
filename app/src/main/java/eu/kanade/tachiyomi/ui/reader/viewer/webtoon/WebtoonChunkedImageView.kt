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
 * The view is scroll-aware: each draw it reads its position in the recycler (via the holder's
 * `top` — the view itself fills the holder, so its own `top` is always 0) and decodes/recycles
 * chunks around the visible window, mihon's model. Touches are ignored — the reader's scroll
 * container owns all gestures. Each chunk is capped at [chunkHeight] display pixels so no single
 * bitmap approaches the 4096 GPU texture limit, and a fresh [BitmapRegionDecoder] is opened per
 * chunk (cheap; mihon does the same) so no decoder state is shared across coroutines.
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAll()
        scope?.cancel()
        scope = null
        releaseChunks()
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
        info = null
        decodeWindow = null
        keepRange = null
    }

    private fun releaseChunks() {
        for (b in bitmaps) b?.recycle()
        bitmaps.clear()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateVisible()
        val w = width.coerceAtLeast(1)
        val scale = w.toFloat()
        val hTotal = height.toFloat()
        val dst = RectF()
        for (i in bitmaps.indices) {
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

    /** Called on every draw: extends the decode window to the current viewport, recycles chunks
     *  that scrolled out of the keep range, and (re)starts the decode loop if it isn't running. */
    private fun updateVisible() {
        val cur = info ?: return
        if (bitmaps.isEmpty()) return
        val vTop = viewportTop()
        val vBottom = vTop + viewportHeight()
        val partCount = cur.partCount
        val first = ((vTop / chunkHeight) - decodeBehindChunks).coerceIn(0, partCount - 1)
        val last = ((vBottom / chunkHeight) + decodeAheadChunks).coerceIn(0, partCount - 1)
        val keepFirst = ((vTop / chunkHeight) - keepBehindChunks).coerceAtLeast(0)
        val keepLast = ((vBottom / chunkHeight) + keepAheadChunks).coerceAtMost(partCount - 1)
        decodeWindow = first..last
        keepRange = keepFirst..keepLast
        for (i in bitmaps.indices) {
            if (bitmaps[i] != null && (i < keepFirst || i > keepLast)) {
                bitmaps[i]?.recycle()
                bitmaps[i] = null
            }
        }
        kickDecodeLoop()
    }

    /** The scroll offset of this page's top edge within the recycler viewport, in display px.
     *  The chunked view fills its holder, so the holder's `top` (relative to the recycler content)
     *  is the page's position: when scrolled down by S, holder.top = itemTop - S, so the viewport
     *  covers page rows [-holder.top, -holder.top + viewportHeight). */
    private fun viewportTop(): Int = -(parent as? View)?.top ?: 0

    private fun viewportHeight(): Int {
        var p = parent
        while (p != null) {
            if (p is RecyclerView) return p.height
            p = p.parent
        }
        return height
    }

    private fun kickDecodeLoop() {
        if (decodeJob?.isActive == true) return
        val gen = generation
        decodeJob = scope?.launch {
            while (isActive && gen == generation) {
                val idx = nextChunkToDecode() ?: break
                val bmp = decodeChunk(idx) ?: run {
                    if (gen == generation) onError?.invoke()
                    break
                }
                if (gen != generation) {
                    bmp.recycle()
                    return@launch
                }
                if (!chunkWanted(idx)) {
                    // Scrolled out of the keep range while decoding — free it instead of caching.
                    bmp.recycle()
                    continue
                }
                bitmaps[idx] = bmp
                invalidate()
                if (!readyFired) {
                    readyFired = true
                    onReady?.invoke()
                }
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
     *  decodeRegion hazard). Returns null on any decode failure. */
    private suspend fun decodeChunk(idx: Int): Bitmap? {
        val cur = info ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val decoder = newRegionDecoder(cur.file)
                try {
                    decoder.decodeRegion(
                        Rect(0, cur.srcTop(idx), cur.srcWidth, cur.srcBottom(idx)),
                        BitmapFactory.Options().apply {
                            inSampleSize = cur.sample
                            inPreferredConfig =
                                if (rgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                        },
                    )
                } finally {
                    decoder.recycle()
                }
            }.getOrNull()
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
}
