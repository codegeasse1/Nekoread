package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.ChapterEntity
import com.example.data.source.MangaSource
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonItem
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonTrailer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

/**
 * Hosts the yomi webtoon viewer (a RecyclerView-based reader) inside Compose. Pages are streamed
 * chapter-by-chapter: each streamed segment after the first is preceded by a chapter divider, and
 * a trailing item shows the next-chapter loading/error/end state. All scroll/zoom/tap behavior is
 * handled by the native viewer, exactly as it is in yomi.
 */
@Composable
fun YomiWebtoonReader(
    source: MangaSource?,
    cacheDir: File,
    streamQueue: List<ChapterEntity>,
    streamSegments: List<List<Any>>,
    segSizes: List<Int>,
    bgColor: Color,
    textColor: Color,
    gaps: Boolean,
    cropBorders: Boolean,
    doubleTapZoom: Boolean,
    tapToChangePages: Boolean,
    autoScroll: Boolean,
    autoScrollSpeedDp: Float,
    initialPageIndex: Int,
    trailer: WebtoonTrailer,
    viewerRef: MutableState<WebtoonViewer?>,
    onPageChanged: (seg: Int, page: Int, pageTotal: Int) -> Unit,
    onNearEndChanged: (Boolean) -> Unit,
    onMenuTap: () -> Unit,
    onUserScroll: () -> Unit,
    onTrailerRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    val items = remember(streamSegments) {
        buildList {
            streamSegments.forEachIndexed { segIdx, seg ->
                if (segIdx > 0) {
                    val ch = streamQueue.getOrNull(segIdx)
                    if (ch != null) add(WebtoonItem.Divider(ch.id, ch.name))
                }
                seg.forEachIndexed { pi, m ->
                    if (m is MangaSource.PageDescriptor) {
                        add(WebtoonItem.Page(segIdx, pi + 1, m, m.imageUrl))
                    }
                }
            }
        }
    }

    // Before the first chapter's pages are loaded there is nothing to show yet.
    if (items.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = textColor)
        }
        return
    }

    // The item list only changes when a chapter is streamed in; use a cheap signature so the
    // update block doesn't re-run a DiffUtil (and re-scroll) on every recomposition while reading.
    val itemsSignature = remember(streamQueue, streamSegments) {
        streamQueue.joinToString("|") { it.id } + "::" + streamSegments.joinToString("|") { it.size.toString() }
    }
    var lastSignature by remember { mutableStateOf("") }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val v = WebtoonViewer(ctx)
            viewerRef.value = v
            v.source = source
            v.cacheDir = cacheDir
            v.gaps = gaps
            v.cropBorders = cropBorders
            v.doubleTapZoom = doubleTapZoom
            v.tapToChangePages = tapToChangePages
            v.onPageChanged = { seg, page, total -> onPageChanged(seg, page, total) }
            v.onNearEndChanged = { near -> onNearEndChanged(near) }
            v.onMenuTap = { onMenuTap() }
            v.onUserScroll = { onUserScroll() }
            v.onTrailerRetry = { onTrailerRetry() }
            v.setTheme(bgColor.toArgb(), textColor.toArgb())
            v.setItems(items, segSizes, trailer, initialPageIndex)
            v.view
        },
        update = { _ ->
            val v = viewerRef.value ?: return@AndroidView
            v.source = source ?: v.source
            v.cacheDir = cacheDir
            v.gaps = gaps
            v.cropBorders = cropBorders
            v.doubleTapZoom = doubleTapZoom
            v.tapToChangePages = tapToChangePages
            v.setTheme(bgColor.toArgb(), textColor.toArgb())
            if (lastSignature != itemsSignature) {
                lastSignature = itemsSignature
                v.setItems(items, segSizes, trailer, initialPageIndex)
            } else if (v.trailer != trailer) {
                v.setTrailer(trailer)
            }
        },
    )

    // Yomi-style auto-scroll: drives the native recycler directly; any user touch stops it (the
    // viewer reports drags and taps through onUserScroll).
    LaunchedEffect(viewerRef.value, autoScroll, autoScrollSpeedDp) {
        val v = viewerRef.value ?: return@LaunchedEffect
        if (!autoScroll) return@LaunchedEffect
        val pxPerMs = with(density) { autoScrollSpeedDp.dp.toPx() } / 1000f
        while (isActive) {
            v.scrollBy((pxPerMs * 16f).toInt().coerceAtLeast(1))
            delay(16)
        }
    }
}
