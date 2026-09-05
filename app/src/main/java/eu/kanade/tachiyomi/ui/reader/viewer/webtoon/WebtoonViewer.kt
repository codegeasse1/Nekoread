package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.WebtoonLayoutManager
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.example.data.reader.WebtoonPageCache
import com.example.data.source.MangaSource
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import java.io.File
import java.io.IOException

/**
 * The yomi webtoon reader, ported into Nekoread. A [RecyclerView] (with the extra-layout-space
 * [WebtoonLayoutManager]) displays every page through a [ReaderPageImageView] that region-decodes
 * each strip straight from its on-device cache file — never a giant full-height bitmap, which is
 * exactly what keeps long-strip scrolling smooth on slow sources like comix. Pinch / double-tap
 * whole-strip zoom, tap zones (left = scroll up, right = scroll down, middle = toggle menu) and
 * near-end reporting mirror yomi's behavior.
 */
class WebtoonViewer(context: Context) {

    /** Recycler view used by this viewer. */
    val recycler = WebtoonRecyclerView(context)

    /** Frame containing the recycler view (handles scaled-touch translation for whole-strip zoom). */
    private val frame = WebtoonFrame(context)

    /** Distance to scroll when the user taps on one side of the recycler view. */
    private val scrollDistance = context.resources.displayMetrics.heightPixels * 3 / 4

    /** How far beyond the viewport the layout manager creates/binds pages, so pages attach (from
     *  the warmed Coil memory cache / cached metadata / the chunked decode) well before they scroll
     *  into view. One screen matches yomi: binds are cheap now (Coil memory-cache hits for short
     *  pages, one chunk decode for tall strips), so a full extra screen of runway is enough — a
     *  second screen would only keep more decoded page bitmaps alive (the 2x we used before was
     *  compensating for the SSIV view's slow per-bind region-decode). */
    private val extraLayoutSpace = context.resources.displayMetrics.heightPixels

    /** Layout manager of the recycler view. */
    private val layoutManager = WebtoonLayoutManager(context, extraLayoutSpace)

    /** Adapter of the recycler view. */
    private val adapter = WebtoonAdapter(this)

    /** The source used to download page bytes (through its own client) into [cacheDir]. */
    var source: MangaSource? = null

    /** On-device cache dir holding the downloaded page image files. */
    var cacheDir: File = File(context.cacheDir, "webtoon_pages")

    /** Whether a small gap is added between pages (the "vertical with gaps" mode). */
    var gaps: Boolean = false

    /** Whether the page image viewer crops the (often white/black) borders off each strip. */
    var cropBorders: Boolean = false
        set(value) {
            field = value
            pageConfig = pageConfig.copy(cropBorders = value)
        }

    /** Whether double-tapping a page zooms it in/out (yomi's "Double tap to zoom"). */
    var doubleTapZoom: Boolean = true
        set(value) {
            field = value
            frame.doubleTapZoom = value
        }

    /** Whether tapping the left/right side of the screen scrolls (yomi's tap zones). */
    var tapToChangePages: Boolean = false

    /** Target decode width (px) for short webtoon pages that render via Coil (yomi's fast path).
     *  Set by the reader from the quality-scaled display width; 0 means the screen width. */
    var decodeWidth: Int = 0

    /** Tall strips (the chunked decode path) decode as RGB_565 at Low image quality — half the
     *  memory per chunk. Set by the reader; mirrors the short-page path's quality handling. */
    var decodeRgb565: Boolean = false

    /** Rendering config for each page (fit-width, pinch-to-zoom, ...). */
    var pageConfig: ReaderPageImageView.Config = ReaderPageImageView.Config(
        zoomDuration = 200,
        minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
        cropBorders = false,
        webtoonSmartFit = false,
        enablePinchToZoom = true,
    )

    /** Called when the current page changes (segment index, 1-based page, page total). */
    var onPageChanged: ((seg: Int, page: Int, pageTotal: Int) -> Unit)? = null

    /** Called when the user enters/leaves the last few pages of the last streamed chapter. */
    var onNearEndChanged: ((Boolean) -> Unit)? = null

    /** Called when the user taps the center of the screen (toggle the reader menu). */
    var onMenuTap: (() -> Unit)? = null

    /** Called when the user presses "Retry" in the failed-next-chapter trailer. */
    var onTrailerRetry: (() -> Unit)? = null

    /** Called on any user touch (drag or tap) — used to stop auto-scroll. */
    var onUserScroll: (() -> Unit)? = null

    /** Text color for the reader chrome (dividers, errors, trailer text). */
    var textColor: Int = AndroidColor.WHITE
        private set

    /** Current trailing item kind. */
    var trailer: WebtoonTrailer = WebtoonTrailer.None
        private set

    private var positioned = false
    private var reportedPage: Triple<Int, Int, Int>? = null
    private var lastNearEnd: Boolean? = null

