package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import coil.compose.AsyncImagePainter
import coil.compose.LocalImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
import com.example.data.local.ChapterEntity
import com.example.data.local.MangaEntity
import com.example.data.reader.WebtoonPageCache
import com.example.data.source.MangaSource
import com.example.ui.MainViewModel
import com.example.ui.ReaderBg
import com.example.ui.ReaderFit
import com.example.ui.ReaderMode
import com.example.ui.ReaderOrientation
import com.example.ui.looksLikeCloudflare
import com.example.util.sortChapters
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonTrailer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// How many in-window webtoon pages the prewarm fetches/decodes at once. A small concurrent batch
// keeps the ±8 page window filled ahead of the scroll even when every page costs a full download +
// descramble (e.g. comix) — a sequential one-at-a-time loop can't keep up on slow sources.
private const val WEBTOON_BATCH = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: MainViewModel,
    manga: MangaEntity?,
    chapter: ChapterEntity?,
    allChapters: List<ChapterEntity>,
    onBackClick: () -> Unit,
    onChapterChange: (String) -> Unit,
    startAtBeginning: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (manga == null || chapter == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Chapter not found.")
        }
        return
    }

    var showHud by remember { mutableStateOf(true) }

    val globalReaderMode: ReaderMode by viewModel.readerMode.collectAsStateWithLifecycle()
    val seriesOverrideEnabled by viewModel.seriesOverrideEnabled.collectAsStateWithLifecycle()
    val seriesReaderMode by viewModel.seriesReaderMode.collectAsStateWithLifecycle()
    // Effective reading mode: the per-series override when the user enabled one for this manga,
    // otherwise the global reader mode (yomi-style "for this series" scope).
    val readerMode: ReaderMode = remember(manga.id, globalReaderMode, seriesOverrideEnabled, seriesReaderMode) {
        if (seriesOverrideEnabled[manga.id] == true) {
            seriesReaderMode[manga.id] ?: globalReaderMode
        } else {
            globalReaderMode
        }
    }
    val readerBg: ReaderBg by viewModel.readerBg.collectAsStateWithLifecycle()
    val readerFit: ReaderFit by viewModel.readerFit.collectAsStateWithLifecycle()
    val readerOrientation: ReaderOrientation by viewModel.readerOrientation.collectAsStateWithLifecycle()
    val keepScreenOn: Boolean by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val showPageNumber: Boolean by viewModel.showPageNumber.collectAsStateWithLifecycle()
    val webtoonFade: Boolean by viewModel.webtoonFade.collectAsStateWithLifecycle()
    val autoScroll: Boolean by viewModel.autoScroll.collectAsStateWithLifecycle()
    val autoScrollSpeedDp: Float by viewModel.autoScrollSpeedDp.collectAsStateWithLifecycle()
    val readerQuality: Int by viewModel.readerQuality.collectAsStateWithLifecycle()
    val cropBorders: Boolean by viewModel.cropBorders.collectAsStateWithLifecycle()
    val doubleTapZoom: Boolean by viewModel.doubleTapZoom.collectAsStateWithLifecycle()
    val tapToChangePages: Boolean by viewModel.tapToChangePages.collectAsStateWithLifecycle()

    // Both long-strip modes render as one continuous vertical list; only the gap differs.
    val isWebtoon = readerMode == ReaderMode.WEBTOON || readerMode == ReaderMode.WEBTOON_GAPS

    var pages by remember { mutableStateOf<List<Any>?>(null) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var pageLoading by remember { mutableStateOf(true) }
    var retryKey by remember { mutableStateOf(0) }
    var pendingCfVerify by remember(chapter.id) { mutableStateOf(false) }
    var slowChapterWarning by remember(chapter.id) { mutableStateOf(false) }

    // Continuous-reading stream (webtoon modes): chapters in reading order + their loaded pages.
    var streamQueue by remember(chapter.id) { mutableStateOf(listOf(chapter)) }
    var streamSegments by remember(chapter.id) { mutableStateOf<List<List<Any>>>(emptyList()) }
    // Coil page-image models for webtoon pages are gone — webtoon pages now render from on-device
    // cache files (WebtoonPageCache), so no separate source-aware model fetch is needed (it
    // doubled the chapter-open cost: getPageList ran twice per chapter).
    var webtoonLoadingNext by remember(chapter.id) { mutableStateOf(false) }
    var webtoonError by remember(chapter.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(chapter.id, retryKey, isWebtoon) {
        pageLoading = true
        pageError = null
        try {
            if (isWebtoon) {
                // Webtoon modes fetch page DESCRIPTORS (URLs). Page bytes are downloaded once per
                // page to an on-device cache file through the source's own client (see the prewarm
                // loop below), so there's no need for a separate source-aware Coil-model fetch here
                // — that used to double the chapter-open cost on slow sources (getPageList ran
                // twice per chapter).
                pages = viewModel.repository.getChapterPageDescriptors(chapter.id)
            } else {
                pages = viewModel.repository.getChapterPageImageModels(chapter.id)
            }
        } catch (e: Throwable) {
            pageError = e.message ?: "Failed to load chapter pages"
            pages = null
            if (looksLikeCloudflare(e)) {
                pendingCfVerify = true
            }
        } finally {
            pageLoading = false
        }
    }

    // Seed the webtoon stream with the current chapter's pages once loaded.
    LaunchedEffect(pages, chapter.id, isWebtoon) {
        if (isWebtoon && pages != null && (streamSegments.isEmpty() || streamSegments.firstOrNull() != pages)) {
            streamSegments = listOf(pages!!)
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // Determine current background color
    val bgColor = when (readerBg) {
        ReaderBg.PURE_BLACK -> Color.Black
        ReaderBg.DARK_GRAY -> Color(0xFF181A24)
        ReaderBg.CREAM -> Color(0xFFFBF0D9)
        ReaderBg.WHITE -> Color.White
    }

    val contentTextColor = if (readerBg == ReaderBg.CREAM || readerBg == ReaderBg.WHITE) Color.Black else Color.White

    // Initial position: in-reader next/prev chapter navigation starts at the first page
    // (startAtBeginning), while opening a chapter from the library/detail resumes where the
    // user left off (lastPageRead).
    val initialPageIndex = if (startAtBeginning) {
        0
    } else {
        (chapter.lastPageRead - 1).coerceAtLeast(0)
    }

    // Webtoon reader state (yomi-style RecyclerView viewer): the current (segment, page, pageTotal)
    // reported by the viewer, whether the user is near the end of the stream (drives the
    // auto-continue load of the next chapter), and a handle on the viewer itself (used by the page
    // slider to jump to a page). The viewer replaces the Compose LazyColumn for webtoon modes.
    var viewerPos by remember(chapter.id) { mutableStateOf(Triple(0, 1, pages?.size ?: 1)) }
    var viewerNearEnd by remember(chapter.id) { mutableStateOf(false) }
    val viewerRef = remember(chapter.id) { mutableStateOf<WebtoonViewer?>(null) }

    // Paged Reader State (shared by horizontal + vertical pagers)
    val pagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { pages?.size ?: 0 }
    )

    // Force the reader to open on the intended starting page once a chapter's content is on
    // screen. The list/pager are created with the right initial index, but in-reader prev/next
    // navigation between chapters can otherwise carry a previous chapter's scroll position over —
    // which is why a next-chapter tap used to land on a random previously-read page instead of
    // page 1. Keying on the chapter id makes the start page reliable on every navigation path.
    LaunchedEffect(chapter.id, pages, isWebtoon, startAtBeginning) {
        val list = pages ?: return@LaunchedEffect
        if (list.isEmpty()) return@LaunchedEffect
        val target = if (startAtBeginning) 0 else (chapter.lastPageRead - 1).coerceAtLeast(0)
        try {
            if (isWebtoon) {
                // The yomi viewer positions itself on first layout (setItems with initialPageIndex);
                // seed the page state so the HUD reads correctly until the viewer reports in.
                viewerPos = Triple(0, (target + 1).coerceIn(1, list.size), list.size)
            } else {
                pagerState.scrollToPage(target.coerceAtMost(list.lastIndex))
            }
        } catch (_: Exception) {
        }
    }

    // The chapter currently on screen (in webtoon modes this can advance past the starting chapter).
    // In webtoon mode the position comes from the yomi viewer's page-change reports.
    val streamPosition by remember {
        derivedStateOf {
            if (isWebtoon) viewerPos else Triple(0, 1, pages?.size ?: 1)
        }
    }

    val activeChapter by remember {
        derivedStateOf {
            if (isWebtoon) streamQueue.getOrNull(streamPosition.first) ?: chapter else chapter
        }
    }

    val currentPage by remember {
        derivedStateOf {
            if (isWebtoon) {
                streamPosition.second
            } else {
                val total = pages?.size ?: 0
                if (total == 0) 0 else (pagerState.currentPage + 1).coerceAtMost(total)
            }
        }
    }

    val pageTotal by remember {
        derivedStateOf {
            if (isWebtoon) streamPosition.third else (pages?.size ?: 0)
        }
    }

    // Save reading progress on page / active-chapter change
    LaunchedEffect(currentPage, activeChapter.id) {
        if (currentPage > 0) {
            viewModel.saveProgress(manga.id, activeChapter.id, activeChapter.name, currentPage)
        }
    }

    val sortedChapters = remember(allChapters) { sortChapters(allChapters) }

    // Previous/next chapters are found by POSITION in the chapter list rather than by comparing
    // chapter numbers: sources that don't number their chapters store -1 for every chapter, which
    // made both buttons permanently disabled and silently killed the webtoon auto-continue
    // ("End of Chapter / you caught up" even when dozens of chapters were still ahead).
    val activeIdx = remember(sortedChapters, activeChapter) {
        sortedChapters.indexOfFirst { it.id == activeChapter.id }
    }
    val prevChapter = remember(sortedChapters, activeIdx) {
        if (activeIdx > 0) sortedChapters[activeIdx - 1] else null
    }
    val nextChapter = remember(sortedChapters, activeIdx) {
        if (activeIdx in 0 until sortedChapters.lastIndex) sortedChapters[activeIdx + 1] else null
    }

    // The next chapter after the LAST one already streamed (used for auto-continue).
    val streamNextChapter = remember(sortedChapters, streamQueue) {
        val last = streamQueue.lastOrNull() ?: return@remember null
        val lastIdx = sortedChapters.indexOfFirst { it.id == last.id }
        if (lastIdx in 0 until sortedChapters.lastIndex) sortedChapters[lastIdx + 1] else null
    }

    fun loadNextIntoStream(next: ChapterEntity) {
        coroutineScope.launch {
            webtoonLoadingNext = true
            webtoonError = null
            try {
                if (isWebtoon) {
                    val descs = viewModel.repository.getChapterPageDescriptors(next.id)
                    streamQueue = streamQueue + next
                    streamSegments = streamSegments + listOf(descs)
                } else {
                    val p = viewModel.repository.getChapterPageImageModels(next.id)
                    streamQueue = streamQueue + next
                    streamSegments = streamSegments + listOf(p)
                }
            } catch (e: Throwable) {
                webtoonError = e.message ?: "Failed to load next chapter"
            } finally {
                webtoonLoadingNext = false
            }
        }
    }

    // When the user nears the bottom of the stream, fetch the next chapter and append it. The yomi
    // viewer reports this: true while the last few pages of the last streamed chapter are on screen,
    // false once the user scrolls back up.
    val nearStreamEnd = viewerNearEnd

    LaunchedEffect(nearStreamEnd, streamQueue.size, webtoonLoadingNext, webtoonError, isWebtoon) {
        if (!isWebtoon) return@LaunchedEffect
        if (!nearStreamEnd) return@LaunchedEffect
        if (webtoonLoadingNext) return@LaunchedEffect
        if (webtoonError != null) return@LaunchedEffect
        val next = streamNextChapter ?: return@LaunchedEffect
        if (!streamQueue.any { it.id == next.id }) {
            loadNextIntoStream(next)
        }
    }

    // Quick-load nearby pages: warm Coil's MEMORY cache for the pages around the current one, so
    // scrolling or jumping to a page renders instantly. The image bytes are keyed by their URL,
    // never by position, so there's no risk of serving another chapter's page. Pages are decoded
    // at (at most) the strip's display width, not the full source resolution, so tall strips cost
    // far less memory and scrolling stays smooth.
    val imageLoader = LocalImageLoader.current
    val context = LocalContext.current
    val screenWidthPx = context.resources.displayMetrics.widthPixels
    val screenHeightPx = context.resources.displayMetrics.heightPixels
    val density = LocalDensity.current
    // Webtoon strips are always displayed at fill-width (never zoomed), so decoding them below the
    // full screen width costs a little sharpness but makes decodes and memory much cheaper — the
    // main source of scroll stutter on tall strips. The reader's "Image quality" setting picks the
    // width (50/75/100%); the Low tier also decodes as RGB_565 (half the memory) for heavy
    // full-HD/4K chapters.
    val webtoonDecodeWidth = (screenWidthPx * readerQuality / 100f).roundToInt().coerceAtLeast(360)
    // Low-quality webtoon pages decode as RGB_565: half the memory per strip for nearly identical
    // appearance (webtoon art is flat colors). Paged modes stay full ARGB_8888.
    val useRgb565 = isWebtoon && readerQuality == 50

    // Known page aspect ratios, filled in as nearby pages decode (prewarm below). Webtoon list
    // items use them to size their slot to the page's real height up front, so the list doesn't
    // relayout every strip as it finishes loading — that relayout is what made scrolling feel
    // laggy. Keyed by the page model's string (a URL for MangaDex, ExtensionPageImage for
    // extensions — both unique per page).
    val pageAspectRatios = remember { mutableStateMapOf<String, Float>() }

    val displayDecodeWidth = if (isWebtoon) webtoonDecodeWidth else screenWidthPx
    val prewarmAfter = if (isWebtoon) 8 else 4
    // Pages whose DISPLAY-size decode has finished (so the window doesn't re-decode them). Keyed on
    // the decode width so changing the image-quality setting re-prewarms at the new size.
    val displayPrewarmed = remember(displayDecodeWidth) { mutableStateMapOf<String, Boolean>() }

    // Webtoon pages whose cache file is downloaded (or attempted). The prewarm loop owns this: it
    // fetches every in-window page's bytes ONCE into WebtoonPageCache's on-device cache dir (see the
    // loop below), and the visible items render from those files — so a scroll never re-fetches a
    // page. The visible item has its own retry + button if it wins the race with the prewarm.
    val webtoonDownloaded = remember { mutableStateMapOf<String, Boolean>() }
    // imageUrl -> time of last failed download. The prewarm backs off on these for a few seconds so
    // a transient failure doesn't spin in a hot loop; the visible item has its own retry + button.
    val downloadFailed = remember { mutableStateMapOf<String, Long>() }
    // The chapter's source — used to download webtoon pages through the source's own client
    // (Referer/Origin etc., exactly like yomi's HttpPageLoader), and the on-device cache dir that
    // stores the downloaded page image files for the subsampling view to region-decode.
    val source = remember(manga) {
        if (manga == null) null else runCatching { viewModel.repository.sourceForManga(manga.id) }.getOrNull()
    }
    val webtoonCacheDir = remember { File(context.cacheDir, "webtoon_pages") }

    // Stable per-page cache key: the image URL for descriptor pages (unique per page, shared by the
    // prewarm and the visible item), the model's string otherwise.
    fun pageKey(m: Any): String = if (m is MangaSource.PageDescriptor) m.imageUrl else m.toString()

    // Rolling prewarm — ONE persistent loop, keyed only on chapter/segments/quality, NOT on the
    // current page. (The old two-stage effects restarted on every page scroll, cancelling all
    // their in-flight decodes each time — that cancel/restart churn was a major scroll-stutter
    // source.) The loop walks outward from the current page, nearest page first, keeping 8 pages
    // ahead and 8 pages behind display-ready:
    //   - WEBTOON mode is HYBRID and FILE-BACKED. Every page in the ±8 window is downloaded ONCE
    //     (through the source's own client + Descrambler, single-flighted via WebtoonPageCache)
    //     to an on-device cache file, then its true aspect ratio is read from that file
    //     (bounds-only decode — no network) so slots are pre-sized and the tall/short decision is
    //     made before the page scrolls in. TALL strips (display height > ~2.5 screens) are
    //     rendered by the subsampling view region-decoding straight from the file — never a giant
    //     full-height bitmap, which is what keeps long strips smooth. SHORT pages render with Coil
    //     FROM THE FILE: the display-size decode is warmed into Coil's memory cache so the visible
    //     item pops instantly, and every re-scroll is a local disk read instead of a repeat
    //     network fetch + descramble (the thing that made slow sources like comix stutter). Pages
    //     are processed in a small CONCURRENT batch so the window fills ahead of the scroll.
    //   - PAGED mode: the Coil two-step — RATIO (a 64px decode for far-ahead pages) and DISPLAY
    //     (a display-size decode that warms Coil's memory cache for near pages). The display decode
    //     is the AUTHORITATIVE aspect ratio, so slot and bitmap match to the pixel (no hairline
    //     seams between consecutive pages).
    LaunchedEffect(pages, streamSegments, imageLoader, displayDecodeWidth, useRgb565, source, webtoonCacheDir) {
        if (imageLoader == null) return@LaunchedEffect
        while (isActive) {
            val now = System.currentTimeMillis()
            val segs = streamSegments
            if (segs.isEmpty() || segs.any { it.isEmpty() }) { delay(120); continue }
            val starts = IntArray(segs.size)
            var total = 0
            for (i in segs.indices) { starts[i] = total; total += segs[i].size }
            if (total == 0) { delay(120); continue }
            val segIdx = if (isWebtoon) streamPosition.first.coerceIn(0, segs.lastIndex) else 0
            val segSize = segs[segIdx].size
            val globCur = (starts[segIdx] + (currentPage - 1).coerceIn(0, segSize - 1)).coerceIn(0, total - 1)
            // Preload window: 8 pages behind and 8 pages ahead of the current page are made
            // display-ready (Coil memory-cache warm for short pages, cache-file download for tall
            // strips) so that scrolling — and jumping straight to any page — shows the 8 neighbours
            // instantly instead of decoding them on first scroll-in.
            val warmFrom = (globCur - 8).coerceAtLeast(0)
            val warmTo = (globCur + 8).coerceAtMost(total - 1)

            // Pick the nearest not-yet-processed pages, walking outward from the current page
            // (current, +1, -1, +2, -2, ...). After a scroll or a skip, the pages closest to the
            // viewport are handled first, so the very next image is ready before it scrolls in.
            //   - WEBTOON mode collects a small BATCH of nearest in-window pages and processes them
            //     CONCURRENTLY (downloads/decodes interleave), which keeps the ±8 window filled
            //     ahead of the scroll on slow sources — a sequential one-at-a-time loop can't keep
            //     up when every page costs a full download + descramble (comix).
            //   - PAGED mode picks a single nearest page for the two-step ratio/display warm.
            var batch: List<Triple<Int, Int, MangaSource.PageDescriptor>> = emptyList()
            var candidate: Triple<Int, Int, Any>? = null
            if (isWebtoon) {
                val maxDist = maxOf(globCur - warmFrom, warmTo - globCur)
                val collected = mutableListOf<Triple<Int, Int, MangaSource.PageDescriptor>>()
                walk@ for (d in 0..maxDist) {
                    val gs = if (d == 0) intArrayOf(globCur) else intArrayOf(globCur + d, globCur - d)
                    for (g in gs) {
                        if (collected.size >= WEBTOON_BATCH) break@walk
                        if (g < warmFrom || g > warmTo) continue
                        var seg = 0
                        while (seg < segs.size && g >= starts[seg] + segs[seg].size) seg++
                        if (seg >= segs.size) continue
                        val m = segs[seg][g - starts[seg]]
                        // Only descriptor pages are pre-processed in webtoon mode (anything else
                        // renders via the item's Coil fallback).
                        if (m !is MangaSource.PageDescriptor) continue
                        val key = pageKey(m)
                        // Skip pages that just failed — the visible item retries on its own; this
                        // loop would otherwise hot-spin on the same failure every 120ms.
                        if (downloadFailed.containsKey(key) && now - downloadFailed[key]!! < 8000) continue
                        // The viewer's page holder downloads a page the moment it scrolls into view;
                        // this loop just fills the ±8 page window AHEAD of the scroll so pages are
                        // already on disk (single-flighted via WebtoonPageCache) when the holder
                        // asks — the yomi trick that keeps long strips smooth on slow sources.
                        if (!webtoonDownloaded.containsKey(key)) {
                            collected.add(Triple(seg, g, m))
                        }
                    }
                }
                batch = collected
            } else {
                val from = (globCur - 2).coerceAtLeast(0)
                val to = (globCur + prewarmAfter).coerceAtMost(total - 1)
                for (g in from..to) {
                    var seg = 0
                    while (seg < segs.size && g >= starts[seg] + segs[seg].size) seg++
                    if (seg >= segs.size) continue
                    val m = segs[seg][g - starts[seg]]
                    val needRatio = !pageAspectRatios.containsKey(m.toString())
                    val needDisplay = (g >= globCur - 2) && !displayPrewarmed.containsKey(m.toString())
                    if (needRatio || needDisplay) { candidate = Triple(seg, g, m); break }
                }
            }

            if (isWebtoon) {
                if (batch.isEmpty()) { delay(120); continue }
                // Process the batch CONCURRENTLY: each page's file download runs through the
                // source's own client (single-flighted via WebtoonPageCache), so the preload
                // window fills ~3x faster than one-at-a-time on slow sources. State writes all
                // happen on the main dispatcher (async inherits it; the downloads themselves run
                // on WebtoonPageCache's IO scope), so the maps stay safe.
                coroutineScope {
                    batch.map { (seg, g, m) ->
                        async {
                            try {
                                val key = pageKey(m)
                                if (!webtoonDownloaded.containsKey(key) && source != null) {
                                    WebtoonPageCache.fileFor(m, source, webtoonCacheDir)
                                }
                                webtoonDownloaded[key] = true
                            } catch (e: Throwable) {
                                // Effect restarts (scroll/key change) cancel in-flight work — don't
                                // treat a cancellation as a page failure.
                                if (e is CancellationException) throw e
                                // A failed page shouldn't wedge the loop on the same entry forever.
                                val key = pageKey(m)
                                downloadFailed[key] = System.currentTimeMillis()
                                if (looksLikeCloudflare(e)) pendingCfVerify = true
                            }
                        }
                    }.awaitAll()
                }
                continue
            }

            if (candidate == null) { delay(120); continue }
            val (seg, g, m) = candidate
            // Paged mode: near pages get their display decode warmed (the visible item then pops
            // straight from memory).
            val inDisplay = g >= globCur - 2
            try {
                if (!pageAspectRatios.containsKey(m.toString())) {
                    val ratioReq = ImageRequest.Builder(context)
                        .data(m)
                        .size(Size(64, Dimension.Undefined))
                        .setParameter("reader_retry", 0)
                        .setParameter("reader_role", "ratio")
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                    val rr = imageLoader.execute(ratioReq)
                    val rd = rr.drawable
                    if (rd != null && rd.intrinsicWidth > 0 && rd.intrinsicHeight > 0) {
                        pageAspectRatios[m.toString()] = rd.intrinsicWidth.toFloat() / rd.intrinsicHeight
                    }
                }
                if (inDisplay && !displayPrewarmed.containsKey(m.toString())) {
                    val displayReq = ImageRequest.Builder(context)
                        .data(m)
                        .size(Size(displayDecodeWidth, Dimension.Undefined))
                        .setParameter("reader_retry", 0)
                        .apply { if (useRgb565) allowRgb565(true) }
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                    val dr = imageLoader.execute(displayReq)
                    val dd = dr.drawable
                    if (dd != null && dd.intrinsicWidth > 0 && dd.intrinsicHeight > 0) {
                        pageAspectRatios[m.toString()] = dd.intrinsicWidth.toFloat() / dd.intrinsicHeight
                    }
                    displayPrewarmed[m.toString()] = true
                }
            } catch (e: Throwable) {
                // A failed page shouldn't wedge the loop on the same entry forever.
                displayPrewarmed[m.toString()] = true
            }
        }
    }

    // Source's base URL (for the Cloudflare / site-verification WebView button).
    val sourceBaseUrl = remember(manga) {
        if (manga == null) "" else runCatching {
            viewModel.repository.sourceForManga(manga.id).baseUrl
        }.getOrDefault("")
    }
    val sourceUserAgent = remember(manga) {
        if (manga == null) "" else runCatching {
            viewModel.repository.sourceForManga(manga.id).userAgent
        }.getOrDefault("")
    }

    // Cloudflare / site verification overlay (a Dialog, so closing it keeps the user in the reader).
    var webviewTarget by remember { mutableStateOf<Pair<String, String?>?>(null) }

    LaunchedEffect(pendingCfVerify, sourceBaseUrl) {
        if (pendingCfVerify && sourceBaseUrl.isNotBlank()) {
            pendingCfVerify = false
            webviewTarget = sourceBaseUrl to sourceUserAgent
        }
    }

    // Immersive reading: hide the system bars (status bar + nav bar) while reading, and only
    // bring them back when the user taps the screen to show the HUD.
    val activity = LocalContext.current as? Activity
    DisposableEffect(showHud, activity) {
        val controller = activity?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        if (controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (showHud) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            // Restore system bars when leaving the reader.
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Keep the screen awake while reading (a long scroll session shouldn't let the display sleep).
    DisposableEffect(keepScreenOn, activity) {
        val window = activity?.window
        if (keepScreenOn && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Yomi-style orientation lock: PORTRAIT/LANDSCAPE pin the reader to that orientation for as
    // long as it's open; AUTO restores the system's free rotation. Always reset on dispose so
    // leaving the reader never leaves the app locked.
    DisposableEffect(readerOrientation, activity) {
        val requested = when (readerOrientation) {
            ReaderOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ReaderOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ReaderOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        activity?.requestedOrientation = requested
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Auto-scroll now lives inside the yomi viewer wrapper (it drives the native RecyclerView
    // directly, and any touch on the reader stops it).

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            // Taps are handled per-page: in webtoon mode the yomi viewer owns them (middle =
            // toggle HUD, sides = scroll), and in paged mode each page handles its own tap zones
            // (prev/next/menu) and double-tap zoom, so nothing is wired at the container level.
            .testTag("reader_container")
    ) {
        // Reader Content (error / pages). No full-screen blocking loading screen: while the first
        // chapter's pages load, the previous content stays on screen with a centered spinner.
        when {
            pageError != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Couldn't load this chapter",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = contentTextColor
                            )
                        )
                        Text(
                            text = pageError ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(color = contentTextColor.copy(alpha = 0.7f)),
                            maxLines = 3
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { retryKey++ }) {
                                Text("Retry")
                            }
                            if (sourceBaseUrl.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        webviewTarget = sourceBaseUrl to sourceUserAgent
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = contentTextColor)
                                ) {
                                    Text("Verify in WebView")
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                val pageList = pages ?: emptyList()
                if (isWebtoon) {
                    YomiWebtoonReader(
                        source = source,
                        cacheDir = webtoonCacheDir,
                        streamQueue = streamQueue,
                        streamSegments = streamSegments,
                        segSizes = streamSegments.map { it.size },
                        bgColor = bgColor,
                        textColor = contentTextColor,
                        gaps = readerMode == ReaderMode.WEBTOON_GAPS,
                        cropBorders = cropBorders,
                        doubleTapZoom = doubleTapZoom,
                        tapToChangePages = tapToChangePages,
                        decodeWidth = displayDecodeWidth,
                        autoScroll = autoScroll,
                        autoScrollSpeedDp = autoScrollSpeedDp,
                        initialPageIndex = initialPageIndex,
                        trailer = when {
                            webtoonLoadingNext -> WebtoonTrailer.Loading
                            webtoonError != null && streamNextChapter != null ->
                                WebtoonTrailer.Error(webtoonError ?: "Failed to load the next chapter")
                            streamNextChapter != null -> WebtoonTrailer.Idle
                            else -> WebtoonTrailer.End(activeChapter.name)
                        },
                        viewerRef = viewerRef,
                        onPageChanged = { seg, page, total ->
                            viewerPos = Triple(seg, page, total)
                        },
                        onNearEndChanged = { near ->
                            viewerNearEnd = near
                        },
                        onMenuTap = { showHud = !showHud },
                        onUserScroll = { viewModel.setAutoScroll(false) },
                        onTrailerRetry = {
                            webtoonError = null
                            streamNextChapter?.let { loadNextIntoStream(it) }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val fitScale = when (readerFit) {
                        ReaderFit.FIT -> ContentScale.Fit
                        ReaderFit.FIT_WIDTH -> ContentScale.FillWidth
                        ReaderFit.FIT_HEIGHT -> ContentScale.FillHeight
                    }
                    if (pageList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = contentTextColor)
                        }
                    } else if (readerMode == ReaderMode.VERTICAL) {
                        VerticalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { pageIndex ->
                            PagedPage(
                                model = pageList[pageIndex],
                                pageNumber = pageIndex + 1,
                                contentScale = fitScale,
                                spinnerColor = contentTextColor,
                                doubleTapZoom = doubleTapZoom,
                                tapToChangePages = tapToChangePages,
                                isReversed = false,
                                isVertical = true,
                                onToggleHud = { showHud = !showHud },
                                onPrevPage = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                                    }
                                },
                                onNextPage = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost((pages?.size ?: 1) - 1))
                                    }
                                },
                            )
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            reverseLayout = readerMode == ReaderMode.RIGHT_TO_LEFT,
                            modifier = Modifier.fillMaxSize()
                        ) { pageIndex ->
                            PagedPage(
                                model = pageList[pageIndex],
                                pageNumber = pageIndex + 1,
                                contentScale = fitScale,
                                spinnerColor = contentTextColor,
                                doubleTapZoom = doubleTapZoom,
                                tapToChangePages = tapToChangePages,
                                isReversed = readerMode == ReaderMode.RIGHT_TO_LEFT,
                                isVertical = false,
                                onToggleHud = { showHud = !showHud },
                                onPrevPage = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                                    }
                                },
                                onNextPage = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost((pages?.size ?: 1) - 1))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

    // Bookmark state is tracked locally: activeChapter is a snapshot that won't update when
    // the DB row flips under it, so we mirror the toggle here and reset whenever the chapter
    // changes.
    var chapterBookmarked by remember(activeChapter.id) { mutableStateOf(activeChapter.bookmarked) }

        // Yomi-style reader chrome: top bar (bookmark / overflow / auto-scroll), chapter
        // navigator pill, bottom toolbar and the settings sheets/dialogs live in YomiReaderChrome.
        YomiReaderChrome(
            visible = showHud,
            mangaTitle = manga.title,
            chapterTitle = activeChapter.name,
            bookmarked = chapterBookmarked,
            onToggleBookmarked = {
                viewModel.toggleChapterBookmark(activeChapter.id)
                chapterBookmarked = !chapterBookmarked
            },
            onOpenInWebView = if (sourceBaseUrl.isNotBlank()) {
                { webviewTarget = sourceBaseUrl to sourceUserAgent }
            } else {
                null
            },
            onReloadChapter = { retryKey++ },
            onBack = onBackClick,
            isWebtoon = isWebtoon,
            autoScroll = autoScroll,
            autoScrollSpeedDp = autoScrollSpeedDp,
            onToggleAutoScroll = { viewModel.setAutoScroll(!autoScroll) },
            onAutoScrollSpeedChange = { viewModel.setAutoScrollSpeedDp(it) },
            prevEnabled = prevChapter != null,
            onPrevChapter = { prevChapter?.let { onChapterChange(it.id) } },
            nextEnabled = nextChapter != null,
            onNextChapter = { nextChapter?.let { onChapterChange(it.id) } },
            currentPage = currentPage,
            totalPages = pageTotal,
            onSeekPage = { targetPage ->
                coroutineScope.launch {
                    if (isWebtoon) {
                        val seg = streamPosition.first
                        val start = streamSegments.take(seg).sumOf { it.size } + seg
                        viewerRef.value?.moveToPage(start + targetPage)
                    } else {
                        pagerState.scrollToPage(targetPage)
                    }
                }
            },
            readerMode = readerMode,
            onSelectReaderMode = { mode ->
                if (seriesOverrideEnabled[manga.id] == true) {
                    viewModel.setSeriesReaderMode(manga.id, mode)
                } else {
                    viewModel.setReaderMode(mode)
                }
            },
            readerFit = readerFit,
            onSelectReaderFit = { viewModel.setReaderFit(it) },
            readerOrientation = readerOrientation,
            onSelectReaderOrientation = { viewModel.setReaderOrientation(it) },
            cropBorders = cropBorders,
            onToggleCropBorders = { viewModel.setCropBorders(!cropBorders) },
            doubleTapZoom = doubleTapZoom,
            onToggleDoubleTapZoom = { viewModel.setDoubleTapZoom(!doubleTapZoom) },
            tapToChangePages = tapToChangePages,
            onToggleTapToChangePages = { viewModel.setTapToChangePages(!tapToChangePages) },
            readerBg = readerBg,
            onSelectReaderBg = { viewModel.setReaderBg(it) },
            showPageNumber = showPageNumber,
            onToggleShowPageNumber = { viewModel.setShowPageNumber(!showPageNumber) },
            keepScreenOn = keepScreenOn,
            onToggleKeepScreenOn = { viewModel.setKeepScreenOn(!keepScreenOn) },
            webtoonFade = webtoonFade,
            onToggleWebtoonFade = { viewModel.setWebtoonFade(!webtoonFade) },
            readerQuality = readerQuality,
            onSelectReaderQuality = { viewModel.setReaderQuality(it) },
            onResetSettings = {
                viewModel.setReaderOrientation(ReaderOrientation.AUTO)
                viewModel.setKeepScreenOn(true)
                viewModel.setShowPageNumber(true)
                viewModel.setWebtoonFade(false)
                viewModel.setAutoScroll(false)
                viewModel.setAutoScrollSpeedDp(80f)
                viewModel.setReaderQuality(75)
                viewModel.setCropBorders(false)
                viewModel.setDoubleTapZoom(true)
                viewModel.setTapToChangePages(false)
            },
            seriesOverrideEnabled = seriesOverrideEnabled[manga.id] == true,
            onToggleSeriesOverride = {
                val enabling = seriesOverrideEnabled[manga.id] != true
                if (enabling) {
                    // Seed the series mode with the current effective mode so toggling on pins
                    // whatever is on screen.
                    viewModel.setSeriesReaderMode(manga.id, readerMode)
                }
                viewModel.setSeriesOverrideEnabled(manga.id, enabling)
            },
            chapters = sortedChapters,
            activeChapterId = activeChapter.id,
            onSelectChapter = { onChapterChange(it) }
        )
    }

    webviewTarget?.let { (url, ua) ->
        WebViewDialog(
            url = url,
            userAgent = ua,
            onDismiss = {
                webviewTarget = null
                retryKey++
            }
        )
    }
}

@Composable
private fun PagedPage(
    model: Any?,
    pageNumber: Int,
    contentScale: ContentScale,
    spinnerColor: Color,
    doubleTapZoom: Boolean,
    tapToChangePages: Boolean,
    isReversed: Boolean,
    isVertical: Boolean,
    onToggleHud: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    var zoomed by remember { mutableStateOf(false) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(doubleTapZoom, tapToChangePages, isReversed, isVertical) {
                detectTapGestures(
                    onTap = { pos ->
                        if (!tapToChangePages) {
                            onToggleHud()
                        } else {
                            val w = size.width.coerceAtLeast(1)
                            val h = size.height.coerceAtLeast(1)
                            var prev = if (isVertical) pos.y / h < 0.34f else pos.x / w < 0.34f
                            var next = if (isVertical) pos.y / h > 0.66f else pos.x / w > 0.66f
                            if (isReversed) {
                                val t = prev; prev = next; next = t
                            }
                            when {
                                prev -> onPrevPage()
                                next -> onNextPage()
                                else -> onToggleHud()
                            }
                        }
                    },
                    onDoubleTap = if (doubleTapZoom) {
                        {
                            zoomed = !zoomed
                            pan = Offset.Zero
                        }
                    } else {
                        null
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = if (zoomed) 2f else 1f
                    scaleY = if (zoomed) 2f else 1f
                    translationX = pan.x.coerceIn(-size.width / 2f, size.width / 2f)
                    translationY = pan.y.coerceIn(-size.height / 2f, size.height / 2f)
                }
                .pointerInput(zoomed) {
                    if (zoomed) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            pan += dragAmount
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            ReaderPageImage(
                model = model,
                contentDescription = "Page $pageNumber",
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                spinnerColor = spinnerColor,
                placeholderModifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ReaderPageImage(
    model: Any?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale,
    spinnerColor: Color,
    placeholderModifier: Modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 240.dp),
    crossfade: Boolean = false,
    decodeWidthPx: Int? = null,
    rgb565: Boolean = false
) {
    val context = LocalContext.current
    val screenWidthPx = decodeWidthPx ?: context.resources.displayMetrics.widthPixels
    // Auto-retry a failed page image up to 10 retries with a short pause between attempts — a
    // transient network hiccup or Cloudflare challenge usually clears on a later try. Each retry
    // bumps the attempt counter, which changes the request's reader_retry parameter and busts
    // Coil's cache key, so it's a real new fetch through the extension's own client. Stops after
    // 10 retries; a success at any attempt needs no further refreshes.
    var attempt by remember(model) { mutableStateOf(0) }
    var gaveUp by remember(model) { mutableStateOf(false) }
    var retrying by remember(model) { mutableStateOf(false) }

    // After a failed attempt: pause briefly, then bump the attempt counter to trigger a fresh
    // request (or mark the page as given-up once the retry budget is spent).
    LaunchedEffect(retrying) {
        if (retrying) {
            delay(1200)
            if (attempt < 10) {
                attempt += 1
            } else {
                gaveUp = true
            }
            retrying = false
        }
    }

    val request = remember(model, attempt) {
        ImageRequest.Builder(context)
            .data(model)
            .size(Size(screenWidthPx, Dimension.Undefined))
            .setParameter("reader_retry", attempt)
            .apply {
                if (crossfade) crossfade(true)
                if (rgb565) allowRgb565(true)
            }
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request)
    val state = painter.state

    // Kick a retry when the request lands in the error state (a success needs no further action).
    LaunchedEffect(state) {
        if (state is AsyncImagePainter.State.Error && !gaveUp && !retrying) {
            retrying = true
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale
        )
        when {
            state is AsyncImagePainter.State.Loading -> {
                Box(
                    modifier = placeholderModifier,
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = spinnerColor.copy(alpha = 0.6f),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            state is AsyncImagePainter.State.Error -> {
                Box(
                    modifier = placeholderModifier,
                    contentAlignment = Alignment.Center
                ) {
                    if (gaveUp) {
                        Text(
                            text = "Couldn't load page",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = spinnerColor.copy(alpha = 0.7f)
                            )
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = spinnerColor.copy(alpha = 0.6f),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "Retrying…",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = spinnerColor.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
