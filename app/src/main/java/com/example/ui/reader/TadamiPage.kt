package com.example.ui.reader

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.example.data.source.MangaSource
import java.io.File
import kotlin.math.abs

/**
 * Tadami-style reader page renderer for Compose. Each page is drawn by the SAME
 * [SubsamplingScaleImageView] that Tadami/Mihon use (the tachiyomiorg fork — see
 * `subsampling-scale-image-view` in libs.versions.toml), fed from the on-device reader_pages
 * cache. SSIV decodes only the tiles visible on screen at the resolution needed, so a
 * 6000px-tall webtoon strip shows its first screen almost instantly and never allocates a
 * full-page bitmap — the two things that made the old whole-image Coil reader feel slow and
 * OOM-prone. Pinch-zoom / double-tap / pan come free (paged mode), and the long strip's
 * LazyColumn keeps owning the scroll (webtoon mode).
 *
 * Pages arrive as [File]s via [MangaSource.PageDescriptor] + the repository's page downloader
 * (`getPageImageFile`), which streams each page through the source's own client (hotlink
 * protection honoured) exactly like Tadami's HttpPageLoader.
 */
@Composable
fun TadamiPage(
    descriptor: MangaSource.PageDescriptor,
    file: File?,
    error: String?,
    isWebtoon: Boolean,
    scaleType: Int,
    spinnerColor: Color,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onSwipePage: ((Boolean) -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    // The last page file this AndroidView was asked to show. SSIV's fork exposes no reliable
    // "currently displayed path" on the base class, so the composable tracks it itself to avoid
    // re-setting the same image on every recomposition.
    var shownPath by remember { mutableStateOf<String?>(null) }
    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                val view = if (isWebtoon) WebtoonReaderImageView(ctx) else PagedReaderImageView(ctx)
                view.configure(scaleType, isWebtoon)
                if (view is PagedReaderImageView) {
                    view.onTap = onTap
                    view.onSwipePage = onSwipePage
                }
                view
            },
            update = { view ->
                if (view is PagedReaderImageView) {
                    view.onTap = onTap
                    view.onSwipePage = onSwipePage
                }
                val f = file
                val path = f?.absolutePath
                if (path != null && shownPath != path) {
                    shownPath = path
                    view.setImage(ImageSource.uri(context, Uri.fromFile(f)))
                }
            },
            onRelease = { it.recycle() },
            modifier = Modifier.fillMaxSize()
        )
        if (file == null && error == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = spinnerColor
                )
            }
        }
        if (error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x33000000))
                    .clickable(onClick = { (onRetry ?: onTap)() }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Page failed — tap to retry",
                    style = MaterialTheme.typography.bodySmall.copy(color = spinnerColor.copy(alpha = 0.9f)),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Shared SSIV config, mirroring Tadami's ReaderPageImageView.setup. */
private fun SubsamplingScaleImageView.configure(scaleType: Int, isWebtoon: Boolean) {
    setMinimumScaleType(scaleType)
    setMinimumDpi(1) // very small images still fit on first load
    setMinimumTileDpi(180)
    setMaxTileSize(2048)
    setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
    setDoubleTapZoomDuration(300)
    setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
    if (isWebtoon) {
        // Defer high-res tile decoding until gestures/scrolling settle — the same flag Tadami's
        // WebtoonSubsamplingImageView sets, so continuous scroll stays smooth on 90/120Hz.
        setEagerLoadingEnabled(false)
    }
}

/**
 * The paged-mode page view: pinch/double-tap/pan from SSIV, single-tap toggles the reader HUD
 * (SSIV fires performClick on single tap, exactly like Tadami's ReaderPageImageView), and a
 * horizontal fling at min scale turns the page — the Compose equivalent of Tadami's
 * canPanLeft/canPanRight gate for "page turn vs pan".
 */
class PagedReaderImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SubsamplingScaleImageView(context, attrs) {

    var onTap: () -> Unit = {}
    var onSwipePage: ((forward: Boolean) -> Unit)? = null

    private val flingDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (isReady && getScale() <= getMinScale() + 0.01f && abs(velocityX) > abs(velocityY)) {
                    val dx = e2.x - (e1?.x ?: e2.x)
                    if (abs(dx) > 40f) {
                        onSwipePage?.invoke(dx < 0f)
                        return true
                    }
                }
                return false
            }
        }
    )

    init {
        setOnClickListener { onTap() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (flingDetector.onTouchEvent(event)) return true
        return super.onTouchEvent(event)
    }
}

/**
 * The webtoon-mode page view: display only. It ignores every touch event (like Tadami's
 * WebtoonSubsamplingImageView) so the long strip's LazyColumn owns scrolling.
 */
class WebtoonReaderImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SubsamplingScaleImageView(context, attrs) {

    override fun onTouchEvent(event: MotionEvent): Boolean = false
}

/**
 * Read an image file's intrinsic dimensions from its header only (no pixel decode — a few ms even
 * for a 8000px strip). The reader uses this to size webtoon items to the page's real aspect ratio
 * before SSIV renders.
 */
fun decodeImageBounds(file: File): Pair<Int, Int> {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, opts)
    return (opts.outWidth to opts.outHeight)
}

/**
 * Cheap low-res render of a page for the reader's preview layer: drawn UNDER the tiled
 * SubsamplingScaleImageView while it decodes, so a page scrolling into view never shows a blank
 * frame (the tiled view's base-tile decode can take a few hundred ms on a tall strip). Capped to
 * [maxDim] on the long side (RGB_565) so it stays small; only alive while the item is composed.
 */
fun decodePreview(file: File, maxDim: Int = 2048): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) return null
    var sample = 1
    while (maxOf(w, h) / (sample * 2) >= maxDim) sample *= 2
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
    )
}
