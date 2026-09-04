package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
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
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonBorderDetector
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
 * The webtoon page view ported verbatim from yomi's reader: every non-animated page is drawn by a
 * [SubsamplingScaleImageView] that region-decodes only the visible slice from the page's on-device
 * cache file (never a giant full-height bitmap in memory — that is what keeps long-strip scrolling
 * smooth on slow sources like comix). Animated images (gif / animated webp) fall back to a plain
 * [ImageView] fed by Coil. Touches are ignored on the image itself — the reader's scroll container
 * (or the whole-strip zoom handled by the viewer frame) owns all gestures, exactly like yomi.
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

    @CallSuper
    open fun onImageLoaded() {
        onImageLoaded?.invoke()
        background = pageBackground
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
        this.config = config
        smartFitJob?.cancel()
        smartFitJob = null
        if (isAnimated) {
            prepareAnimatedImageView()
            setAnimatedImage(file, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(file, config)
        }
    }

    fun recycle() {
        smartFitJob?.cancel()
        smartFitJob = null
        pageView?.let {
            when (it) {
                is SubsamplingScaleImageView -> it.recycle()
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
                // Defer high-res tile decoding until gestures/flings settle. This reduces decode
                // churn while scrolling through long strips, which is a major source of dropped
                // frames on high refresh rate displays (90/120Hz+).
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
        setZoomEnabled(config.enablePinchToZoom)
        setDoubleTapZoomDuration(config.zoomDuration.coerceAtLeast(1))
        setMinimumScaleType(config.minimumScaleType)
        setMinimumDpi(1) // Just so that very small images fit for initial load
        setCropBorders(config.cropBorders)
        setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    this@ReaderPageImageView.onImageLoaded()
                }

                override fun onImageLoadError(e: Exception) {
                    this@ReaderPageImageView.onImageLoadError()
                }
            },
        )

        val canUseHardware = config.canUseHardwareBitmap ?: (android.os.Build.VERSION.SDK_INT >= 26)
        setHardwareConfig(canUseHardware)

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

    /** Configuration for a single page render. Only [minimumScaleType]/[cropBorders]/
     *  [webtoonSmartFit] are used for the non-animated (SSIV) path; the rest mirror yomi's config. */
    data class Config(
        val zoomDuration: Int = 200,
        val minimumScaleType: Int = SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE,
        val cropBorders: Boolean = false,
        val webtoonSmartFit: Boolean = false,
        val enablePinchToZoom: Boolean = true,
        val isTallImage: Boolean? = null,
        val canUseHardwareBitmap: Boolean? = null,
    )

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
