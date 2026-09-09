package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.ColorFilter
import android.graphics.PointF
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.StyleRes
import androidx.core.view.isVisible
import coil.dispose
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.example.data.coil.cropBorders
import com.example.data.reader.WebtoonPageCache
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonBorderDetector
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonChunkedImageView
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSubsamplingImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * The webtoon page view ported from yomi/chimahon. Non-tall webtoon pages (height ≤ 3x width) are
 * decoded ONCE by Coil at the strip's display width and shown in a plain [ImageView] — a single,
 * memory-cached decode (warmed by the reader's preload loop) instead of a per-bind region-decode
 * pipeline, which is what keeps scrolling smooth. TALL strips (long webtoon pages, h > 3w) are
 * likewise decoded WHOLE at the display width by Coil into one software bitmap shown in a plain
 * [ImageView] — a single stable bitmap drawn every frame (the render thread caches its textures),
 * with no per-frame tile/chunk decoding, which is what finally keeps comix flings smooth. Only
 * strips whose single decode would exceed a memory budget (pathological mega-strips) fall back to
 * a [WebtoonChunkedImageView]; only border-cropped pages (or the explicit "always decode long
 * strips with SSIV" setting) use a [SubsamplingScaleImageView] region-decoded from the page's
 * on-device cache file. Animated images (gif / animated webp) fall back to a plain [ImageView] fed
 * by Coil. Border cropping on the fast path goes through the custom Coil decoder (see
 * TachiyomiReaderDecoder).
 */
open class ReaderPageImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttrs: Int = 0,
    @StyleRes defStyleRes: Int = 0,
    private val isWebtoon: Boolean = false,
) : FrameLayout(context, attrs, defStyleAttrs, defStyleRes) {

    private var pageView: View? = null

    private var config: Config? = null

    private var scope: CoroutineScope? = null
    private var smartFitJob: Job? = null

    /** Color filter (grayscale / inverted colors / enhance) applied to the whole page at draw time. */
    var colorFilter: ColorFilter? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    // Everything inside this view (plain ImageView paths AND SubsamplingScaleImageView pages) is
    // drawn into a layer and composited through this paint, so the filter applies even to pages
    // that region-decode via SubsamplingScaleImageView — which cannot hold a ColorFilter itself.
    private val filterPaint = android.graphics.Paint().apply { isFilterBitmap = true }

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        val cf = colorFilter
        if (cf == null) {
            super.dispatchDraw(canvas)
            return
        }
        filterPaint.colorFilter = cf
        val layer = canvas.saveLayer(null, filterPaint)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(layer)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope?.let {
            if (it.isActive) it.cancel()
        }
        scope = null
        smartFitJob?.cancel()
        smartFitJob = null
    }

    var onImageLoaded: (() -> Unit)? = null
    var onImageLoadError: (() -> Unit)? = null
    var onScaleChanged: ((newScale: Float) -> Unit)? = null
    var onViewClicked: (() -> Unit)? = null

    /** Automatic background: set as this view's background once the image loads. */
    var pageBackground: Drawable? = null

    /** Target decode width (px) for short webtoon pages decoded by Coil (yomi's fast path). */
    var decodeWidthPx: Int = 0

    /** Whole-strip single decodes larger than this (bytes, at the quality-scaled decode width) are
     *  rendered as windowed chunks by [WebtoonChunkedImageView] instead, so a pathological
     *  mega-strip can't OOM the reader (a few live pages at this size is already ~150MB of heap).
     */
    private val TALL_SINGLE_DECODE_BYTES: Long = 40L * 1024 * 1024

    @CallSuper
    open fun onImageLoaded() {
        onImageLoaded?.invoke()
        background = pageBackground
        if (config?.fadeIn == true) {
            pageView?.let { v ->
                v.alpha = 0f
                v.animate().alpha(1f).setDuration(200).start()
            }
        }
    }

    @CallSuper
    open fun onImageLoadError() {
        onImageLoadError?.invoke()
    }

    @CallSuper
    open fun onScaleChanged(newScale: Float) {
        onScaleChanged?.invoke(newScale)
    }

    @CallSuper
    open fun onViewClicked() {
        onViewClicked?.invoke()
    }

    fun setImage(file: File, isAnimated: Boolean, config: Config) {
        ReaderDiagnostics.init(context)
        this.config = config
        smartFitJob?.cancel()
        smartFitJob = null
        val dims = runCatching {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, o)
            "${o.outWidth}x${o.outHeight}"
        }.getOrDefault("?")
        val decodeW = if (decodeWidthPx > 0) decodeWidthPx else context.resources.displayMetrics.widthPixels
        if (isAnimated) {
            ReaderDiagnostics.log("path=ANIMATED dims=$dims")
            prepareAnimatedImageView()
            setAnimatedImage(file, config)
        } else {
            val isTall = config.isTallImage ?: isTallImageFile(file)
            if (isWebtoon && !isTall && !config.alwaysDecodeLongStripWithSSIV) {
                ReaderDiagnostics.log("path=SHORT_WHOLE dims=$dims tall=false decodeW=$decodeW rgb565=${config.decodeRgb565}")
                prepareShortImageView()
                setShortImage(file, config)
            } else if (isWebtoon && isTall && !config.cropBorders && !config.alwaysDecodeLongStripWithSSIV) {
                if (fitsSingleDecode(file)) {
                    // Whole-strip single decode (software, memory-cached by Coil): one stable
                    // bitmap drawn per frame — no per-frame tile/chunk decode, the smooth path.
                    ReaderDiagnostics.log("path=TALL_WHOLE dims=$dims tall=true decodeW=$decodeW rgb565=${config.decodeRgb565}")
                    prepareShortImageView()
                    setTallImage(file, config)
                } else {
                    // Pathological mega-strip: a single bitmap would blow the memory budget, so
                    // render it as a windowed stack of chunks instead.
                    ReaderDiagnostics.log("path=TALL_CHUNKED dims=$dims tall=true decodeW=$decodeW (exceeds 40MB single-decode budget)")
                    prepareChunkedImageView()
                    setChunkedImage(file, config)
                }
            } else {
                ReaderDiagnostics.log("path=SSIV dims=$dims tall=$isTall cropBorders=${config.cropBorders} alwaysSSIV=${config.alwaysDecodeLongStripWithSSIV}")
                prepareNonAnimatedImageView()
                setNonAnimatedImage(file, config)
            }
        }
    }

    fun recycle() {
        smartFitJob?.cancel()
        smartFitJob = null
        pageView?.let {
            when (it) {
                is SubsamplingScaleImageView -> it.recycle()
                is WebtoonChunkedImageView -> it.recycle()
                is ImageView -> it.dispose()
            }
            it.isVisible = false
        }
    }

    private fun prepareNonAnimatedImageView() {
        if (pageView is SubsamplingScaleImageView) return
        removeView(pageView)

        pageView = if (isWebtoon) {
            WebtoonSubsamplingImageView(context)
        } else {
            SubsamplingScaleImageView(context)
        }.apply {
            setMaxTileSize(4096)
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumTileDpi(180)
            if (isWebtoon) {
                setEagerLoadingEnabled(false)
            }
            setOnStateChangedListener(
                object : SubsamplingScaleImageView.OnStateChangedListener {
                    override fun onScaleChanged(newScale: Float, origin: Int) {
                        this@ReaderPageImageView.onScaleChanged(newScale)
                    }

                    override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                        // Not used
                    }
                },
            )
            setOnClickListener { this@ReaderPageImageView.onViewClicked() }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun setNonAnimatedImage(
        file: File,
        config: Config,
    ) = (pageView as? SubsamplingScaleImageView)?.apply {
        val t0 = SystemClock.elapsedRealtime()
        setZoomEnabled(config.enablePinchToZoom)
        setDoubleTapZoomDuration(config.zoomDuration.coerceAtLeast(1))
        setMinimumScaleType(config.minimumScaleType)
        setMinimumDpi(1)
        setCropBorders(config.cropBorders)
        setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    ReaderDiagnostics.log("SSIV ready ${SystemClock.elapsedRealtime() - t0}ms (tile-decode from file)")
                    this@ReaderPageImageView.onImageLoaded()
                    setupZoom(config)
                }

                override fun onImageLoadError(e: Exception) {
                    ReaderDiagnostics.log("SSIV ERROR after ${SystemClock.elapsedRealtime() - t0}ms: ${e.message}")
                    this@ReaderPageImageView.onImageLoadError()
                }
            },
        )

        setHardwareConfig(config.canUseHardwareBitmap ?: (android.os.Build.VERSION.SDK_INT >= 26))

        if (config.webtoonSmartFit && scope != null) {
            smartFitJob = scope?.launch {
                val bounds = withContext(Dispatchers.IO) {
                    runCatching {
                        WebtoonBorderDetector.detectContentBounds(FileInputStream(file))
                    }.getOrNull()
                }
                if (bounds != null) {
                    setImage(ImageSource.provider { FileInputStream(file) }.region(bounds))
                } else {
                    setImage(ImageSource.provider { FileInputStream(file) })
                }
                isVisible = true
            }
        } else {
            setImage(ImageSource.provider { FileInputStream(file) })
            isVisible = true
        }
    }

    /** True if [file] is a tall webtoon strip (height > 3x width â yomi/mihon's rule). */
    private fun isTallImageFile(file: File): Boolean {
        return try {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
            WebtoonPageCache.isTallPage(opts.outWidth, opts.outHeight)
        } catch (e: Throwable) {
            true
        }
    }

    private fun prepareChunkedImageView() {
        if (pageView is WebtoonChunkedImageView) return
        removeView(pageView)
        pageView = WebtoonChunkedImageView(context)
        addView(pageView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun setChunkedImage(
        file: File,
        config: Config,
    ) = (pageView as? WebtoonChunkedImageView)?.apply {
        onReady = { this@ReaderPageImageView.onImageLoaded() }
        onError = { this@ReaderPageImageView.onImageLoadError() }
        val decodeW = if (decodeWidthPx > 0) decodeWidthPx else context.resources.displayMetrics.widthPixels
        setChunkedImage(file, decodeW, config.decodeRgb565)
        // The holder's recycle() hides the page view when the view scrolls off; a rebound (or
        // fresh) chunked view must be visible again or the page stays blank after re-entering.
        isVisible = true
    }

    /** Applies the zoom configuration once the image is ready (chimahon's setupZoom). */
    private fun setupZoom(config: Config) {
        val imageView = pageView as? SubsamplingScaleImageView ?: return
        val scale = imageView.scale
        imageView.maxScale = scale * 5
        if (config.disableZoomIn) {
            imageView.isZoomEnabled = false
        } else {
            imageView.setDoubleTapZoomScale(if (config.doubleTapZoom) scale * 2 else scale)
        }
        when (config.zoomStartPosition) {
            Config.ZoomStartPosition.LEFT ->
                imageView.setScaleAndCenter(scale, PointF(0f, 0f))
            Config.ZoomStartPosition.RIGHT ->
                imageView.setScaleAndCenter(scale, PointF(imageView.sWidth.toFloat(), 0f))
            Config.ZoomStartPosition.CENTER ->
                imageView.setScaleAndCenter(scale, PointF(imageView.sWidth / 2f, imageView.sHeight / 2f))
        }
    }

    private fun prepareShortImageView() {
        if (pageView is ImageView && pageView !is SubsamplingScaleImageView) return
        removeView(pageView)
        pageView = ImageView(context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0)
        }
        addView(pageView, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun setShortImage(
        file: File,
        config: Config,
    ) = (pageView as? ImageView)?.apply {
        val decodeW = if (decodeWidthPx > 0) decodeWidthPx else context.resources.displayMetrics.widthPixels
        val t0 = SystemClock.elapsedRealtime()
        val request = ImageRequest.Builder(context)
            .data(file)
            .size(Size(decodeW, Dimension.Undefined))
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            // GPU-backed hardware bitmaps: drawing a software bitmap forces the RenderThread to
            // upload its pixels to a texture on first draw — with several full-width pages hitting
            // the screen during a fling that upload churn is the scroll jank. Hardware bitmaps are
            // created in GPU memory, draw for free, and Coil 2 DOES store them in the memory cache
            // (so the prewarm still hits). Disabled only when border-cropping: the crop decoder
            // reads pixels back, which hardware bitmaps can't do (that decoder returns its own
            // software bitmap anyway, so the flag is irrelevant to the crop path).
            .allowHardware(!config.cropBorders)
            .cropBorders(config.cropBorders)
            .target(
                onSuccess = { drawable ->
                    setImageDrawable(drawable)
                    isVisible = true
                    ReaderDiagnostics.log(
                        "decoded ${SystemClock.elapsedRealtime() - t0}ms " +
                            "bitmap=${drawable.intrinsicWidth}x${drawable.intrinsicHeight} " +
                            "config=${(drawable as? BitmapDrawable)?.bitmap?.config?.name ?: "?"}",
                    )
                    this@ReaderPageImageView.onImageLoaded()
                },
                onError = {
                    ReaderDiagnostics.log("decode ERROR after ${SystemClock.elapsedRealtime() - t0}ms")
                    this@ReaderPageImageView.onImageLoadError()
                },
            )
            .build()
        context.imageLoader.enqueue(request)
    }

    /** Whole-strip smooth path for TALL webtoon pages: decode the ENTIRE strip once at the display
     *  width into a single bitmap shown in the plain [ImageView]. The bitmap is deliberately
     *  SOFTWARE — never hardware — because a tall strip can exceed the GPU's texture-size limit,
     *  which makes Coil's hardware decode fail entirely; software bitmaps are tiled internally by
     *  the render thread and, being stable for the page's lifetime, are drawn without any
     *  per-frame decode or upload churn (the source of the old SSIV/chunked scroll jank).
     */
    private fun setTallImage(
        file: File,
        config: Config,
    ) = (pageView as? ImageView)?.apply {
        val decodeW = if (decodeWidthPx > 0) decodeWidthPx else context.resources.displayMetrics.widthPixels
        val t0 = SystemClock.elapsedRealtime()
        val request = ImageRequest.Builder(context)
            .data(file)
            .size(Size(decodeW, Dimension.Undefined))
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .allowHardware(false)
            .allowRgb565(config.decodeRgb565)
            .target(
                onSuccess = { drawable ->
                    setImageDrawable(drawable)
                    isVisible = true
                    ReaderDiagnostics.log(
                        "decoded ${SystemClock.elapsedRealtime() - t0}ms " +
                            "bitmap=${drawable.intrinsicWidth}x${drawable.intrinsicHeight} " +
                            "config=${(drawable as? BitmapDrawable)?.bitmap?.config?.name ?: "?"} " +
                            "tallWhole=true",
                    )
                    this@ReaderPageImageView.onImageLoaded()
                },
                onError = {
                    ReaderDiagnostics.log("decode ERROR after ${SystemClock.elapsedRealtime() - t0}ms (whole-strip)")
                    this@ReaderPageImageView.onImageLoadError()
                },
            )
            .build()
        context.imageLoader.enqueue(request)
    }

    /** True if [file]'s whole-strip decode at the display width fits within the memory budget (the
     *  smooth single-decode path). Strips whose single decode would exceed the budget are rendered
     *  as windowed chunks instead, so a pathological mega-strip can't OOM the reader.
     */
    private fun fitsSingleDecode(file: File): Boolean {
        val cfg = config ?: return true
        val decodeW = if (decodeWidthPx > 0) decodeWidthPx else context.resources.displayMetrics.widthPixels
        return try {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
            val w = opts.outWidth
            val h = opts.outHeight
            if (w <= 0 || h <= 0) return false
            val bpp = if (cfg.decodeRgb565) 2 else 4
            val decodedW = minOf(w, decodeW)
            val decodedH = decodedW.toLong() * h / w
            decodedW.toLong() * decodedH * bpp <= TALL_SINGLE_DECODE_BYTES
        } catch (e: Throwable) {
            false
        }
    }

    private fun prepareAnimatedImageView() {
        if (pageView is ImageView && pageView !is SubsamplingScaleImageView) return
        removeView(pageView)

        pageView = ImageView(context).apply {
            adjustViewBounds = true
            setBackgroundColor(0)
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun setAnimatedImage(
        file: File,
        config: Config,
    ) = (pageView as? ImageView)?.apply {
        val request = ImageRequest.Builder(context)
            .data(file)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .target(
                onSuccess = { drawable ->
                    setImageDrawable(drawable)
                    (drawable as? Animatable)?.start()
                    isVisible = true
                    this@ReaderPageImageView.onImageLoaded()
                },
                onError = {
                    this@ReaderPageImageView.onImageLoadError()
                },
            )
            .build()
        context.imageLoader.enqueue(request)
    }

    fun getImageView(): View? = pageView

    /** Configuration for a single page render. */
    data class Config(
        val zoomDuration: Int = 200,
        val minimumScaleType: Int = SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE,
        val cropBorders: Boolean = false,
        val webtoonSmartFit: Boolean = false,
        val enablePinchToZoom: Boolean = true,
        val isTallImage: Boolean? = null,
        val canUseHardwareBitmap: Boolean? = null,
        val decodeRgb565: Boolean = false,
        val alwaysDecodeLongStripWithSSIV: Boolean = false,
        val doubleTapZoom: Boolean = true,
        val disableZoomIn: Boolean = false,
        val zoomStartPosition: ZoomStartPosition = ZoomStartPosition.CENTER,
        val fadeIn: Boolean = false,
    ) {
        enum class ZoomStartPosition { LEFT, CENTER, RIGHT }
    }

    /** True if [file] looks like an animated GIF or animated WebP. */
    fun isAnimatedFile(file: File): Boolean {
        return try {
            val bytes = ByteArray(32)
            val raf = java.io.RandomAccessFile(file, "r")
            try {
                val n = raf.read(bytes)
                if (n < 12) return false
                val head = String(bytes, 0, n.coerceAtMost(12), Charsets.US_ASCII)
                if (head.startsWith("GIF8")) return true
                if (head.startsWith("RIFF") && n >= 12 && head.substring(8, 12) == "WEBP") {
                    // VP8X chunk carries an animation flag (0x02) at its flags byte.
                    if (n >= 24 && head.substring(12, 16) == "VP8X") {
                        return (bytes[20].toInt() and 0x02) != 0
                    }
                }
                false
            } finally {
                raf.close()
            }
        } catch (e: Exception) {
            false
        }
    }
}
