package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.data.reader.WebtoonPageCache
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holder of the yomi webtoon reader for a single page of a chapter (ported from yomi's
 * WebtoonPageHolder): downloads the page's bytes ONCE to the on-device cache file (single-flighted
 * with the preload loop via [WebtoonPageCache]), then hands the file to the [ReaderPageImageView]
 * which region-decodes only the visible slice from disk — never a giant full-height bitmap. While
 * downloading/decoding a progress container keeps the holder at viewport height; failures show a
 * retry button.
 */
class WebtoonPageHolder(
    private val frame: ReaderPageImageView,
    private val viewer: WebtoonViewer,
) : androidx.recyclerview.widget.RecyclerView.ViewHolder(frame) {

    /** Loading progress bar to indicate the current progress. */
    private val progressBar: ProgressBar

    /** Progress container. Kept at a minimum height (the viewport) so the adapter doesn't create
     *  more views to fill the screen while a page loads. */
    private lateinit var progressContainer: FrameLayout

    /** Error layout to show when the image fails to load. */
    private var errorLayout: LinearLayout? = null

    /** Current page bound to this holder (for the retry button). */
    private var item: WebtoonItem.Page? = null

    private val scope = MainScope()

    /** Job for loading the page. */
    private var loadJob: Job? = null

    private val parentHeight
        get() = viewer.recycler.height

    init {
        refreshLayoutParams()

        frame.onImageLoaded = { onImageDecoded() }
        frame.onImageLoadError = { setError() }

        progressBar = createProgressIndicator()
    }

    /** Binds the given [page] to this holder and starts loading its cache file. */
    fun bind(item: WebtoonItem.Page) {
        this.item = item
        loadJob?.cancel()
        removeErrorLayout()
        progressContainer.isVisible = true
        refreshLayoutParams()
        refreshPlaceholderHeight()
        loadJob = scope.launch {
            try {
                // Pre-size the holder to the page's REAL height (when its cache file is already on
                // disk — the prewarm downloads pages well ahead, so nearly every page is known) so
                // loading the image never relayouts the list. That per-image relayout (viewport ->
                // image height) is a major scroll-stutter source. Unknown pages keep the
                // viewport-height placeholder and settle once the image lands.
                val d = WebtoonPageCache.dimensions(item.desc, viewer.cacheDir)
                val rw = viewer.recycler.width.takeIf { it > 0 } ?: frame.context.resources.displayMetrics.widthPixels
                val targetH = if (d != null) {
                    (rw.toFloat() * d.second / d.first).toInt().coerceAtLeast(1)
                } else {
                    WRAP_CONTENT
                }
                val lp = frame.layoutParams as? FrameLayout.LayoutParams
                if (lp != null && lp.height != targetH) lp.height = targetH

                val file = viewer.loadPage(item)
                // Sniff the strip's height ratio off the UI thread so the viewer can pick the fast
                // Coil path for short pages (like yomi) and reserve region-decoding for tall strips.
                val tall = withContext(Dispatchers.IO) { viewer.isTallPage(file) }
                frame.decodeWidthPx = viewer.decodeWidth
                frame.setImage(
                    file,
                    frame.isAnimatedFile(file),
                    viewer.pageConfig.copy(isTallImage = tall),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                setError()
            }
        }
    }

    private fun refreshLayoutParams() {
        val bottomMargin = if (viewer.gaps) dp(15) else 0

        // Avoid layout thrash: rebinds while scrolling must not trigger a requestLayout
        // when nothing about the layout params actually changed.
        val current = frame.layoutParams as? FrameLayout.LayoutParams
        if (current != null && current.bottomMargin == bottomMargin) {
            return
        }
        frame.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            this.bottomMargin = bottomMargin
        }
    }

    /** Keeps the loading placeholder matched to the current viewport height (rotation etc). */
    private fun refreshPlaceholderHeight() {
        val height = parentHeight
        if (height <= 0) return
        val params = progressContainer.layoutParams
        if (params != null && params.height != height) {
            progressContainer.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, height)
        }
    }

    /** Called when the view is recycled and added to the view pool. */
    fun recycle() {
        loadJob?.cancel()
        loadJob = null
        removeErrorLayout()
        frame.recycle()
        progressContainer.isVisible = true
    }

    /** Called when the image is decoded and going to be displayed. */
    private fun onImageDecoded() {
        progressContainer.isVisible = false
        removeErrorLayout()
    }

    /** Called when the page has an error. */
    private fun setError() {
        progressContainer.isVisible = false
        if (errorLayout == null) {
            errorLayout = LinearLayout(frame.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    MATCH_PARENT,
                    (viewer.recycler.height * 0.8).toInt().coerceAtLeast(400),
                )
                val msg = TextView(frame.context).apply {
                    text = "Couldn't load page"
                    textSize = 14f
                    setTextColor(viewer.textColor)
                }
                addView(msg)
                val retry = Button(frame.context).apply {
                    text = "Retry"
                    setOnClickListener { item?.let { bind(it) } }
                }
                addView(retry, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(12) })
            }
            frame.addView(errorLayout)
        }
    }

    /** Removes the error layout from the holder, if found. */
    private fun removeErrorLayout() {
        errorLayout?.let {
            frame.removeView(it)
            errorLayout = null
        }
    }

    /** Creates a new progress bar centered in a viewport-height container. */
    private fun createProgressIndicator(): ProgressBar {
        progressContainer = FrameLayout(frame.context)
        frame.addView(progressContainer, MATCH_PARENT, parentHeight.coerceAtLeast(1))
        return ProgressBar(frame.context).also {
            val lp = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)
            progressContainer.addView(it, lp)
        }
    }
}

private fun dp(value: Int): Int =
    (value * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
