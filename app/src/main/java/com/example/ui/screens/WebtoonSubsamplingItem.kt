package com.example.ui.screens

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.example.data.reader.WebtoonPageCache
import com.example.data.source.MangaSource
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * One webtoon strip, rendered with a [SubsamplingScaleImageView] — the yomi/Tadami technique that
 * makes long-strip scrolling smooth: the view never holds the strip's full-height bitmap in
 * memory; it decodes only the currently visible region (at the display scale) from the downloaded
 * cache file, and defers high-res tile decoding until the scroll settles, so it never chokes on
 * tall 4K/5K manhwa pages.
 *
 * The item downloads its page through the source's own client (single-flighted via
 * [WebtoonPageCache]) and reports the strip's true aspect ratio once known, so the LazyColumn slot
 * is sized to the page up front (no relayout-pop while scrolling).
 */
@Composable
fun WebtoonSubsamplingItem(
    desc: MangaSource.PageDescriptor,
    source: MangaSource,
    cacheDir: File,
    modifier: Modifier = Modifier,
    spinnerColor: Color = Color.White,
    onRatioKnown: (Float) -> Unit = {},
) {
    var file by remember(desc) { mutableStateOf<File?>(null) }
    var failed by remember(desc) { mutableStateOf(false) }
    var attempt by remember(desc) { mutableIntStateOf(0) }

    // Auto-retry with a pause (like the old Coil page): transient network blips and Cloudflare
    // challenges usually clear. Bounded so a permanently-failing page stops hammering the network.
    LaunchedEffect(desc, attempt) {
        failed = false
        var tries = 0
        while (isActive) {
            try {
                val f = WebtoonPageCache.fileFor(desc, source, cacheDir)
                file = f
                WebtoonPageCache.dimensions(desc, cacheDir)?.let { (w, h) ->
                    if (w > 0 && h > 0) onRatioKnown(w.toFloat() / h)
                }
                return@LaunchedEffect
            } catch (e: Throwable) {
                failed = true
                tries++
                if (tries >= 8) return@LaunchedEffect
                delay(3000)
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                NekoWebtoonImageView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
                    setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
                    setMinimumTileDpi(180)
                    // Never downsample for the initial load — even short/small pages fit at full
                    // sharpness (same as yomi's reader).
                    setMinimumDpi(1)
                    setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
                    setZoomEnabled(false)
                    // Defer high-res tile decoding until layout/flings settle — decoding tiles
                    // mid-scroll is a major dropped-frame source on high-refresh displays.
                    setEagerLoadingEnabled(false)
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.recycle() },
            update = { view ->
                val f = file
                if (f != null) {
                    val current = view.tag as? String
                    if (current != f.absolutePath) {
                        view.tag = f.absolutePath
                        view.recycle()
                        // This fork's ImageSource has no file() factory — a provider that opens a
                        // fresh stream per call is the equivalent (the decoder reads it once and
                        // keeps the bytes natively, then region-decodes from memory).
                        view.setImage(ImageSource.provider { FileInputStream(f) })
                    }
                }
            },
        )
        when {
            file == null && !failed -> {
                CircularProgressIndicator(color = spinnerColor.copy(alpha = 0.6f))
            }
            failed && file == null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Couldn't load page",
                        color = spinnerColor.copy(alpha = 0.7f),
                    )
                    TextButton(onClick = { attempt++ }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

/** Subsampling view that ignores touch events — the reader's LazyColumn handles all gestures. */
private class NekoWebtoonImageView(context: Context) : SubsamplingScaleImageView(context) {
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
