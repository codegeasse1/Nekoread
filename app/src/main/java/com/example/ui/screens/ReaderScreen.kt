package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.Activity
import android.content.pm.ActivityInfo
import coil.compose.AsyncImagePainter
import coil.compose.LocalImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.example.data.local.ChapterEntity
import com.example.data.local.MangaEntity
import com.example.data.source.ExtensionPageImage
import com.example.util.describe
import com.example.ui.MainViewModel
import com.example.ui.ReaderBg
import com.example.ui.ReaderFit
import com.example.ui.ReaderMode
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: MainViewModel,
    manga: MangaEntity?,
    chapter: ChapterEntity?,
    allChapters: List<ChapterEntity>,
    onBackClick: () -> Unit,
    onChapterChange: (String) -> Unit,
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
    var showChaptersSheet by remember { mutableStateOf(false) }
    var rotationLocked by remember { mutableStateOf(false) }

    val readerMode: ReaderMode by viewModel.readerMode.collectAsStateWithLifecycle()
    val readerBg: ReaderBg by viewModel.readerBg.collectAsStateWithLifecycle()
    val readerFit: ReaderFit by viewModel.readerFit.collectAsStateWithLifecycle()

    // Both long-strip modes render as one continuous vertical list (only the gap between pages
    // differs); every check in the reader should treat them alike.
    val isWebtoon = readerMode == ReaderMode.WEBTOON || readerMode == ReaderMode.WEBTOON_GAPS

    var pages by remember { mutableStateOf<List<Any>?>(null) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var pageLoading by remember { mutableStateOf(true) }
    var retryKey by remember { mutableStateOf(0) }
    var pageImageErrors by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    val pageRetries = remember { mutableStateMapOf<Int, Int>() }
    // Slider thumb while the user is dragging it; the actual scroll happens once on release so a
    // drag can't fire a storm of conflicting scrollToItem calls into unloaded content.
    var sliderDragPage by remember { mutableStateOf<Float?>(null) }

    // Continuous scroll (webtoon): chapters queued after the current one, appended automatically
    // as the reader reaches the end. The first chapter's pages live in [pages]; every queued
    // chapter is a (chapter, loaded-pages-or-null, error-or-null) entry.
    val queuedChapters = remember { mutableStateListOf<QueuedCh>() }

    // Chapters BEFORE the current one, prepended to the strip when the reader scrolls near its top
    // so Long strip scrolls back into earlier chapters — not just forward — without a chapter
    // change or reload. Stored in reading order (earliest first); the strip renders them reversed.
    val previousChapters = remember { mutableStateListOf<QueuedCh>() }
    // True while a previous chapter's page list is being fetched, so the prepend effect never
    // fires a second prepend into the same (still-loading) head of the strip.
    var prependingPrevious by remember(chapter.id) { mutableStateOf(false) }

    LaunchedEffect(chapter.id, retryKey) {
        pageLoading = true
        pageError = null
        pageImageErrors = emptyMap()
        queuedChapters.clear()
        previousChapters.clear()
        try {
            pages = withTimeout(MAIN_LOAD_TIMEOUT_MS) {
                viewModel.repository.getChapterPageImageModels(chapter.id)
            }
        } catch (e: Throwable) {
            pageError = e.describe()
            pages = null
        } finally {
            pageLoading = false
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // Download-ahead scope: background job that pulls pages into the on-device reader_pages cache
    // (Aniyomi/Tadami-style page cache), so scrolling never waits on the network. It's tied to this
    // composition, so leaving the reader cancels it automatically. All prefetch downloads share ONE
    // gate so their combined concurrency stays well under the CDN's per-host connection limit —
    // exceeding it made the reader's own foreground page loads stall ("keeps loading").
    val prefetchScope = rememberCoroutineScope()
    val prefetchJob = remember { mutableStateOf<Job?>(null) }
    val nextPrefetchJob = remember { mutableStateOf<Job?>(null) }
    val prefetchGate = remember { Semaphore(2) }

    // Re-load a queued chapter's pages after a failure (tapped from its error row). A failed
    // queued chapter never blocks continuous scroll — the reader just shows the retry row.
    fun retryQueuedChapter(chapterId: String) {
        val i = queuedChapters.indexOfFirst { it.chapter.id == chapterId }
        if (i >= 0) {
            val qc = queuedChapters[i]
            queuedChapters[i] = QueuedCh(qc.chapter, null, null)
            coroutineScope.launch {
                try {
                    val p = withTimeout(QUEUED_LOAD_TIMEOUT_MS) {
                        viewModel.repository.getChapterPageImageModels(qc.chapter.id)
                    }
                    val cur = queuedChapters.getOrNull(i)
                    if (cur != null && cur.chapter.id == qc.chapter.id) {
                        queuedChapters[i] = QueuedCh(qc.chapter, p, null)
                    }
                } catch (e: Throwable) {
                    val cur = queuedChapters.getOrNull(i)
                    if (cur != null && cur.chapter.id == qc.chapter.id) {
                        queuedChapters[i] = QueuedCh(qc.chapter, null, e.describe())
                    }
                }
            }
            return
        }
        // Same for a previous chapter prepended above the current one.
        val j = previousChapters.indexOfFirst { it.chapter.id == chapterId }
        if (j < 0) return
        val pc = previousChapters[j]
        previousChapters[j] = QueuedCh(pc.chapter, null, null)
        coroutineScope.launch {
            try {
                val p = withTimeout(QUEUED_LOAD_TIMEOUT_MS) {
                    viewModel.repository.getChapterPageImageModels(pc.chapter.id)
                }
                val cur = previousChapters.getOrNull(j)
                if (cur != null && cur.chapter.id == pc.chapter.id) {
                    previousChapters[j] = QueuedCh(pc.chapter, p, null)
                }
            } catch (e: Throwable) {
                val cur = previousChapters.getOrNull(j)
                if (cur != null && cur.chapter.id == pc.chapter.id) {
                    previousChapters[j] = QueuedCh(pc.chapter, null, e.describe())
                }
            }
        }
    }

    // Display size + image loader used to downsample reader pages to screen width (much cheaper to
    // decode than full-resolution, which is what made webtoon scrolling lag).
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val imageLoader = LocalImageLoader.current

    // Reading a long chapter floods Coil's shared in-memory cache with page bitmaps. Evict ONLY
    // those page entries when the reader leaves (freeing the memory) — NOT the whole cache, which
    // would force every library/catalog cover thumbnail to re-download and show as blank tiles
    // right after closing the reader. Reader pages are memory-cached under their image URL
    // (ExtensionPageImageKeyer), so we can evict exactly those keys; covers (keyed
    // "cover:<source>|<url>") are untouched and load instantly from memory.
    // Also stop the source's background page-list prefetches so nothing keeps hammering the CDN.
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                val mc = imageLoader?.memoryCache
                if (mc != null) {
                    val urls = LinkedHashSet<String>()
                    fun collect(list: List<Any>?) {
                        list?.forEach { p -> if (p is ExtensionPageImage) urls.add(p.imageUrl) }
                    }
                    collect(pages)
                    queuedChapters.forEach { collect(it.pages) }
                    previousChapters.forEach { collect(it.pages) }
                    urls.forEach { mc.remove(MemoryCache.Key(it)) }
                }
            }
            prefetchJob.value?.cancel()
            nextPrefetchJob.value?.cancel()
            HttpSource.cancelAllPrefetches()
        }
    }

    val screenW = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    // Decode webtoon pages at most this tall (in pixels). Bounded so the biggest possible bitmap
    // (screenW x this) stays small enough that several can coexist in memory — the old ~8000px cap
    // made 34MB bitmaps, which is exactly what froze/ANR'd and crashed the app on slow networks.
    // Strips taller than 2 screens are downscaled; typical per-panel webtoon pages keep full detail.
    val screenH = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val webtoonDecodeH = minOf(screenH * 2, 4800).coerceAtLeast(1600)
    // Loading placeholder: a SMALL minimum height. A viewport-tall placeholder centered short
    // pages inside a full-screen box, leaving big black bands that cut the artwork — this was the
    // "image cut in half" bug. With a small placeholder the item collapses to the image size.
    val webtoonPlaceholderH = 200.dp
    val activity = context as? Activity

    fun toggleRotation() {
        rotationLocked = !rotationLocked
        activity?.requestedOrientation = if (rotationLocked) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Determine current background color
    val bgColor = when (readerBg) {
        ReaderBg.PURE_BLACK -> Color.Black
        ReaderBg.DARK_GRAY -> Color(0xFF181A24)
        ReaderBg.CREAM -> Color(0xFFFBF0D9)
        ReaderBg.WHITE -> Color.White
    }

    val contentTextColor = if (readerBg == ReaderBg.CREAM || readerBg == ReaderBg.WHITE) Color.Black else Color.White

    // The reader draws fullscreen behind the system bars, so the status-bar icons must follow
    // the reader background (light reader -> dark icons, dark reader -> light icons).
    val view = LocalView.current
    LaunchedEffect(bgColor) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = readerBg == ReaderBg.CREAM || readerBg == ReaderBg.WHITE
    }

    // Tadami-style immersive reader: with the HUD hidden, hide the system status + navigation
    // bars too (true fullscreen reading, no battery/network/time clutter); tapping to show the
    // HUD brings them back. When the reader is left, the bars are restored for the rest of the app.
    LaunchedEffect(showHud) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (showHud) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val activity = view.context as? Activity
            if (activity != null) {
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Webtoon Vertical List State — keyed on the chapter so switching chapters resets the scroll
    // to the new chapter's start (a stale index from a long previous list landed mid/end of the
    // new chapter and re-triggered continuous-scroll queuing).
    val listState = remember(chapter.id) {
        LazyListState(firstVisibleItemIndex = (chapter.lastPageRead - 1).coerceAtLeast(0))
    }

    // Paged Reader State — same reason: reset per chapter.
    val pagerState = remember(chapter.id) {
        PagerState(
            currentPage = (chapter.lastPageRead - 1).coerceAtLeast(0),
            pageCount = { pages?.size ?: 0 }
        )
    }

    val currentPage by remember {
        derivedStateOf {
            val total = pages?.size ?: 0
            if (total == 0) {
                0
            } else if (readerMode == ReaderMode.WEBTOON || readerMode == ReaderMode.WEBTOON_GAPS) {
                (listState.firstVisibleItemIndex + 1).coerceAtMost(total)
            } else {
                (pagerState.currentPage + 1).coerceAtMost(total)
            }
        }
    }

    // Save reading progress on page change (paged modes; webtoon continuous scroll saves the
    // chapter actually on screen, in the effect below).
    LaunchedEffect(currentPage) {
        if (!isWebtoon && currentPage > 0) {
            viewModel.saveProgress(manga.id, chapter.id, chapter.name, currentPage)
        }
    }

    // ---- Continuous scroll (webtoon): previous + current + queued chapters flattened into one
    // strip, so Long strip scrolls BOTH directions (back into earlier chapters and forward into
    // later ones) with no chapter change or reload. ----
    val entries: List<Any> = buildList {
        for (pc in previousChapters.asReversed()) {
            val pp = pc.pages
            if (pp != null) {
                add(DividerItem(pc.chapter))
                addAll(pp)
            } else {
                add(LoadingItem(pc.chapter, pc.error))
            }
        }
        val cur = pages
        if (cur != null) {
            if (previousChapters.isNotEmpty()) add(DividerItem(chapter))
            addAll(cur)
        }
        for (qc in queuedChapters) {
            val qp = qc.pages
            if (qp != null) {
                add(DividerItem(qc.chapter))
                addAll(qp)
            } else {
                add(LoadingItem(qc.chapter, qc.error))
            }
        }
    }

    // Where each chapter's pages start inside [entries], so the HUD/progress can map the scroll
    // position back to a chapter + page-in-chapter (divider/loading items shift page indices, hence
    // tracking start offsets in the real item list).
    val pageRanges: List<PageRange> = buildList {
        var idx = 0
        for (pc in previousChapters.asReversed()) {
            val pp = pc.pages
            if (pp != null) {
                idx += 1 // divider item occupies one slot
                add(PageRange(pc.chapter, idx, pp.size)); idx += pp.size
            } else {
                idx += 1 // loading item occupies one slot
            }
        }
        val cur = pages
        if (cur != null) {
            if (previousChapters.isNotEmpty()) idx += 1 // divider before the current chapter
            add(PageRange(chapter, idx, cur.size)); idx += cur.size
        }
        for (qc in queuedChapters) {
            val qp = qc.pages
            if (qp != null) {
                idx += 1 // divider item occupies one slot
                add(PageRange(qc.chapter, idx, qp.size)); idx += qp.size
            } else {
                idx += 1 // loading item occupies one slot
            }
        }
    }

    val firstVisible = listState.firstVisibleItemIndex
    val visibleRange = pageRanges.lastOrNull { firstVisible >= it.start } ?: pageRanges.firstOrNull()
    val pageInChapter = visibleRange?.let { (firstVisible - it.start + 1).coerceIn(1, it.count) } ?: 1
    val activeChapter = visibleRange?.chapter

    val lastVisibleEntry by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    }

    // Stable reading order for the whole chapter list. Extension sources that leave the -1 default
    // chapter number (TheBlank) can't be meaningfully sorted by number, so fall back to upload
    // date. Powers the chapter sheet, prev/next navigation and continuous scroll.
    val orderedChapters = remember(allChapters) {
        if (allChapters.any { it.chapterNumber > 0f }) {
            allChapters.sortedBy { it.chapterNumber }
        } else {
            allChapters.sortedWith(compareBy<ChapterEntity> { it.dateUpload }.thenBy { it.name })
        }
    }

    fun nextChapterAfter(c: ChapterEntity): ChapterEntity? {
        val idx = orderedChapters.indexOfFirst { it.id == c.id }
        return if (idx >= 0 && idx < orderedChapters.size - 1) orderedChapters[idx + 1] else null
    }

    fun prevChapterBefore(c: ChapterEntity): ChapterEntity? {
        val idx = orderedChapters.indexOfFirst { it.id == c.id }
        return if (idx > 0) orderedChapters[idx - 1] else null
    }

    // Append the next chapter when the reader scrolls near the end of the loaded pages.
    LaunchedEffect(isWebtoon, lastVisibleEntry, entries.size, queuedChapters.size) {
        // Hard cap so a jump near the end can't queue every remaining chapter at once (each would
        // spawn a network load and block threads — the whole app freezes/lags).
        if (queuedChapters.size >= MAX_QUEUED_CHAPTERS) return@LaunchedEffect
        if (lastVisibleEntry < entries.size - 3) return@LaunchedEffect
        val tail = queuedChapters.lastOrNull()
        // "Settled" = loaded OR failed. A failed chapter must not block continuous scroll forever.
        val tailSettled = if (tail == null) pages != null else (tail.pages != null || tail.error != null)
        if (!tailSettled) return@LaunchedEffect
        val lastCh = tail?.chapter ?: chapter
        val next = nextChapterAfter(lastCh) ?: return@LaunchedEffect
        val cid = next.id
        queuedChapters.add(QueuedCh(next, null, null))
        val qi = queuedChapters.size - 1
        coroutineScope.launch {
            try {
                val p = withTimeout(QUEUED_LOAD_TIMEOUT_MS) { viewModel.repository.getChapterPageImageModels(cid) }
                val cur = queuedChapters.getOrNull(qi)
                if (cur != null && cur.chapter.id == cid) queuedChapters[qi] = QueuedCh(next, p, null)
            } catch (e: Throwable) {
                val cur = queuedChapters.getOrNull(qi)
                if (cur != null && cur.chapter.id == cid) queuedChapters[qi] = QueuedCh(next, null, e.describe())
            }
        }
    }

    // Prepend the PREVIOUS chapter when the reader scrolls near the top of the strip, so Long
    // strip scrolls back into earlier chapters too (not just forward), with no chapter change.
    // Once a chapter's pages have loaded they stay in the list, so scrolling up and down across
    // chapters never re-triggers a reload — that "loading again" behaviour is exactly what made
    // webtoon scrolling up feel broken.
    LaunchedEffect(isWebtoon, firstVisible, entries.size, previousChapters.size, prependingPrevious) {
        if (!isWebtoon) return@LaunchedEffect
        if (prependingPrevious) return@LaunchedEffect
        if (previousChapters.size >= MAX_QUEUED_CHAPTERS) return@LaunchedEffect
        if (firstVisible > 3) return@LaunchedEffect
        val head = previousChapters.firstOrNull()
        val headSettled = if (head == null) pages != null else (head.pages != null || head.error != null)
        if (!headSettled) return@LaunchedEffect
        val firstCh = head?.chapter ?: chapter
        val prev = prevChapterBefore(firstCh) ?: return@LaunchedEffect
        val pid = prev.id
        // Capture the viewport before the insert so we can restore it once the pages arrive —
        // prepending shifts every item index down, and without compensation the reader would jump
        // away from the page you were looking at.
        val savedIndex = listState.firstVisibleItemIndex
        val savedOffset = listState.firstVisibleItemScrollOffset
        prependingPrevious = true
        previousChapters.add(0, QueuedCh(prev, null, null))
        val pi = previousChapters.indexOfFirst { it.chapter.id == pid }
        coroutineScope.launch {
            try {
                val p = withTimeout(QUEUED_LOAD_TIMEOUT_MS) { viewModel.repository.getChapterPageImageModels(pid) }
                val cur = previousChapters.getOrNull(pi)
                if (cur != null && cur.chapter.id == pid) {
                    previousChapters[pi] = QueuedCh(prev, p, null)
                    // The loading row became a divider + N pages. The FIRST prepend also makes a
                    // divider appear before the current chapter (it had none while the strip held
                    // only the current chapter), hence the extra +1 only when it's the first.
                    val addedCurDiv = if (previousChapters.size == 1) 1 else 0
                    val target = (savedIndex + 1 + p.size + addedCurDiv).coerceAtLeast(0)
                    // Restore the viewport to the page that was on screen before the prepend —
                    // but only if the reader is still near the top. If the user scrolled away
                    // while the previous chapter's pages were loading, don't yank them back.
                    if (listState.firstVisibleItemIndex < target + 3) {
                        listState.scrollToItem(target, savedOffset)
                    }
                }
            } catch (e: Throwable) {
                val cur = previousChapters.getOrNull(pi)
                if (cur != null && cur.chapter.id == pid) previousChapters[pi] = QueuedCh(prev, null, e.describe())
            } finally {
                prependingPrevious = false
            }
        }
    }

    // Save progress + mark earlier chapters read as the reader crosses into queued chapters.
    if (isWebtoon) {
        val vis = visibleRange
        LaunchedEffect(vis?.chapter?.id, pageInChapter) {
            val v = vis
            if (v != null && pageInChapter > 0) {
                viewModel.saveProgress(manga.id, v.chapter.id, v.chapter.name, pageInChapter)
                if (v.chapter.id != chapter.id) {
                    viewModel.markPreviousChaptersRead(manga.id, v.chapter.chapterNumber)
                }
            }
        }
    }

    // Preload pages ahead of the viewport (like Tadami's preload manager): by the time an item
    // scrolls into view its bytes are already downloaded AND decoded, so continuous scroll never
    // sits on a spinner. Must use the SAME size as the display request (screenW/webtoonDecodeH)
    // so Coil's cache/in-flight coalescing reuses the result — a different size decodes a second,
    // full-height bitmap, which is what blew up memory (freeze/crash) and made pages re-download.
    LaunchedEffect(isWebtoon, firstVisible, entries.size) {
        if (!isWebtoon || entries.isEmpty()) return@LaunchedEffect
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: firstVisible
        val visibleCount = info.visibleItemsInfo.size.coerceAtLeast(1)
        val start = last + 1
        val end = minOf(start + visibleCount + PRELOAD_PAGES, entries.size)
        for (i in start until end) {
            val m = entries.getOrNull(i) ?: continue
            if (m is DividerItem || m is LoadingItem) continue
            imageLoader?.enqueue(
                ImageRequest.Builder(context)
                    .data(m)
                    .size(screenW, webtoonDecodeH)
                    .crossfade(false)
                    .build()
            )
        }
    }

    // Download-ahead (Aniyomi/Tadami page cache): pull EVERY page of the loaded strip into the
    // on-device reader_pages cache in the background, a few at a time, so a page is already on
    // disk by the time it scrolls into view and renders instantly — no network spinner, no
    // re-download on scroll-back, and no memory spike (raw bytes on disk vs full-size bitmaps in
    // RAM, which is what made scrolling heavy). The reader's page fetcher serves from this cache
    // first, so this is disk-only prefetching. Already-cached pages are skipped, so re-running
    // when a queued chapter's pages arrive is cheap.
    // Downloads are ordered from the CURRENT reading position outward (ahead first, then behind):
    // opening a chapter resumes mid-way, and prefetching from page 1 meant every page the reader
    // actually showed was still on the network — the "stuck on a spinner while scrolling" problem.
    // Re-runs every ~24 pages of scroll so a jump (slider/next-chapter) re-centres the downloads.
    val prefetchBucket = if (isWebtoon) listState.firstVisibleItemIndex / 24 else pagerState.currentPage / 24
    LaunchedEffect(chapter.id, isWebtoon, entries.size, pages?.size, prefetchBucket) {
        if (pages == null) return@LaunchedEffect
        // (position index in the strip, chapterId, pageUrl, imageUrl)
        val targets = buildList {
            fun addChapter(cid: String, list: List<Any>?) {
                list?.forEach { m ->
                    when (m) {
                        is ExtensionPageImage -> add(Triple(cid, m.pageUrl, m.imageUrl))
                        is String -> add(Triple(cid, "", m))
                    }
                }
            }
            addChapter(chapter.id, pages)
            queuedChapters.forEach { addChapter(it.chapter.id, it.pages) }
            previousChapters.forEach { addChapter(it.chapter.id, it.pages) }
        }
        if (targets.isEmpty()) return@LaunchedEffect
        val cur = if (isWebtoon) listState.firstVisibleItemIndex.coerceAtLeast(0) else pagerState.currentPage.coerceAtLeast(0)
        val ordered = buildList {
            targets.forEachIndexed { i, t ->
                if (i == cur) return@forEachIndexed // the on-screen page is already being loaded
                add(Pair(t, if (i >= cur) (i - cur) else Int.MAX_VALUE / 2 + (cur - i)))
            }
        }.sortedBy { it.second }.map { it.first }
        prefetchJob.value?.cancel()
        prefetchJob.value = prefetchScope.launch {
            withContext(Dispatchers.IO) {
                for ((cid, pUrl, iUrl) in ordered) {
                    prefetchGate.withPermit {
                        runCatching { viewModel.repository.getPageImageFile(cid, pUrl, iUrl) }
                    }
                }
            }
        }
    }

    // Aniyomi-style "load everything": also pull the NEXT chapter's pages into the page cache so
    // crossing the chapter boundary is seamless too (the strip's own queuing fetches its page list
    // again when it's actually reached, which the source's page-list cache makes instant). Runs once
    // per chapter, only if the strip hasn't queued a next chapter yet. Shares the same download gate
    // as the current chapter so combined prefetch concurrency stays safe.
    LaunchedEffect(chapter.id, isWebtoon, queuedChapters.size) {
        if (!isWebtoon) return@LaunchedEffect
        if (queuedChapters.isNotEmpty()) return@LaunchedEffect
        val lastCh = queuedChapters.lastOrNull()?.chapter ?: chapter
        val next = nextChapterAfter(lastCh) ?: return@LaunchedEffect
        val cid = next.id
        val list = runCatching {
            withTimeout(QUEUED_LOAD_TIMEOUT_MS) { viewModel.repository.getChapterPageImageModels(cid) }
        }.getOrNull() ?: return@LaunchedEffect
        val targets = list.mapNotNull { m ->
            when (m) {
                is ExtensionPageImage -> Triple(cid, m.pageUrl, m.imageUrl)
                is String -> Triple(cid, "", m)
                else -> null
            }
        }
        if (targets.isEmpty()) return@LaunchedEffect
        nextPrefetchJob.value?.cancel()
        nextPrefetchJob.value = prefetchScope.launch {
            withContext(Dispatchers.IO) {
                for ((ch, pUrl, iUrl) in targets) {
                    prefetchGate.withPermit {
                        runCatching { viewModel.repository.getPageImageFile(ch, pUrl, iUrl) }
                    }
                }
            }
        }
    }

    val prevChapter = remember(orderedChapters, chapter) { prevChapterBefore(chapter) }

    val nextChapter = remember(orderedChapters, chapter) { nextChapterAfter(chapter) }

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
        // Reader Content (loading / error / pages)
        when {
            pageLoading || pages == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = contentTextColor)
                        Text(
                            text = "Loading pages...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = contentTextColor)
                        )
                    }
                }
            }

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
                val pageList = pages!!
                if (isWebtoon) {
                    // Per-page loading state for the COMPOSED (visible) items only. The spinner is
                    // shown on at most the first two pages that are still loading, so wherever you
                    // scroll in a long chapter there's always 1-2 loading indicators but never one
                    // on every unloaded page (the rest keep a dark placeholder).
                    val pageLoadState = remember { mutableStateMapOf<Int, Boolean>() }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(entries.size) { i ->
                            when (val item = entries[i]) {
                                is DividerItem -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        HorizontalDivider(
                                            modifier = Modifier.fillMaxWidth(0.7f),
                                            color = contentTextColor.copy(alpha = 0.25f)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "End of ${item.chapter.name}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = contentTextColor.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = "Continuing into the next chapter…",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = contentTextColor.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(
                                            modifier = Modifier.fillMaxWidth(0.7f),
                                            color = contentTextColor.copy(alpha = 0.25f)
                                        )
                                    }
                                }
                                is LoadingItem -> {
                                    if (item.error != null) {
                                        Text(
                                            text = "Couldn't load ${item.chapter.name} — tap to retry",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = contentTextColor.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp)
                                                .clickable { retryQueuedChapter(item.chapter.id) }
                                        )
                                    } else {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 28.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = contentTextColor
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Loading ${item.chapter.name}…",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = contentTextColor.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    val retries = pageRetries[i] ?: 0
                                    // Track this page as loading while it's composed; cleanup on
                                    // scroll-away keeps the frontier limited to visible pages.
                                    DisposableEffect(i) {
                                        pageLoadState[i] = true
                                        onDispose { pageLoadState.remove(i) }
                                    }
                                    val frontier = pageLoadState.filterValues { it }.keys.sorted().take(2)
                                    key(item, retries) {
                                        // Memoize the request per page (keyed on the page object +
                                        // retry count) so it's the SAME object across scroll
                                        // recompositions. Coil's rememberAsyncImagePainter keys on
                                        // the model — a fresh ImageRequest every recomposition made
                                        // it restart the download on every scroll tick, leaving
                                        // pages stuck on spinners, hammering the network (freeze)
                                        // and blowing up memory (crash).
                                        val model = remember(item, retries) {
                                            ImageRequest.Builder(context)
                                                .data(item)
                                                .size(screenW, webtoonDecodeH)
                                                .crossfade(false)
                                                .build()
                                        }
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(
                                                    if (readerMode == ReaderMode.WEBTOON_GAPS) {
                                                        Modifier.padding(bottom = 16.dp)
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                        ) {
                                            LoadableReaderImage(
                                                stableKey = item,
                                                model = model,
                                                contentDescription = "Page ${i + 1}",
                                                contentScale = ContentScale.FillWidth,
                                                zoom = if (readerFit == ReaderFit.FIT_WIDTH) 1.2f else 1f,
                                                spinnerColor = contentTextColor,
                                                onError = { msg ->
                                                    pageImageErrors = pageImageErrors + (i to msg)
                                                },
                                                onRetry = {
                                                    pageImageErrors = pageImageErrors - i
                                                    pageRetries[i] = (pageRetries[i] ?: 0) + 1
                                                },
                                                // At most a couple of loading circles (the user
                                                // asked): the first two pages that are STILL
                                                // loading get a spinner; the rest of the loading
                                                // pages keep a dark placeholder.
                                                showSpinner = i in frontier,
                                                onLoadingChanged = { loading -> pageLoadState[i] = loading },
                                                // A loading page keeps a viewport-height
                                                // placeholder (like Tadami) so the list stays
                                                // scrollable and doesn't jump when each image
                                                // finishes loading.
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = webtoonPlaceholderH)
                                                    .clipToBounds()
                                                    .testTag("reader_page_$i")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val pageContent: @Composable (Int) -> Unit = { pageIndex ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val pageUrl = pageList[pageIndex]
                            val retries = pageRetries[pageIndex] ?: 0
                            key(pageUrl, retries) {
                                // Sized + memoized request: paged pages were decoded at full
                                // resolution before — tall pages exceeded the GPU texture limit
                                // (black band) and ate memory. Same cap as webtoon, so the request
                                // is also reused via Coil's cache when flipping back.
                                val model = remember(pageUrl, retries) {
                                    ImageRequest.Builder(context)
                                        .data(pageUrl)
                                        .size(screenW, webtoonDecodeH)
                                        .crossfade(false)
                                        .build()
                                }
                                Box(modifier = Modifier.fillMaxSize()) {
                                    LoadableReaderImage(
                                        stableKey = pageUrl,
                                        model = model,
                                        contentDescription = "Page ${pageIndex + 1}",
                                        contentScale = when (readerFit) {
                                            ReaderFit.FIT_WIDTH -> ContentScale.Fit
                                            ReaderFit.FIT_HEIGHT -> ContentScale.FillHeight
                                            ReaderFit.FIT -> ContentScale.Fit
                                        },
                                        zoom = if (readerFit == ReaderFit.FIT_WIDTH) 1.2f else 1f,
                                        spinnerColor = contentTextColor,
                                        onError = { msg ->
                                            pageImageErrors = pageImageErrors + (pageIndex to msg)
                                        },
                                        onRetry = {
                                            pageImageErrors = pageImageErrors - pageIndex
                                            pageRetries[pageIndex] = (pageRetries[pageIndex] ?: 0) + 1
                                        },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clipToBounds()
                                            .testTag("reader_page_$pageIndex")
                                    )
                                }
                            }
                        }
                    }
                    if (readerMode == ReaderMode.VERTICAL) {
                        VerticalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { pageContent(it) }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            reverseLayout = readerMode == ReaderMode.RIGHT_TO_LEFT,
                            modifier = Modifier.fillMaxSize()
                        ) { pageContent(it) }
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
            // Compact Tadami-style glass top bar: translucent, full-width, minimal height.
            // Tapping the empty parts falls through to the reader's tap handler (hide/show HUD).
            Surface(
                color = Color(0x99101622),
                contentColor = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(40.dp).testTag("reader_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = manga.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = activeChapter?.name ?: chapter.name,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray),
                            maxLines = 1
                        )
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.size(40.dp).testTag("reader_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Reader Settings", tint = Color.White)
                    }
                }
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
                color = Color(0x99101622),
                contentColor = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { prevChapter?.let { onChapterChange(it.id) } },
                            enabled = prevChapter != null,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NavigateBefore,
                                contentDescription = "Previous Chapter",
                                tint = if (prevChapter != null) Color.White else Color.Gray
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isWebtoon && activeChapter != null) {
                                    val n = activeChapter.chapterNumber
                                    val prefix = if (n > 0f) "Ch. ${formatChapterNum(n)} • " else ""
                                    "$prefix Page $pageInChapter / ${visibleRange?.count ?: 0}"
                                } else {
                                    "Page $currentPage / ${pages?.size ?: 0}"
                                },
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                modifier = Modifier.testTag("page_indicator_text")
                            )

                            if (pageImageErrors.isNotEmpty()) {
                                Text(
                                    text = "${pageImageErrors.size} img failed",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF5252)
                                    ),
                                    modifier = Modifier.testTag("page_errors_count")
                                )
                            }
                        }

                        IconButton(
                            onClick = { nextChapter?.let { onChapterChange(it.id) } },
                            enabled = nextChapter != null,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NavigateNext,
                                contentDescription = "Next Chapter",
                                tint = if (nextChapter != null) Color.White else Color.Gray
                            )
                        }
                    }

                    // Reader toolbar (mirrors Tadami's): chapter list, fit mode, rotation lock.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ReaderToolButton(Icons.Default.FormatListBulleted, "Chapters") { showChaptersSheet = true }
                        ReaderToolButton(
                            icon = Icons.Default.AspectRatio,
                            label = if (readerFit == ReaderFit.FIT_WIDTH) "Normal" else "Fit Width"
                        ) {
                            viewModel.setReaderFit(
                                if (readerFit == ReaderFit.FIT_WIDTH) ReaderFit.FIT else ReaderFit.FIT_WIDTH
                            )
                        }
                        ReaderToolButton(
                            icon = Icons.Default.ScreenRotation,
                            label = if (rotationLocked) "Unlock" else "Landscape"
                        ) { toggleRotation() }
                    }

                    Slider(
                        // During a drag the thumb follows the finger (sliderDragPage); the actual
                        // scroll happens ONCE on release. Per-tick scrollToItem calls stormed the
                        // list state, corrupted it, and left the reader stuck on endless loading
                        // icons for any source.
                        value = sliderDragPage ?: if (isWebtoon) pageInChapter.toFloat() else currentPage.toFloat(),
                        onValueChange = { sliderDragPage = it },
                        onValueChangeFinished = {
                            val p = sliderDragPage
                            sliderDragPage = null
                            if (p != null) {
                                val targetPage = p.toInt() - 1
                                coroutineScope.launch {
                                    if (isWebtoon) {
                                        val maxIdx = (entries.size - 1).coerceAtLeast(0)
                                        listState.scrollToItem(((visibleRange?.start ?: 0) + targetPage).coerceIn(0, maxIdx))
                                    } else {
                                        pagerState.scrollToPage(targetPage.coerceIn(0, (pages?.size ?: 1) - 1))
                                    }
                                }
                            }
                        },
                        valueRange = 1f..(
                            if (isWebtoon)
                                (visibleRange?.count ?: 1).coerceAtLeast(1).toFloat()
                            else
                                (pages?.size ?: 1).coerceAtLeast(1).toFloat()
                        ),
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
                Column {
                    Text(
                        text = "Reading Mode",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeTile(
                            mode = ReaderMode.WEBTOON,
                            label = "Long strip",
                            selected = readerMode == ReaderMode.WEBTOON,
                            onClick = { viewModel.setReaderMode(ReaderMode.WEBTOON) },
                            modifier = Modifier.weight(1f)
                        )
                        ModeTile(
                            mode = ReaderMode.WEBTOON_GAPS,
                            label = "Long strip\nwith gaps",
                            selected = readerMode == ReaderMode.WEBTOON_GAPS,
                            onClick = { viewModel.setReaderMode(ReaderMode.WEBTOON_GAPS) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeTile(
                            mode = ReaderMode.LEFT_TO_RIGHT,
                            label = "Paged\n(left to right)",
                            selected = readerMode == ReaderMode.LEFT_TO_RIGHT,
                            onClick = { viewModel.setReaderMode(ReaderMode.LEFT_TO_RIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                        ModeTile(
                            mode = ReaderMode.RIGHT_TO_LEFT,
                            label = "Paged\n(right to left)",
                            selected = readerMode == ReaderMode.RIGHT_TO_LEFT,
                            onClick = { viewModel.setReaderMode(ReaderMode.RIGHT_TO_LEFT) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeTile(
                            mode = ReaderMode.VERTICAL,
                            label = "Paged\n(vertical)",
                            selected = readerMode == ReaderMode.VERTICAL,
                            onClick = { viewModel.setReaderMode(ReaderMode.VERTICAL) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Reader Background",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

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
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (showChaptersSheet) {
        ModalBottomSheet(onDismissRequest = { showChaptersSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Chapters",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
                val activeId = activeChapter?.id ?: chapter.id
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    items(orderedChapters, key = { it.id }) { c ->
                        val isCurrent = c.id == activeId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showChaptersSheet = false
                                    onChapterChange(c.id)
                                }
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                                )
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = c.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (c.read) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Read",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    webviewTarget?.let { (url, ua) ->
        WebViewDialog(
            url = url,
            userAgent = ua,
            onDismiss = { webviewTarget = null }
        )
    }
}

@Composable
private fun ReaderToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(15.dp), tint = Color.White)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
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

@Composable
private fun ModeTile(
    mode: ReaderMode,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(2.dp, accent) else null,
        modifier = modifier.height(92.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ModeIcon(
                mode = mode,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Small page-layout glyphs for the reading-mode picker: two side-by-side pages (paged LTR/RTL),
// one short page with a down-arrow (paged vertical), a full-height continuous strip (long strip)
// and a strip with a visible gap between pages (long strip with gaps).
@Composable
private fun ModeIcon(mode: ReaderMode, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val stroke = s * 0.085f

        fun page(x: Float, y: Float, w: Float, h: Float) {
            drawRect(color = tint, topLeft = Offset(x, y), size = Size(w, h), style = Stroke(width = stroke))
        }

        fun chevron(x: Float, y: Float, dx: Float, dy: Float) {
            val l = s * 0.15f
            drawLine(tint, Offset(x, y - l), Offset(x + dx, y), strokeWidth = stroke)
            drawLine(tint, Offset(x + dx, y), Offset(x, y + l), strokeWidth = stroke)
        }

        when (mode) {
            ReaderMode.LEFT_TO_RIGHT -> {
                page(s * 0.14f, s * 0.28f, s * 0.30f, s * 0.44f)
                page(s * 0.56f, s * 0.28f, s * 0.30f, s * 0.44f)
                chevron(s * 0.50f, s * 0.50f, s * 0.07f, 0f)
            }
            ReaderMode.RIGHT_TO_LEFT -> {
                page(s * 0.14f, s * 0.28f, s * 0.30f, s * 0.44f)
                page(s * 0.56f, s * 0.28f, s * 0.30f, s * 0.44f)
                chevron(s * 0.50f, s * 0.50f, -s * 0.07f, 0f)
            }
            ReaderMode.VERTICAL -> {
                page(s * 0.14f, s * 0.22f, s * 0.72f, s * 0.34f)
                chevron(s * 0.50f, s * 0.70f, 0f, s * 0.07f)
            }
            ReaderMode.WEBTOON -> {
                page(s * 0.14f, s * 0.14f, s * 0.72f, s * 0.72f)
            }
            ReaderMode.WEBTOON_GAPS -> {
                page(s * 0.14f, s * 0.14f, s * 0.72f, s * 0.28f)
                page(s * 0.14f, s * 0.58f, s * 0.72f, s * 0.28f)
            }
        }
    }
}

private data class QueuedCh(
    val chapter: ChapterEntity,
    val pages: List<Any>?,
    val error: String?,
)

private data class DividerItem(val chapter: ChapterEntity)

private data class LoadingItem(val chapter: ChapterEntity, val error: String?)

private data class PageRange(val chapter: ChapterEntity, val start: Int, val count: Int)

private fun formatChapterNum(n: Float): String =
    if (n % 1f == 0f) n.toInt().toString() else n.toString()

// Timeout for the first chapter's page list (must also cover a Cloudflare silent solve).
private const val MAIN_LOAD_TIMEOUT_MS = 60_000L

// Continuous-scroll queued chapters: shorter timeout (a stalled queued chapter shows a retry row
// instead of an endless spinner) and a hard cap so a jump to the end can't queue every remaining
// chapter at once (that froze/lagged the whole app). Generous enough for sources whose page-list
// call does a session/secret-stream handshake (TheBlank) — a too-tight timeout surfaced error rows
// and made the reader feel broken mid-scroll.
private const val QUEUED_LOAD_TIMEOUT_MS = 45_000L
private const val MAX_QUEUED_CHAPTERS = 8

// Webtoon: how many items past the current viewport to preload (like Tadami's preload window).
// Kept modest so the higher-resolution decodes don't run too many at once.
private const val PRELOAD_PAGES = 3

/**
 * A reader page image with its own loading spinner and tap-to-retry error state (like Tadami).
 *
 * Plain painter + [Image] rather than SubcomposeAsyncImage: the subcomposed variant hangs / ANRs
 * inside a LazyColumn when the loading slot's size differs from the loaded image, leaving pages
 * stuck on eternal spinners. A spinner is drawn as an overlay so the page keeps a minimum height
 * while loading and the list stays scrollable.
 */
@Composable
private fun LoadableReaderImage(
    stableKey: Any,
    model: Any,
    contentDescription: String,
    contentScale: ContentScale,
    spinnerColor: Color,
    onError: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    showSpinner: Boolean = true,
    onLoadingChanged: ((Boolean) -> Unit)? = null,
) {
    // stableKey is the source model object (stable across recompositions); `model` may be a fresh
    // ImageRequest wrapper each recomposition, so never key state on it.
    var loading by remember(stableKey) { mutableStateOf(true) }
    var failed by remember(stableKey) { mutableStateOf(false) }
    val painter = rememberAsyncImagePainter(
        model = model,
        onState = { state ->
            val isNowLoading = state is AsyncImagePainter.State.Empty || state is AsyncImagePainter.State.Loading
            loading = isNowLoading
            failed = state is AsyncImagePainter.State.Error
            onLoadingChanged?.invoke(isNowLoading)
            if (state is AsyncImagePainter.State.Error) {
                onError(state.result.throwable.message ?: "image load failed")
            }
        }
    )
    Box(modifier = modifier) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .then(if (zoom != 1f) Modifier.scale(zoom) else Modifier),
            contentScale = contentScale
        )
        if (loading && showSpinner) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = spinnerColor
                )
            }
        }
        if (failed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x33000000))
                    .clickable(onClick = onRetry),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Page failed — tap to retry",
                    style = MaterialTheme.typography.bodySmall,
                    color = spinnerColor.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
