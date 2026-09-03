package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
import com.example.data.local.ChapterEntity
import com.example.data.local.MangaEntity
import com.example.ui.MainViewModel
import com.example.ui.ReaderBg
import com.example.ui.ReaderFit
import com.example.ui.ReaderMode
import com.example.ui.ReaderOrientation
import com.example.ui.looksLikeCloudflare
import com.example.util.sortChapters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import com.example.data.source.ExtensionPageImage
import eu.kanade.tachiyomi.source.model.Page
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.ceil
import kotlin.math.floor

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
    var showSettingsDialog by remember { mutableStateOf(false) }

    val readerMode: ReaderMode by viewModel.readerMode.collectAsStateWithLifecycle()
    val readerBg: ReaderBg by viewModel.readerBg.collectAsStateWithLifecycle()
    val readerFit: ReaderFit by viewModel.readerFit.collectAsStateWithLifecycle()
    val readerOrientation: ReaderOrientation by viewModel.readerOrientation.collectAsStateWithLifecycle()
    val keepScreenOn: Boolean by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val showPageNumber: Boolean by viewModel.showPageNumber.collectAsStateWithLifecycle()
    val webtoonFade: Boolean by viewModel.webtoonFade.collectAsStateWithLifecycle()
    val autoScroll: Boolean by viewModel.autoScroll.collectAsStateWithLifecycle()
    val autoScrollSpeedDp: Float by viewModel.autoScrollSpeedDp.collectAsStateWithLifecycle()
    val readerQuality: Int by viewModel.readerQuality.collectAsStateWithLifecycle()

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
    var webtoonLoadingNext by remember(chapter.id) { mutableStateOf(false) }
    var webtoonError by remember(chapter.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(chapter.id, retryKey) {
        pageLoading = true
        pageError = null
        try {
            pages = viewModel.repository.getChapterPageImageModels(chapter.id)
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

    // Webtoon Vertical List State
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPageIndex)

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
                listState.scrollToItem(target)
            } else {
                pagerState.scrollToPage(target.coerceAtMost(list.lastIndex))
            }
        } catch (_: Exception) {
        }
    }

    // The chapter currently on screen (in webtoon modes this can advance past the starting chapter).
    val streamPosition by remember {
        derivedStateOf {
            if (isWebtoon && streamSegments.isNotEmpty()) {
                val sizes = streamSegments.map { it.size }
                val g = listState.firstVisibleItemIndex
                var seg = 0
                var page = 1
                for (i in sizes.indices) {
                    val start = sizes.take(i).sum() + i
                    if (g >= start - 1) {
                        seg = i
                        page = (g - start + 1).coerceIn(1, sizes[i].coerceAtLeast(1))
                    } else break
                }
                Triple(seg, page, sizes.getOrElse(seg) { pages?.size ?: 1 })
            } else {
                Triple(0, 1, pages?.size ?: 1)
            }
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
                val p = viewModel.repository.getChapterPageImageModels(next.id)
                streamQueue = streamQueue + next
                streamSegments = streamSegments + listOf(p)
            } catch (e: Throwable) {
                webtoonError = e.message ?: "Failed to load next chapter"
            } finally {
                webtoonLoadingNext = false
            }
        }
    }

    // When the user nears the bottom of the stream, fetch the next chapter and append it.
    val nearStreamEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) false
            else {
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= total - 4
            }
        }
    }

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
    val context = LocalContext.current
    val screenWidthPx = context.resources.displayMetrics.widthPixels
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
    // Fallback height for a webtoon strip whose true aspect ratio isn't known yet. Bounded so the
    // page can start loading (Coil won't decode a page in an unbounded-height list item); the slot
    // snaps to the real height as soon as the prewarm below learns the page's dimensions. Sized
    // for a typical ~0.7 page ratio so the snap is small.
    val fallbackPageHeight = with(LocalConfiguration.current) { (screenWidthDp / 0.7f).dp }

    // Known page aspect ratios, filled in as nearby pages decode (prewarm below). Webtoon list
    // items use them to size their slot to the page's real height up front, so the list doesn't
    // relayout every strip as it finishes loading — that relayout is what made scrolling feel
    // laggy. Keyed by the page model's string (a URL for MangaDex, ExtensionPageImage for
    // extensions — both unique per page).
    // Bounds how many webtoon pages may be fetching/decoding at once (visible strips + prefetch
    // share this gate). Two concurrent operations fill the window fast, and hard-cap the burst
    // that used to spike when jumping into the middle of a long chapter — a dozen full-screen
    // strips loading at once was what made the reader and the whole app stutter.
    val loadGate = remember(chapter.id) { Semaphore(2) }

    // Latest in-flight jump warm-up job; cancelling the previous one means a fast slider drag only
    // ever runs the warm for the most recent target.
    var warmJob by remember(chapter.id) { mutableStateOf<Job?>(null) }

    // Pre-fetch the SOURCE BYTES of a window around a target page into the bounded strip-bytes
    // cache, so jumping there renders immediately: each destination strip decodes its own tiles
    // from cached bytes instead of waiting on a network fetch. Gated like everything else.
    fun warmWindow(targetPage: Int) {
        val segs = if (streamSegments.isNotEmpty()) streamSegments
        else if (pages != null) listOf(pages!!)
        else return
        val seg = if (isWebtoon) streamPosition.first.coerceIn(0, segs.lastIndex) else 0
        val segPages = segs[seg]
        if (segPages.isEmpty()) return
        val target = targetPage.coerceIn(0, segPages.lastIndex)
        val from = (target - 1).coerceAtLeast(0)
        val to = (target + 6).coerceAtMost(segPages.lastIndex)
        warmJob?.cancel()
        warmJob = coroutineScope.launch {
            for (p in from..to) {
                if (!isActive) return@launch
                val m = segPages[p]
                val url = pageUrl(m)
                if (readerStripBytes[url] != null) continue
                try {
                    val b = loadGate.withPermit {
                        withContext(Dispatchers.IO) { fetchPageBytes(m) }
                    }
                    cacheStripBytes(url, b)
                } catch (_: Throwable) {
                }
            }
        }
    }

    // Resuming into the middle of a long chapter: warm the window around the resume point so the
    // first visible strips are already decoded (and their slots pre-sized) instead of all loading
    // at once.
    LaunchedEffect(pages, chapter.id, isWebtoon, startAtBeginning) {
        if (isWebtoon && pages != null && !startAtBeginning && initialPageIndex > 1) {
            warmWindow(initialPageIndex)
        }
    }

    // Rolling byte prefetch — ONE persistent loop for webtoon mode, NOT keyed on the current page.
    // Each strip now sizes and decodes itself from its own source bytes (tile-based), so the only
    // thing a reader needs ahead of time is the source bytes ready before the strip scrolls into
    // view. This loop pre-fetches bytes for the window around the current position into the bounded
    // [readerStripBytes] cache, so scrolling renders tiles instantly. (Paged modes don't use this —
    // ReaderPageImage handles its own loads via Coil.)
    LaunchedEffect(pages, streamSegments, isWebtoon) {
        if (!isWebtoon) return@LaunchedEffect
        while (isActive) {
            val segs = streamSegments
            if (segs.isEmpty() || segs.any { it.isEmpty() }) { delay(120); continue }
            val segIdx = streamPosition.first.coerceIn(0, segs.lastIndex)
            val segPages = segs[segIdx]
            if (segPages.isEmpty()) { delay(120); continue }
            val cur = (currentPage - 1).coerceIn(0, segPages.lastIndex)
            val from = (cur - 1).coerceAtLeast(0)
            val to = (cur + 8).coerceAtMost(segPages.lastIndex)
            var didWork = false
            for (p in from..to) {
                val m = segPages[p]
                val url = pageUrl(m)
                if (readerStripBytes[url] != null) continue
                try {
                    val b = loadGate.withPermit {
                        withContext(Dispatchers.IO) { fetchPageBytes(m) }
                    }
                    cacheStripBytes(url, b)
                } catch (_: Throwable) {
                }
                didWork = true
                break
            }
            if (didWork) delay(40) else delay(250)
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

    // Auto-scroll (Yomi-style): smoothly scrolls the webtoon list at the chosen speed while
    // enabled. Any touch on the list stops it (see the pointerInput on the LazyColumn).
    LaunchedEffect(isWebtoon, autoScroll, autoScrollSpeedDp, listState) {
        if (!isWebtoon || !autoScroll) return@LaunchedEffect
        val pxPerMs = with(density) { autoScrollSpeedDp.dp.toPx() } / 1000f
        while (isActive) {
            listState.scroll { scrollBy(pxPerMs * 16f) }
            delay(16)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showHud = !showHud }
                )
            }
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
                    Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = if (readerMode == ReaderMode.WEBTOON_GAPS) {
                            Arrangement.spacedBy(10.dp)
                        } else {
                            Arrangement.Top
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(autoScroll) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitFirstDown(requireUnconsumed = false)
                                        if (autoScroll) viewModel.setAutoScroll(false)
                                    }
                                }
                            }
                    ) {
                        // While the chapter's pages are still loading, fill the viewport with a
                        // centered spinner (no separate full-screen loading screen).
                        if (pageList.isEmpty()) {
                            item(key = "initial_loading") {
                                Box(
                                    modifier = Modifier.fillParentMaxHeight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = contentTextColor)
                                }
                            }
                        }
                        // Each streamed chapter: a small divider, then its pages.
                        streamQueue.forEachIndexed { segIdx, segChapter ->
                            val segPages = streamSegments.getOrNull(segIdx)
                            if (segPages != null) {
                                if (segIdx > 0) {
                                    item(key = "divider_${segChapter.id}") {
                                        ChapterDivider(
                                            title = segChapter.name,
                                            textColor = contentTextColor
                                        )
                                    }
                                }
                                itemsIndexed(
                                    segPages,
                                    key = { pi, _ -> "${segChapter.id}_$pi" }
                                ) { pi, pageModel ->
                                    WebtoonPage(
                                        model = pageModel,
                                        contentDescription = "Page ${pi + 1}",
                                        decodeWidthPx = webtoonDecodeWidth,
                                        rgb565 = useRgb565,
                                        fade = webtoonFade,
                                        spinnerColor = contentTextColor,
                                        fallbackHeight = fallbackPageHeight,
                                        loadGate = loadGate,
                                        testTag = "reader_page_${segIdx}_$pi"
                                    )
                                }
                            }
                        }

                        // Trailing item: spinner / retry / end-of-stream.
                        item(key = "stream_trailer") {
                            when {
                                webtoonLoadingNext -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(color = contentTextColor.copy(alpha = 0.7f))
                                        Text(
                                            text = "Loading next chapter...",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = contentTextColor.copy(alpha = 0.7f)
                                            )
                                        )
                                    }
                                }

                                webtoonError != null && streamNextChapter != null -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "Couldn't load the next chapter",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = contentTextColor.copy(alpha = 0.7f)
                                            )
                                        )
                                        Button(onClick = { webtoonError = null; loadNextIntoStream(streamNextChapter) }) {
                                            Text("Retry")
                                        }
                                    }
                                }

                                streamNextChapter != null -> {
                                    // Auto-continue trigger zone; loads as the user nears the bottom.
                                    Spacer(modifier = Modifier.height(48.dp))
                                }

                                else -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "End of ${activeChapter.name}",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = contentTextColor
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "You have caught up with the latest released chapter!",
                                            style = MaterialTheme.typography.bodySmall.copy(color = contentTextColor.copy(alpha = 0.7f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
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
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                ReaderPageImage(
                                    model = pageList[pageIndex],
                                    contentDescription = "Page ${pageIndex + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = fitScale,
                                    spinnerColor = contentTextColor,
                                    placeholderModifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            reverseLayout = readerMode == ReaderMode.RIGHT_TO_LEFT,
                            modifier = Modifier.fillMaxSize()
                        ) { pageIndex ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                ReaderPageImage(
                                    model = pageList[pageIndex],
                                    contentDescription = "Page ${pageIndex + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = fitScale,
                                    spinnerColor = contentTextColor,
                                    placeholderModifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top HUD Bar
        AnimatedVisibility(
            visible = showHud,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color(0xD9161926),
                contentColor = Color.White
            ) {
                TopAppBar(
                    windowInsets = WindowInsets(0),
                    title = {
                        Column {
                            Text(
                                text = manga.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = activeChapter.name,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray),
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag("reader_back_button")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.testTag("reader_settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Reader Settings", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }

        // Bottom HUD Bar
        AnimatedVisibility(
            visible = showHud,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color(0xD9161926),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { prevChapter?.let { onChapterChange(it.id) } },
                            enabled = prevChapter != null
                        ) {
                            Icon(
                                imageVector = Icons.Default.NavigateBefore,
                                contentDescription = "Previous Chapter",
                                tint = if (prevChapter != null) Color.White else Color.Gray
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isWebtoon) {
                                IconButton(
                                    onClick = { viewModel.setAutoScroll(!autoScroll) },
                                    modifier = Modifier.testTag("reader_autoscroll_button")
                                ) {
                                    Icon(
                                        imageVector = if (autoScroll) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (autoScroll) "Pause auto-scroll" else "Start auto-scroll",
                                        tint = Color.White
                                    )
                                }
                            }

                            if (pages != null && showPageNumber) {
                                Text(
                                    text = "Page $currentPage / $pageTotal",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    ),
                                    modifier = Modifier.testTag("page_indicator_text")
                                )
                            }
                        }

                        IconButton(
                            onClick = { nextChapter?.let { onChapterChange(it.id) } },
                            enabled = nextChapter != null
                        ) {
                            Icon(
                                imageVector = Icons.Default.NavigateNext,
                                contentDescription = "Next Chapter",
                                tint = if (nextChapter != null) Color.White else Color.Gray
                            )
                        }
                    }

                    Slider(
                        value = currentPage.toFloat(),
                        onValueChange = { pageVal ->
                            val targetPage = pageVal.toInt() - 1
                            coroutineScope.launch {
                                if (isWebtoon) {
                                    val seg = streamPosition.first
                                    val start = streamSegments.take(seg).sumOf { it.size } + seg
                                    listState.scrollToItem(start + targetPage)
                                } else {
                                    pagerState.scrollToPage(targetPage)
                                }
                            }
                            // Pre-size and pre-decode the destination window (gated, coalesced) so a
                            // long jump lands on already-sized, already-cached pages.
                            if (isWebtoon) warmWindow(targetPage)
                        },
                        onValueChangeFinished = {
                            if (isWebtoon) warmWindow(currentPage - 1)
                        },
                        valueRange = 1f..pageTotal.coerceAtLeast(1).toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reader_page_slider")
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = "Reader Settings",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    ReaderSettingsSectionTitle("Reading Mode")

                    ReaderModeOption(
                        label = "Webtoon (Long Strip)",
                        selected = readerMode == ReaderMode.WEBTOON,
                        onClick = { viewModel.setReaderMode(ReaderMode.WEBTOON) }
                    )
                    ReaderModeOption(
                        label = "Vertical With Gaps",
                        selected = readerMode == ReaderMode.WEBTOON_GAPS,
                        onClick = { viewModel.setReaderMode(ReaderMode.WEBTOON_GAPS) }
                    )
                    ReaderModeOption(
                        label = "Vertical Paged",
                        selected = readerMode == ReaderMode.VERTICAL,
                        onClick = { viewModel.setReaderMode(ReaderMode.VERTICAL) }
                    )
                    ReaderModeOption(
                        label = "Manga Left-to-Right",
                        selected = readerMode == ReaderMode.LEFT_TO_RIGHT,
                        onClick = { viewModel.setReaderMode(ReaderMode.LEFT_TO_RIGHT) }
                    )
                    ReaderModeOption(
                        label = "Manga Right-to-Left (Traditional)",
                        selected = readerMode == ReaderMode.RIGHT_TO_LEFT,
                        onClick = { viewModel.setReaderMode(ReaderMode.RIGHT_TO_LEFT) }
                    )

                    ReaderSettingsSectionTitle("Page Fit (paged modes)")

                    ReaderModeOption(
                        label = "Fit Screen",
                        selected = readerFit == ReaderFit.FIT,
                        onClick = { viewModel.setReaderFit(ReaderFit.FIT) }
                    )
                    ReaderModeOption(
                        label = "Fit Width",
                        selected = readerFit == ReaderFit.FIT_WIDTH,
                        onClick = { viewModel.setReaderFit(ReaderFit.FIT_WIDTH) }
                    )
                    ReaderModeOption(
                        label = "Fit Height",
                        selected = readerFit == ReaderFit.FIT_HEIGHT,
                        onClick = { viewModel.setReaderFit(ReaderFit.FIT_HEIGHT) }
                    )

                    ReaderSettingsSectionTitle("Reader Background")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ReaderBgChip(
                            label = "OLED Black",
                            color = Color.Black,
                            isSelected = readerBg == ReaderBg.PURE_BLACK,
                            onClick = { viewModel.setReaderBg(ReaderBg.PURE_BLACK) }
                        )
                        ReaderBgChip(
                            label = "Dark",
                            color = Color(0xFF181A24),
                            isSelected = readerBg == ReaderBg.DARK_GRAY,
                            onClick = { viewModel.setReaderBg(ReaderBg.DARK_GRAY) }
                        )
                        ReaderBgChip(
                            label = "Cream",
                            color = Color(0xFFFBF0D9),
                            isSelected = readerBg == ReaderBg.CREAM,
                            onClick = { viewModel.setReaderBg(ReaderBg.CREAM) }
                        )
                        ReaderBgChip(
                            label = "White",
                            color = Color.White,
                            isSelected = readerBg == ReaderBg.WHITE,
                            onClick = { viewModel.setReaderBg(ReaderBg.WHITE) }
                        )
                    }

                    ReaderSettingsSectionTitle("Display")

                    ReaderModeOption(
                        label = "Auto (follow system)",
                        selected = readerOrientation == ReaderOrientation.AUTO,
                        onClick = { viewModel.setReaderOrientation(ReaderOrientation.AUTO) }
                    )
                    ReaderModeOption(
                        label = "Portrait",
                        selected = readerOrientation == ReaderOrientation.PORTRAIT,
                        onClick = { viewModel.setReaderOrientation(ReaderOrientation.PORTRAIT) }
                    )
                    ReaderModeOption(
                        label = "Landscape",
                        selected = readerOrientation == ReaderOrientation.LANDSCAPE,
                        onClick = { viewModel.setReaderOrientation(ReaderOrientation.LANDSCAPE) }
                    )
                    ReaderSettingsSwitchRow(
                        label = "Show page number",
                        checked = showPageNumber,
                        onCheckedChange = { viewModel.setShowPageNumber(it) }
                    )
                    ReaderSettingsSwitchRow(
                        label = "Keep screen on",
                        checked = keepScreenOn,
                        onCheckedChange = { viewModel.setKeepScreenOn(it) }
                    )

                    ReaderSettingsSectionTitle("Webtoon")

                    Text(
                        text = "Image quality",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier.padding(start = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderQualityChip("Low (fast)", readerQuality == 50) { viewModel.setReaderQuality(50) }
                        ReaderQualityChip("Medium", readerQuality == 75) { viewModel.setReaderQuality(75) }
                        ReaderQualityChip("High (sharp)", readerQuality == 100) { viewModel.setReaderQuality(100) }
                    }

                    ReaderSettingsSwitchRow(
                        label = "Fade pages in",
                        checked = webtoonFade,
                        onCheckedChange = { viewModel.setWebtoonFade(it) }
                    )
                    ReaderSettingsSwitchRow(
                        label = "Auto-scroll",
                        checked = autoScroll,
                        onCheckedChange = { viewModel.setAutoScroll(it) }
                    )
                    if (autoScroll) {
                        Text(
                            text = "Auto-scroll speed: ${autoScrollSpeedDp.toInt()} dp/s",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                        )
                        Slider(
                            value = autoScrollSpeedDp,
                            onValueChange = { viewModel.setAutoScrollSpeedDp(it) },
                            valueRange = 20f..200f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.setReaderOrientation(ReaderOrientation.AUTO)
                            viewModel.setKeepScreenOn(true)
                            viewModel.setShowPageNumber(true)
                            viewModel.setWebtoonFade(false)
                            viewModel.setAutoScroll(false)
                            viewModel.setAutoScrollSpeedDp(80f)
                            viewModel.setReaderQuality(75)
                        }
                    ) {
                        Text("Reset")
                    }
                    Button(onClick = { showSettingsDialog = false }) {
                        Text("Done")
                    }
                }
            }
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
private fun ChapterDivider(title: String, textColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "— $title —",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                color = textColor.copy(alpha = 0.7f)
            ),
            maxLines = 1
        )
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

/**
 * A webtoon strip rendered as a stack of bounded-height TILES — no full-height bitmap is ever
 * allocated. That is the core trick behind smooth long-image readers: a ~1000x20000 source strip
 * decoded whole is an ~80MB bitmap that thrashes memory and can exceed the GPU's max texture
 * size, which is exactly why tall high-res manhwa chapters stuttered. Here the strip's source
 * bytes are fetched ONCE through the extension's own client (headers/descrambling still apply),
 * the image geometry is read from the header, and each ~2048px-tall tile is decoded independently
 * at (at most) the display width via [BitmapRegionDecoder]. Tiles decode sequentially under
 * [loadGate], so across the whole reader only two pages' worth of work runs at once. Every tile
 * slot is pre-sized from the same geometry, so a strip never relayouts. Decoded tiles are cached
 * in a bounded global cache, so scrolling back renders instantly without re-fetching.
 */
private const val WEBTOON_TILE_OUT_H = 2048
private const val READER_TILE_BYTES_BUDGET = 64L * 1024 * 1024
private const val READER_STRIP_BYTES_BUDGET = 64L * 1024 * 1024

// Bounded in-memory caches shared across the reader: decoded webtoon TILES (keyed "url#tile") and
// fetched source BYTES (keyed url). Both evict oldest-first when over budget, so scrolling back
// re-renders instantly while memory stays flat — the behaviour a smooth reader wants, without
// holding full-resolution strips.
private val readerTileCache = ConcurrentHashMap<String, ImageBitmap>()
private val readerTileOrder = ConcurrentLinkedDeque<String>()
private val readerTileBytes = java.util.concurrent.atomic.AtomicLong(0)

private fun cacheTile(key: String, bmp: ImageBitmap) {
    if (readerTileCache.containsKey(key)) return
    val bytes = bmp.width.toLong() * bmp.height * 4
    synchronized(readerTileBytes) {
        readerTileCache[key] = bmp
        readerTileOrder.addLast(key)
        readerTileBytes.addAndGet(bytes)
        while (readerTileBytes.get() > READER_TILE_BYTES_BUDGET) {
            val k = readerTileOrder.pollFirst() ?: break
            val old = readerTileCache.remove(k) ?: continue
            readerTileBytes.addAndGet(-(old.width.toLong() * old.height * 4))
        }
    }
}

private val readerStripBytes = ConcurrentHashMap<String, ByteArray>()
private val readerStripOrder = ConcurrentLinkedDeque<String>()
private val readerStripBytesTotal = java.util.concurrent.atomic.AtomicLong(0)

private fun cacheStripBytes(key: String, data: ByteArray) {
    if (readerStripBytes.containsKey(key)) return
    synchronized(readerStripBytesTotal) {
        readerStripBytes[key] = data
        readerStripOrder.addLast(key)
        readerStripBytesTotal.addAndGet(data.size.toLong())
        while (readerStripBytesTotal.get() > READER_STRIP_BYTES_BUDGET) {
            val k = readerStripOrder.pollFirst() ?: break
            val old = readerStripBytes.remove(k) ?: continue
            readerStripBytesTotal.addAndGet(-old.size.toLong())
        }
    }
}

// Stable identity for a reader page (its image URL for extension pages, its string otherwise).
private fun pageUrl(model: Any): String = (model as? ExtensionPageImage)?.imageUrl ?: model.toString()

// Fetch a page's ORIGINAL bytes through the extension's own client/headers (the same path Coil's
// fetcher uses), so hotlink-protected CDNs and descrambler interceptors behave identically.
private suspend fun fetchPageBytes(model: Any): ByteArray {
    val ep = model as? ExtensionPageImage
    if (ep != null) {
        val page = Page(0, url = ep.pageUrl, imageUrl = ep.imageUrl)
        val response = ep.source.getImage(page)
        val body = response.body ?: throw IOException("Null response body")
        return body.bytes()
    }
    val url = model.toString()
    val conn = java.net.URL(url).openConnection()
    conn.connectTimeout = 15000
    conn.readTimeout = 30000
    val stream = conn.getInputStream()
    return stream.use { it.readBytes() }
}

// Geometry of a tiled strip: the source's native size, the display width we decode at, and the
// per-tile slice math that cuts the strip into bounded-height tiles.
private class StripGeometry(
    val naturalW: Int,
    val naturalH: Int,
    val sampleWidth: Int,
    val sample: Int,
    val tileCount: Int,
) {
    val scale = sampleWidth.toFloat() / naturalW
    val displayH: Int = ceil(naturalH * scale).toInt()

    fun tileOutH(t: Int): Int = minOf(WEBTOON_TILE_OUT_H, displayH - t * WEBTOON_TILE_OUT_H)

    fun srcTop(t: Int): Int = floor(t * WEBTOON_TILE_OUT_H / scale.toDouble()).toInt()

    fun srcBottom(t: Int): Int = minOf(naturalH, ceil(((t + 1) * WEBTOON_TILE_OUT_H).toDouble() / scale).toInt())
}

/**
 * One webtoon strip. Unlike the paged-mode [ReaderPageImage], this does NOT fire an independent
 * Coil request per visible page — every load here shares [loadGate], so at most two pages' worth
 * of fetching/decoding happens at any moment. That bound is what keeps a jump into the middle of
 * a 100-page chapter smooth: previously the ~10 newly-composed pages all fetched and decoded at
 * once, spiking memory and dropping frames across the whole app. While loading it shows a static
 * empty slot (no animated spinner — animating many spinners while scrolling is itself jank), and
 * the slot is sized from the strip's own geometry so nothing shifts when tiles land.
 */
@Composable
private fun WebtoonPage(
    model: Any,
    contentDescription: String,
    decodeWidthPx: Int,
    rgb565: Boolean,
    fade: Boolean,
    spinnerColor: Color,
    fallbackHeight: Dp,
    loadGate: Semaphore,
    testTag: String
) {
    var geometry by remember(model) { mutableStateOf<StripGeometry?>(null) }
    var tiles by remember(model) { mutableStateOf<Map<Int, ImageBitmap>>(emptyMap()) }
    var failed by remember(model) { mutableStateOf(false) }
    var retryKey by remember(model) { mutableStateOf(0) }
    val url = remember(model) { pageUrl(model) }

    LaunchedEffect(model, decodeWidthPx, rgb565, retryKey) {
        geometry = null
        tiles = emptyMap()
        failed = false
        val bytes = readerStripBytes[url] ?: try {
            loadGate.withPermit { withContext(Dispatchers.IO) { fetchPageBytes(model) } }
        } catch (e: Exception) {
            failed = true
            return@LaunchedEffect
        }
        cacheStripBytes(url, bytes)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val nw = bounds.outWidth
        val nh = bounds.outHeight
        if (nw <= 0 || nh <= 0) {
            failed = true
            return@LaunchedEffect
        }
        var sample = 1
        while ((nw / (sample * 2)) >= (decodeWidthPx * 9) / 10) sample *= 2
        val scale = decodeWidthPx.toFloat() / nw
        val displayH = ceil(nh * scale).toInt()
        val tileCount = (displayH + WEBTOON_TILE_OUT_H - 1) / WEBTOON_TILE_OUT_H
        val geo = StripGeometry(nw, nh, decodeWidthPx, sample, tileCount)
        geometry = geo

        // Reuse any tiles already decoded for this page, so scrolling back renders instantly.
        val preloaded = HashMap<Int, ImageBitmap>()
        for (t in 0 until tileCount) readerTileCache["$url#$t"]?.let { preloaded[t] = it }
        if (preloaded.isNotEmpty()) tiles = preloaded

        try {
            val decoder = BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
            try {
                for (t in 0 until tileCount) {
                    if (tiles.containsKey(t)) continue
                    val rect = Rect(0, geo.srcTop(t), nw, geo.srcBottom(t))
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        if (rgb565) inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bmp = withContext(Dispatchers.IO) {
                        loadGate.withPermit { decoder.decodeRegion(rect, opts) }
                    }
                    if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                        val img = bmp.asImageBitmap()
                        cacheTile("$url#$t", img)
                        tiles = tiles + (t to img)
                    }
                }
            } finally {
                decoder.recycle()
            }
        } catch (e: Exception) {
            failed = true
        }
    }

    val geo = geometry
    if (geo == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(fallbackHeight)
                .clipToBounds()
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            if (failed) {
                Text(
                    text = "Couldn't load page",
                    style = MaterialTheme.typography.bodySmall.copy(color = spinnerColor.copy(alpha = 0.7f)),
                    modifier = Modifier.padding(8.dp)
                )
            }
            // Static empty slot until geometry is known; no animated spinner.
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        for (t in 0 until geo.tileCount) {
            val tileH = geo.tileOutH(t)
            val bmp = tiles[t]
            val alpha by animateFloatAsState(
                targetValue = if (bmp != null) 1f else 0f,
                animationSpec = tween(if (fade) 160 else 0),
                label = "tileFade"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(geo.sampleWidth.toFloat() / tileH)
                    .clipToBounds()
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = if (t == 0) contentDescription else null,
                        modifier = Modifier.fillMaxSize().alpha(alpha),
                        contentScale = ContentScale.FillBounds
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderSettingsSectionTitle(text: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ReaderSettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ReaderQualityChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ReaderModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun ReaderBgChip(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.Gray, CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
