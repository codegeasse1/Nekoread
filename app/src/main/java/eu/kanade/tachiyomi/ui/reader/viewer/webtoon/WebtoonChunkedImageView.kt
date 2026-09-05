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
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Renders a tall webtoon strip as a stack of display-width chunk bitmaps decoded ONCE (off the
 * main thread) from the page's cache file, instead of a SubsamplingScaleImageView that
 * region-decodes tiles continuously while scrolling. Tile re-decode churn during a fling is the
 * main source of dropped frames on long strips — chunking removes it: the whole strip is decoded
 * into N chunks up front, then scrolling is just the GPU moving already-decoded bitmaps (the same
 * model mihon's tall-image splitter uses). Each chunk is capped at [CHUNK_HEIGHT] display pixels
 * so no single bitmap approaches the 4096 GPU texture limit, and all chunks are recycled with the
 * view. Touches are ignored — the reader's scroll container owns all gestures, like the SSIV it
 * replaces.
 */
class WebtoonChunkedImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Target height (px) of each chunk in display space. Keeps every bitmap comfortably under the
     *  GPU's 4096 texture limit while keeping the number of separate bitmaps small. */
    private val chunkHeight: Int = 2048

    private var scope: CoroutineScope? = null
    private var decodeJob: Job? = null

    /** The decoded chunk bitmaps, drawn top-to-bottom. */
    private val chunks = ArrayList<Bitmap>(4)

    private var decodeWidth: Int = 0
    private var rgb565: Boolean = false

    var onReady: (() -> Unit)? = null
    var onError: (() -> Unit)? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        decodeJob?.cancel()
        decodeJob = null
        scope?.cancel()
        scope = null
    }

    /** Starts decoding [file] into display-width chunks. Any previous decode is cancelled and its
     *  chunks recycled (holder rebound to another page). */
    fun setChunkedImage(file: File, decodeWidthPx: Int, decodeRgb565: Boolean) {
        decodeJob?.cancel()
        releaseChunks()
        this.decodeWidth = decodeWidthPx.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        this.rgb565 = decodeRgb565
        invalidate()
        decodeJob = scope?.launch {
            try {
                val decoded = withContext(Dispatchers.IO) { decodeChunks(file) }
                if (!isActive) return@launch
                chunks.addAll(decoded)
                invalidate()
                onReady?.invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                releaseChunks()
                onError?.invoke()
            }
        }
    }

    /** Cancels any in-flight decode and frees the decoded chunks (holder recycled / page changed). */
    fun recycle() {
        decodeJob?.cancel()
        decodeJob = null
        releaseChunks()
        invalidate()
    }

    private fun releaseChunks() {
        for (b in chunks) b.recycle()
        chunks.clear()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (chunks.isEmpty()) return
        val w = width.coerceAtLeast(1)
        val hTotal = height.toFloat()
        var y = 0f
        val dst = RectF()
        val scale = w.toFloat()
        for (i in chunks.indices) {
            val b = chunks[i]
            val h = b.height * (scale / b.width)
            val bottom = if (i == chunks.lastIndex) hTotal else y + h
            dst.set(0f, y, scale, bottom)
            canvas.drawBitmap(b, null, dst, null)
            y = bottom
        }
    }

    /** Decodes the strip into display-width chunks. Runs on the IO dispatcher — never the main
     *  thread. Regions are in source pixels; the power-of-two sample maps them to [decodeWidth]. */
    private suspend fun decodeChunks(file: File): List<Bitmap> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val srcW = opts.outWidth
        val srcH = opts.outHeight
        if (srcW <= 0 || srcH <= 0) throw java.io.IOException("Couldn't read image dimensions")

        // Power-of-two downscale so the decoded width is >= the display width (BitmapRegionDecoder
        // only supports power-of-two sampling). Also keep any single dimension under the 4096
        // texture limit for pathological sources.
        var sample = 1
        while (srcW / (sample * 2) >= decodeWidth || srcW / sample > 4096) sample *= 2

        val scaledH = srcH / sample
        val partCount = (scaledH + chunkHeight - 1) / chunkHeight

        val decoder = newRegionDecoder(file)
        val out = ArrayList<Bitmap>(partCount)
        try {
            for (i in 0 until partCount) {
                currentCoroutineContext().ensureActive()
                val top = i * chunkHeight * sample
                val bottom = minOf(srcH, (i + 1) * chunkHeight * sample)
                val b = decoder.decodeRegion(
                    Rect(0, top, srcW, bottom),
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig =
                            if (rgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                    },
                )
                if (b == null) throw java.io.IOException("Region decode failed at chunk $i")
                out.add(b)
            }
            return out
        } catch (e: CancellationException) {
            for (b in out) b.recycle()
            throw e
        } catch (e: Throwable) {
            for (b in out) b.recycle()
            throw e
        } finally {
            decoder.recycle()
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