    /** The view this viewer owns (the zoom frame containing the recycler). */
    val view: View get() = frame

    init {
        recycler.setItemViewCacheSize(4)
        recycler.isVisible = false // Don't let the recycler layout until items are set
        recycler.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        recycler.isFocusable = false
        recycler.itemAnimator = null
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter
        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateCurrentPage()
                    updateNearEnd()
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        onUserScroll?.invoke()
                    }
                }
            },
        )
        recycler.tapListener = { event -> handleTap(event) }
        frame.doubleTapZoom = doubleTapZoom
        frame.zoomOutDisabled = false
        frame.enablePinchToZoom = true
        frame.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        frame.addView(recycler)
    }

    /** Sets the reader's items, sizes and trailer, scrolling to [initialPage] on first layout. */
    fun setItems(items: List<WebtoonItem>, segSizes: List<Int>, trailer: WebtoonTrailer, initialPage: Int) {
        adapter.segSizes = segSizes
        adapter.submit(items, trailer)
        this.trailer = trailer
        if (!positioned) {
            positioned = true
            recycler.isVisible = true
            moveToPage(initialPage)
        } else {
            recycler.post { updateCurrentPage() }
        }
    }

    /** Updates only the trailing item (spinner / error / end) without touching the pages. */
    fun setTrailer(trailer: WebtoonTrailer) {
        if (this.trailer == trailer) return
        adapter.submit(adapter.items, trailer)
        this.trailer = trailer
    }

    /** Applies the reader background and chrome text color. */
    fun setTheme(bgColor: Int, textColor: Int) {
        recycler.setBackgroundColor(bgColor)
        this.textColor = textColor
        adapter.setTheme(textColor)
    }

    /** Scrolls so the given adapter position is at the top of the viewport. */
    fun moveToPage(adapterPosition: Int) {
        val pos = adapterPosition.coerceIn(0, (adapter.itemCount - 1).coerceAtLeast(0))
        layoutManager.scrollToPositionWithOffset(pos, 0)
        recycler.post {
            reportedPage = null
            updateCurrentPage()
        }
    }

    /** Scrolls the strip by [dy] pixels (used by auto-scroll and tap zones). */
    fun scrollBy(dy: Int) {
        recycler.scrollBy(0, dy)
    }

    /** Downloads the page's bytes once to the cache file and returns it. */
    suspend fun loadPage(item: WebtoonItem.Page): File {
        val src = source ?: throw IOException("No source available for this manga")
        return WebtoonPageCache.fileFor(item.desc, src, cacheDir)
    }

    /** Yomi tap zones: with "tap to change pages" ON, left third scrolls up, right third scrolls
     *  down, middle toggles the menu. With it OFF (the default) ANY tap anywhere toggles the menu. */
    private fun handleTap(event: MotionEvent) {
        onUserScroll?.invoke()
        if (!tapToChangePages) {
            onMenuTap?.invoke()
            return
        }
        val width = recycler.width.coerceAtLeast(1)
        val x = event.x / width
        when {
            x < 0.34f -> scrollBy(-scrollDistance)
            x > 0.66f -> scrollBy(scrollDistance)
            else -> onMenuTap?.invoke()
        }
    }

    /** True if [file] is a tall webtoon strip (height > 3x width — yomi/mihon's rule) — the ones
     *  SSIV region-decodes from disk instead of decoding whole via Coil. Bounds-only decode; falls
     *  back to tall on error. */
    fun isTallPage(file: File): Boolean {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            WebtoonPageCache.isTallPage(opts.outWidth, opts.outHeight)
        } catch (e: Throwable) {
            true
        }
    }

    /** The last Page item whose position is at or above the viewport's bottom edge. */
    private fun currentLastPage(): WebtoonItem.Page? {
        val lastEnd = layoutManager.findLastEndVisibleItemPosition()
        if (lastEnd == RecyclerView.NO_POSITION) return null
        for (i in lastEnd downTo 0) {
            (adapter.items.getOrNull(i) as? WebtoonItem.Page)?.let { return it }
        }
        return null
    }

    private fun updateCurrentPage() {
        val page = currentLastPage() ?: return
        val pageTotal = adapter.segSizes.getOrElse(page.segIndex) { page.number }
        val key = Triple(page.segIndex, page.number, pageTotal)
        if (reportedPage != key) {
            reportedPage = key
            onPageChanged?.invoke(page.segIndex, page.number, pageTotal)
        }
    }

    private fun updateNearEnd() {
        val page = currentLastPage()
        val near = if (page == null) {
            false
        } else {
            val lastSeg = adapter.segSizes.lastIndex
            val lastSize = adapter.segSizes.getOrElse(lastSeg) { 0 }
            page.segIndex == lastSeg && lastSize > 0 && page.number >= (lastSize - 3).coerceAtLeast(1)
        }
        if (lastNearEnd != near) {
            lastNearEnd = near
            onNearEndChanged?.invoke(near)
        }
    }
}
