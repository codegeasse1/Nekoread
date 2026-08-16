package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
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
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.example.data.local.ChapterEntity
import com.example.data.local.MangaEntity
import com.example.data.source.MangaSource
import com.example.ui.MainViewModel
import com.example.ui.ReaderBg
import com.example.ui.ReaderFit
import com.example.ui.ReaderMode
import com.example.ui.reader.TadamiPage
import com.example.ui.reader.decodeImageBounds
import com.example.util.dedupeChapters
import com.example.util.describe
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

    var pages by remember { mutableStateOf<List<MangaSource.PageDescriptor>?>(null) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var pageLoading by remember { mutableStateOf(true) }
    var retryKey by remember { mutableStateOf(0) }
    var pageImageErrors by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    val pageRetries = remember { mutableStateMapOf<Int, Int>() }
    // Per-page download state (keyed by image URL). The reader streams every page through the
    // source's own client into the on-device reader_pages cache (Tadami HttpPageLoader model),
    // then renders it with the tiled SubsamplingScaleImageView — so first view downloads once,
    // every re-read after that is instant, and no full-page bitmap is ever decoded.
    val pageStates = remember(chapter.id) { mutableStateMapOf<String, PageFileState>() }
    // Reads hit the CDN a couple at a time so we never saturate a host (a constant download storm
    // made the CDN throttle every request to the host, including pages actually on screen).
    val downloadGate = remember { Semaphore(3) }
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
                viewModel.repository.getChapterPageDescriptors(chapter.id)
            }
        } catch (e: Throwable) {
            pageError = e.describe()
            pages = null
        } finally {
            pageLoading = false
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // Chapter downloads (opt-in, Aniyomi/Tadami model). The reader STREAMS pages on demand — like
    // Tadami online reading — and downloads happen only when the user taps the download button on
    // a chapter, a couple at a time, into the on-device reader_pages cache. Always-on download-ahead
    // had to go: it fired a constant stream of requests at the CDN, which throttled EVERY request
    // to the host (including the pages actually on screen), turning fast loads into 20-30s hangs.
    val chapterDownloads = remember { mutableStateMapOf<String, Float>() } // chapterId -> 0..1 progress

    fun startChapterDownload(c: ChapterEntity) {
        val cur = chapterDownloads[c.id]
        if (cur != null && cur >= 1f) return
        if (cur != null && cur < 1f) return // already downloading
        chapterDownloads[c.id] = 0f
        coroutineScope.launch {
            val list = runCatching {
                withTimeout(QUEUED_LOAD_TIMEOUT_MS) { viewModel.repository.getChapterPageDescriptors(c.id) }
            }.getOrNull() ?: run { chapterDownloads.remove(c.id); return@launch }
            val targets = list.map { Triple(c.id, it.pageUrl, it.imageUrl) }
            if (targets.isEmpty()) { chapterDownloads.remove(c.id); return@launch }
            withContext(Dispatchers.IO) {
                val gate = Semaphore(2)
                var done = 0
                for ((cid, pUrl, iUrl) in targets) {
                    gate.withPermit {
                        runCatching { viewModel.repository.getPageImageFile(cid, pUrl, iUrl) }
                        done++
                        chapterDownloads[c.id] = done.toFloat() / targets.size
                    }
                }
            }
            chapterDownloads[c.id] = 1f
        }
    }

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
                        viewModel.repository.getChapterPageDescriptors(qc.chapter.id)
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
                    viewModel.repository.getChapterPageDescriptors(pc.chapter.id)
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

    // Display size used to size webtoon items to each page's real aspect ratio before SSIV renders.
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current

    // Stop the source's background page-list prefetches when the reader leaves, so nothing keeps
    // hammering the CDN. (No Coil memory-cache eviction needed anymore: the reader no longer caches
    // page bitmaps — SSIV keeps only the tiles on screen and frees them when pages scroll away.)
    DisposableEffect(Unit) {
        onDispose {
            HttpSource.cancelAllPrefetches()
        }
    }

    val screenW = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenH = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    // Loading placeholder height (px): a decent fraction of the viewport so the strip stays
    // scrollable while a page's bytes download. Once the file is on disk the item is resized to
    // the page's true aspect ratio, so there's no permanent layout mismatch.
    val webtoonPlaceholderH = (screenH * 0.4f).toInt().coerceAtLeast(160)
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
                addAll(pp.map { PageItem(pc.chapter, it) })
            } else {
                add(LoadingItem(pc.chapter, pc.error))
            }
        }
        val cur = pages
        if (cur != null) {
            if (previousChapters.isNotEmpty()) add(DividerItem(chapter))
            addAll(cur.map { PageItem(chapter, it) })
        }
        for (qc in queuedChapters) {
            val qp = qc.pages
            if (qp != null) {
                add(DividerItem(qc.chapter))
                addAll(qp.map { PageItem(qc.chapter, it) })
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

    // Stable reading order for the whole chapter list. Duplicate chapters from the same release
    // (a source's mirror sites/scanlators) collapse into one entry, so continuous scroll /
    // prev-next advance 1→2→3 instead of replaying chapter 1 once per source. Extension sources
    // that leave the -1 default chapter number (TheBlank) can't be meaningfully sorted by number,
    // so fall back to upload date. Powers the chapter sheet, prev/next navigation and continuous
    // scroll.
    val orderedChapters = remember(allChapters) {
        val deduped = dedupeChapters(allChapters)
        if (deduped.any { it.chapterNumber > 0f }) {
            deduped.sortedBy { it.chapterNumber }
        } else {
            deduped.sortedWith(compareBy<ChapterEntity> { it.dateUpload }.thenBy { it.name })
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
                val p = withTimeout(QUEUED_LOAD_TIMEOUT_MS) { viewModel.repository.getChapterPageDescriptors(cid) }
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
                val p = withTimeout(QUEUED_LOAD_TIMEOUT_MS) { viewModel.repository.getChapterPageDescriptors(pid) }
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

    // Shared page-file prefetcher (Tadami's preload manager): downloads a page's bytes through the
    // source's own client into the reader_pages cache AND marks it Ready (with decoded bounds), so
    // when the page actually scrolls into view it renders from disk instantly — no spinner, no
    // "loads from the top down to where I am". Skips pages that are already downloaded or already
    // downloading (the on-screen item's own effect owns those). Gated by the same 3-at-a-time
    // semaphore as the reader, so prefetching can never starve the page actually on screen.
    val prefetchPage: suspend (String, String, String) -> Unit = { cid, pUrl, iUrl ->
        val key = iUrl
        if (pageStates[key] !is PageFileState.Ready && pageStates[key] !is PageFileState.Loading) {
            downloadGate.withPermit {
                try {
                    val f = viewModel.repository.getPageImageFile(cid, pUrl, iUrl)
                    val (w, h) = withContext(Dispatchers.IO) { decodeImageBounds(f) }
                    pageStates[key] = PageFileState.Ready(f, w, h)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // A failed prefetch is non-fatal — the page's own item shows the error state.
                }
            }
        }
    }

    // Webtoon: preload a window on BOTH sides of the viewport, so scrolling forward, scrolling
    // back up, and slider-jumping all hit already-cached pages instead of waiting on downloads.
    // Forward first (the usual direction), then behind.
    LaunchedEffect(isWebtoon, firstVisible, entries.size) {
        if (!isWebtoon || entries.isEmpty()) return@LaunchedEffect
        val info = listState.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull()?.index ?: firstVisible
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: first
        val visibleCount = info.visibleItemsInfo.size.coerceAtLeast(1)
        val aheadEnd = minOf(last + visibleCount + PRELOAD_PAGES, entries.size)
        val behindStart = (first - visibleCount - PRELOAD_PAGES).coerceAtLeast(0)
        for (i in (last + 1) until aheadEnd) {
            (entries.getOrNull(i) as? PageItem)?.let { prefetchPage(it.chapter.id, it.desc.pageUrl, it.desc.imageUrl) }
        }
        for (i in behindStart until first) {
            (entries.getOrNull(i) as? PageItem)?.let { prefetchPage(it.chapter.id, it.desc.pageUrl, it.desc.imageUrl) }
        }
    }

    // Paged modes: preload a bounded window ahead of the current page. Flipping or slider-jumping
    // to a page renders it the instant it lands — its bytes are already on disk (the on-screen
    // page's own effect covers the current page). Same gate, so it never starves the visible page.
    LaunchedEffect(isWebtoon, pagerState.currentPage, pages?.size) {
        if (isWebtoon) return@LaunchedEffect
        val pl = pages ?: return@LaunchedEffect
        if (pl.isEmpty()) return@LaunchedEffect
        val cur = pagerState.currentPage
        val aheadEnd = minOf(cur + PRELOAD_PAGES, pl.size)
        for (i in (cur + 1) until aheadEnd) {
            val desc = pl[i]
            prefetchPage(chapter.id, desc.pageUrl, desc.imageUrl)
        }
    }

    // Pages stream ON DEMAND through the source's own client into the reader_pages cache (like
    // Tadami online reading): each visible page downloads once and is then served from disk with
    // the tiled SSIV, so re-reads/scroll-back are instant. Deliberately NO full-chapter background
    // download here — a constant download stream at the CDN made the CDN throttle every request to
    // the host, including the pages actually on screen, turning fast loads into 20-30s hangs.
    // Users who want chapters offline/instant use the download button in the chapter sheet
    // (startChapterDownload).
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // Keep pages around the viewport COMPOSED (not just prefetched): each
                        // composed item's SSIV view stays alive and decoded, and its page bytes
                        // download while it's still off-screen. So a page scrolls into view already
                        // rendered — no blank/decode flash or spinner pop per scroll step, which
                        // is exactly how Tadami's reader feels. Pages leave composition (views
                        // recycled) only after they're beyond this window.
                        beyondBoundsItemCount = PRELOAD_PAGES
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
                                is PageItem -> {
                                    val page = item
                                    val retries = pageRetries[i] ?: 0
                                    val state = pageStates[page.desc.imageUrl]
                                    // Stream this page's bytes through the source's own client into
                                    // the reader_pages cache (Tadami HttpPageLoader model); cached
                                    // pages return instantly, re-reads never hit the network.
                                    // Scroll-away cancels the download cleanly (tmp file cleaned by
                                    // getPageImageFile); scrolling back re-runs this effect.
                                    LaunchedEffect(page.desc.imageUrl, retries, page.chapter.id) {
                                        if (pageStates[page.desc.imageUrl] !is PageFileState.Ready) {
                                            pageStates[page.desc.imageUrl] = PageFileState.Loading
                                            downloadGate.withPermit {
                                                try {
                                                    val f = viewModel.repository.getPageImageFile(page.chapter.id, page.desc.pageUrl, page.desc.imageUrl)
                                                    val (w, h) = withContext(Dispatchers.IO) { decodeImageBounds(f) }
                                                    pageStates[page.desc.imageUrl] = PageFileState.Ready(f, w, h)
                                                    pageImageErrors = pageImageErrors - i
                                                } catch (e: Throwable) {
                                                    if (e is CancellationException) throw e
                                                    val msg = e.message ?: "page download failed"
                                                    pageStates[page.desc.imageUrl] = PageFileState.Failed(msg)
                                                    pageImageErrors = pageImageErrors + (i to msg)
                                                }
                                            }
                                        }
                                    }
                                    // Size the item to the page's true aspect ratio (fit-width) so
                                    // the strip flows seamlessly; a fraction-of-viewport
                                    // placeholder keeps it scrollable while the bytes download.
                                    val ready = state as? PageFileState.Ready
                                    val itemH = if (ready != null && ready.width > 0) {
                                        (screenW * ready.height.toFloat() / ready.width).toInt().coerceAtLeast(2)
                                    } else {
                                        webtoonPlaceholderH
                                    }
                                    key(page, retries) {
                                        TadamiPage(
                                            descriptor = page.desc,
                                            file = ready?.file,
                                            error = (state as? PageFileState.Failed)?.message,
                                            isWebtoon = true,
                                            scaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
                                            spinnerColor = contentTextColor,
                                            onRetry = {
                                                pageImageErrors = pageImageErrors - i
                                                pageRetries[i] = (pageRetries[i] ?: 0) + 1
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height((itemH / density.density).dp)
                                                .then(
                                                    if (readerMode == ReaderMode.WEBTOON_GAPS) {
                                                        Modifier.padding(bottom = 16.dp)
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .clipToBounds()
                                                .testTag("reader_page_$i")
                                        )
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
                            val desc = pageList[pageIndex]
                            val retries = pageRetries[pageIndex] ?: 0
                            val state = pageStates[desc.imageUrl]
                            LaunchedEffect(desc.imageUrl, retries) {
                                if (pageStates[desc.imageUrl] !is PageFileState.Ready) {
                                    pageStates[desc.imageUrl] = PageFileState.Loading
                                    downloadGate.withPermit {
                                        try {
                                            val f = viewModel.repository.getPageImageFile(chapter.id, desc.pageUrl, desc.imageUrl)
                                            val (w, h) = withContext(Dispatchers.IO) { decodeImageBounds(f) }
                                            pageStates[desc.imageUrl] = PageFileState.Ready(f, w, h)
                                            pageImageErrors = pageImageErrors - pageIndex
                                        } catch (e: Throwable) {
                                            if (e is CancellationException) throw e
                                            val msg = e.message ?: "page download failed"
                                            pageStates[desc.imageUrl] = PageFileState.Failed(msg)
                                            pageImageErrors = pageImageErrors + (pageIndex to msg)
                                        }
                                    }
                                }
                            }
                            key(desc, retries) {
                                TadamiPage(
                                    descriptor = desc,
                                    file = (state as? PageFileState.Ready)?.file,
                                    error = (state as? PageFileState.Failed)?.message,
                                    isWebtoon = false,
                                    scaleType = when (readerFit) {
                                        ReaderFit.FIT_WIDTH -> SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH
                                        ReaderFit.FIT_HEIGHT -> SubsamplingScaleImageView.SCALE_TYPE_FIT_HEIGHT
                                        ReaderFit.FIT -> SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
                                    },
                                    spinnerColor = contentTextColor,
                                    onTap = { showHud = !showHud },
                                    onSwipePage = { forward ->
                                        coroutineScope.launch {
                                            val target = (pagerState.currentPage + (if (forward) 1 else -1))
                                                .coerceIn(0, pageList.lastIndex)
                                            pagerState.animateScrollToPage(target)
                                        }
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
                    if (readerMode == ReaderMode.VERTICAL) {
                        VerticalPager(
                            state = pagerState,
                            beyondViewportPageCount = 2,
                            modifier = Modifier.fillMaxSize()
                        ) { pageContent(it) }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            reverseLayout = readerMode == ReaderMode.RIGHT_TO_LEFT,
                            beyondViewportPageCount = 2,
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
                            val dlProgress = chapterDownloads[c.id]
                            when {
                                dlProgress != null && dlProgress >= 1f -> Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                dlProgress != null -> CircularProgressIndicator(
                                    progress = { dlProgress },
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                else -> IconButton(
                                    onClick = { startChapterDownload(c) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download chapter",
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
    val pages: List<MangaSource.PageDescriptor>?,
    val error: String?,
)

private data class DividerItem(val chapter: ChapterEntity)

private data class LoadingItem(val chapter: ChapterEntity, val error: String?)

private data class PageItem(val chapter: ChapterEntity, val desc: MangaSource.PageDescriptor)

private data class PageRange(val chapter: ChapterEntity, val start: Int, val count: Int)

/** Per-page download state in the reader: bytes fetched through the source into reader_pages. */
private sealed interface PageFileState {
    object Loading : PageFileState
    data class Ready(val file: File, val width: Int, val height: Int) : PageFileState
    data class Failed(val message: String) : PageFileState
}

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

// Preload window size (both sides of the viewport in webtoon, ahead in paged): how many items
// past/around the current viewport are fetched ahead of time. Generous so scrolling — in either
// direction or via a slider jump — lands on pages that are already on disk.
private const val PRELOAD_PAGES = 6
