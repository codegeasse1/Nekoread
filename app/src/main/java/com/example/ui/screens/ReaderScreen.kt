package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.net.Uri
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.example.data.local.ChapterEntity
import com.example.data.local.MangaEntity
import com.example.data.source.MangaSource
import com.example.util.describe
import com.example.ui.MainViewModel
import com.example.ui.ReaderBg
import com.example.ui.ReaderFit
import com.example.ui.ReaderMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

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

    val chapterPages = remember { mutableStateListOf<PageItem>() }
    var pageError by remember { mutableStateOf<String?>(null) }
    var pageLoading by remember { mutableStateOf(true) }
    var retryKey by remember { mutableStateOf(0) }
    // Slider thumb while the user is dragging it; the actual scroll happens once on release so a
    // drag can't fire a storm of conflicting scrollToItem calls into unloaded content.
    var sliderDragPage by remember { mutableStateOf<Float?>(null) }
    // Bumped when a failed page is tapped to retry, so the download coordinator rescans and
    // re-downloads the reset items.
    var downloadTick by remember { mutableStateOf(0) }

    // Continuous scroll (webtoon): chapters queued after the current one, appended automatically
    // as the reader reaches the end. The first chapter's pages live in [chapterPages]; every queued
    // chapter is a (chapter, loaded-pages-or-null, error-or-null) entry.
    val queuedChapters = remember { mutableStateListOf<QueuedCh>() }

    LaunchedEffect(chapter.id, retryKey) {
        pageLoading = true
        pageError = null
        queuedChapters.clear()
        chapterPages.clear()
        try {
            val descriptors = withTimeout(MAIN_LOAD_TIMEOUT_MS) {
                viewModel.repository.getChapterPageDescriptors(chapter.id)
            }
            chapterPages.addAll(descriptors.toPageItems(chapter.id))
        } catch (e: Throwable) {
            pageError = e.describe()
        } finally {
            pageLoading = false
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // Re-load a queued chapter's pages after a failure (tapped from its error row). A failed
    // queued chapter never blocks continuous scroll — the reader just shows the retry row.
    fun retryQueuedChapter(chapterId: String) {
        val i = queuedChapters.indexOfFirst { it.chapter.id == chapterId }
        if (i < 0) return
        val qc = queuedChapters[i]
        queuedChapters[i] = QueuedCh(qc.chapter, null, null)
        coroutineScope.launch {
            try {
                val p = withTimeout(QUEUED_LOAD_TIMEOUT_MS) {
                    viewModel.repository.getChapterPageDescriptors(qc.chapter.id)
                }
                val cur = queuedChapters.getOrNull(i)
                if (cur != null && cur.chapter.id == qc.chapter.id) {
                    queuedChapters[i] = QueuedCh(qc.chapter, p.toPageItems(qc.chapter.id), null)
                }
            } catch (e: Throwable) {
                val cur = queuedChapters.getOrNull(i)
                if (cur != null && cur.chapter.id == qc.chapter.id) {
                    queuedChapters[i] = QueuedCh(qc.chapter, null, e.describe())
                }
            }
        }
    }

    // Rotation lock: pin to landscape on the activity when the user taps the toolbar button.
    val activity = LocalContext.current as? Activity

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
            pageCount = { chapterPages.size }
        )
    }

    val currentPage by remember {
        derivedStateOf {
            val total = chapterPages.size
            if (total == 0) {
                0
            } else if (readerMode == ReaderMode.WEBTOON) {
                (listState.firstVisibleItemIndex + 1).coerceAtMost(total)
            } else {
                (pagerState.currentPage + 1).coerceAtMost(total)
            }
        }
    }

    // Save reading progress on page change (paged modes; webtoon continuous scroll saves the
    // chapter actually on screen, in the effect below).
    LaunchedEffect(currentPage) {
        if (readerMode != ReaderMode.WEBTOON && currentPage > 0) {
            viewModel.saveProgress(manga.id, chapter.id, chapter.name, currentPage)
        }
    }

    // ---- Continuous scroll (webtoon): pages + queued chapters flattened into one list ----
    val entries: List<Any> = buildList {
        if (chapterPages.isNotEmpty()) addAll(chapterPages)
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
        if (chapterPages.isNotEmpty()) {
            add(PageRange(chapter, idx, chapterPages.size)); idx += chapterPages.size
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
    LaunchedEffect(lastVisibleEntry, entries.size, queuedChapters.size) {
        // Hard cap so a jump near the end can't queue every remaining chapter at once (each would
        // spawn a network load and block threads — the whole app freezes/lags).
        if (queuedChapters.size >= MAX_QUEUED_CHAPTERS) return@LaunchedEffect
        if (lastVisibleEntry < entries.size - 3) return@LaunchedEffect
        val tail = queuedChapters.lastOrNull()
        // "Settled" = loaded OR failed. A failed chapter must not block continuous scroll forever.
        val tailSettled = if (tail == null) chapterPages.isNotEmpty() else (tail.pages != null || tail.error != null)
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
                if (cur != null && cur.chapter.id == cid) queuedChapters[qi] = QueuedCh(next, p.toPageItems(cid), null)
            } catch (e: Throwable) {
                val cur = queuedChapters.getOrNull(qi)
                if (cur != null && cur.chapter.id == cid) queuedChapters[qi] = QueuedCh(next, null, e.describe())
            }
        }
    }

    // Save progress + mark earlier chapters read as the reader crosses into queued chapters.
    if (readerMode == ReaderMode.WEBTOON) {
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

    // ---- Page download coordinator (Tadami's HttpPageLoader + preload window) ----
    // Downloads page image BYTES (through the source's own client, so hotlink protection headers
    // are honoured) for the items near the viewport, a few at a time, into a disk cache. The
    // renderer then decodes only the visible region from the file — no giant full-page bitmap is
    // ever decoded, so pages stay native-resolution (no blur) with bounded memory (no freeze).
    // Webtoon: downloads the window around the visible items. Paged: the current page + a few
    // ahead, as the pager moves.
    val downloadSemaphore = remember { Semaphore(2) }
    LaunchedEffect(firstVisible, currentPage, entries.size, downloadTick) {
        if (entries.isEmpty()) return@LaunchedEffect
        val start: Int
        val end: Int
        if (readerMode == ReaderMode.WEBTOON) {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: firstVisible
            val visibleCount = info.visibleItemsInfo.size.coerceAtLeast(1)
            start = (firstVisible - 1).coerceAtLeast(0)
            end = minOf(last + visibleCount + PRELOAD_PAGES, entries.size)
        } else {
            val page = (currentPage - 1).coerceAtLeast(0)
            start = page
            end = minOf(page + 1 + PRELOAD_PAGES, entries.size)
        }
        for (i in start until end) {
            val item = entries.getOrNull(i) as? PageItem ?: continue
            if (item.done || item.downloading) continue
            item.downloading = true
            coroutineScope.launch {
                try {
                    downloadSemaphore.withPermit {
                        val f = viewModel.repository.getPageImageFile(item.chapterId, item.pageUrl, item.imageUrl)
                        // Read the image bounds BEFORE exposing the file so the webtoon list can
                        // lock each page to its real aspect ratio from the first frame (no layout
                        // jump / band when a page finishes loading).
                        if (item.pageW == 0) {
                            withContext(Dispatchers.IO) {
                                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(f.absolutePath, opts)
                                if (opts.outWidth > 0 && opts.outHeight > 0) {
                                    item.pageW = opts.outWidth
                                    item.pageH = opts.outHeight
                                }
                            }
                        }
                        item.file = f
                        item.error = null
                    }
                } catch (e: Throwable) {
                    item.error = e.describe()
                } finally {
                    item.done = true
                    item.downloading = false
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
            .testTag("reader_container")
    ) {
        // Reader Content (loading / error / pages)
        when {
            pageLoading || chapterPages.isEmpty() -> {
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
                if (readerMode == ReaderMode.WEBTOON) {
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
                                    val page = item as PageItem
                                    key(page.imageUrl) {
                                        // Tadami-style page: the bytes are already downloaded by
                                        // the coordinator above, and SubsamplingScaleImageView
                                        // decodes only the visible region at full resolution, so
                                        // pages are always sharp and never blow memory.
                                        SubsamplingReaderImage(
                                            item = page,
                                            spinnerColor = contentTextColor,
                                            scaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
                                            onTap = { showHud = !showHud },
                                            onRetry = { downloadTick++ },
                                            webtoon = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("reader_page_$i")
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val pageContent: @Composable (Int) -> Unit = { pageIndex ->
                        val page = chapterPages[pageIndex]
                        key(page.imageUrl) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                SubsamplingReaderImage(
                                    item = page,
                                    spinnerColor = contentTextColor,
                                    scaleType = when (readerFit) {
                                        ReaderFit.FIT_WIDTH -> SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH
                                        ReaderFit.FIT_HEIGHT -> SubsamplingScaleImageView.SCALE_TYPE_FIT_HEIGHT
                                        ReaderFit.FIT -> SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
                                    },
                                    onTap = { showHud = !showHud },
                                    onRetry = { downloadTick++ },
                                    lockAspect = false,
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
                                text = activeChapter?.name ?: chapter.name,
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

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (readerMode == ReaderMode.WEBTOON && activeChapter != null) {
                                    val n = activeChapter.chapterNumber
                                    val prefix = if (n > 0f) "Ch. ${formatChapterNum(n)} • " else ""
                                    "$prefix Page $pageInChapter / ${visibleRange?.count ?: 0}"
                                } else {
                                    "Page $currentPage / ${chapterPages.size}"
                                },
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                modifier = Modifier.testTag("page_indicator_text")
                            )
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

                    // Reader toolbar (mirrors Tadami's): chapter list, fit mode, rotation lock.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ReaderToolButton(Icons.Default.FormatListBulleted, "Chapters") { showChaptersSheet = true }
                        if (readerMode != ReaderMode.WEBTOON) {
                            ReaderToolButton(
                                icon = Icons.Default.AspectRatio,
                                label = when (readerFit) {
                                    ReaderFit.FIT_WIDTH -> "Fit Width"
                                    ReaderFit.FIT_HEIGHT -> "Fit Height"
                                    ReaderFit.FIT -> "Fit Screen"
                                }
                            ) {
                                viewModel.setReaderFit(
                                    when (readerFit) {
                                        ReaderFit.FIT_WIDTH -> ReaderFit.FIT_HEIGHT
                                        ReaderFit.FIT_HEIGHT -> ReaderFit.FIT
                                        ReaderFit.FIT -> ReaderFit.FIT_WIDTH
                                    }
                                )
                            }
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
                        value = sliderDragPage ?: if (readerMode == ReaderMode.WEBTOON) pageInChapter.toFloat() else currentPage.toFloat(),
                        onValueChange = { sliderDragPage = it },
                        onValueChangeFinished = {
                            val p = sliderDragPage
                            sliderDragPage = null
                            if (p != null) {
                                val targetPage = p.toInt() - 1
                                coroutineScope.launch {
                                    if (readerMode == ReaderMode.WEBTOON) {
                                        val maxIdx = (entries.size - 1).coerceAtLeast(0)
                                        listState.scrollToItem(((visibleRange?.start ?: 0) + targetPage).coerceIn(0, maxIdx))
                                    } else {
                                        pagerState.scrollToPage(targetPage.coerceIn(0, chapterPages.size.coerceAtLeast(1) - 1))
                                    }
                                }
                            }
                        },
                        valueRange = 1f..(
                            if (readerMode == ReaderMode.WEBTOON)
                                (visibleRange?.count ?: 1).coerceAtLeast(1).toFloat()
                            else
                                chapterPages.size.coerceAtLeast(1).toFloat()
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

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setReaderMode(ReaderMode.WEBTOON) }
                    ) {
                        RadioButton(
                            selected = readerMode == ReaderMode.WEBTOON,
                            onClick = { viewModel.setReaderMode(ReaderMode.WEBTOON) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Webtoon")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setReaderMode(ReaderMode.LEFT_TO_RIGHT) }
                    ) {
                        RadioButton(
                            selected = readerMode == ReaderMode.LEFT_TO_RIGHT,
                            onClick = { viewModel.setReaderMode(ReaderMode.LEFT_TO_RIGHT) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Left to Right")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setReaderMode(ReaderMode.RIGHT_TO_LEFT) }
                    ) {
                        RadioButton(
                            selected = readerMode == ReaderMode.RIGHT_TO_LEFT,
                            onClick = { viewModel.setReaderMode(ReaderMode.RIGHT_TO_LEFT) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Right to Left")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setReaderMode(ReaderMode.VERTICAL) }
                    ) {
                        RadioButton(
                            selected = readerMode == ReaderMode.VERTICAL,
                            onClick = { viewModel.setReaderMode(ReaderMode.VERTICAL) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vertical")
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
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
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

private data class QueuedCh(
    val chapter: ChapterEntity,
    val pages: SnapshotStateList<PageItem>?,
    val error: String?,
)

/**
 * One reader page in the Tadami pipeline: its bytes are downloaded (through the source's own
 * client, into a disk cache) by the download coordinator, then rendered by a tiled view that
 * decodes only the visible region at full resolution — never a giant full-page bitmap.
 */
class PageItem(
    val chapterId: String,
    val pageUrl: String,
    val imageUrl: String,
) {
    var file by mutableStateOf<File?>(null)
    var pageW by mutableStateOf(0)
    var pageH by mutableStateOf(0)
    var error by mutableStateOf<String?>(null)
    var done by mutableStateOf(false)
    var downloading by mutableStateOf(false)
}

private fun List<MangaSource.PageDescriptor>.toPageItems(chapterId: String): SnapshotStateList<PageItem> =
    mutableStateListOf<PageItem>().apply {
        addAll(map { PageItem(chapterId, it.pageUrl, it.imageUrl) })
    }

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
private const val PRELOAD_PAGES = 6

/**
 * A reader page rendered the Tadami way: the image bytes are already on disk (downloaded by the
 * coordinator through the source's own client), and [SubsamplingScaleImageView] decodes only the
 * visible region at full resolution, in tiles. No giant full-page bitmap is ever decoded — pages
 * are always native-resolution (never blurry) with bounded memory (never a freeze/crash).
 *
 * - Webtoon: scale type FIT_WIDTH, height locked to the image aspect (from the downloaded file's
 *   bounds) so the list never jumps; panning + zoom off so drags scroll the list (the library
 *   releases touch interception when pan is disabled, exactly like Mihon's webtoon reader).
 * - Paged: fills the screen; scale type follows the reader fit setting; panning on so a zoomed
 *   page can be moved around, while page swipes pass through via the library's edge-swipe logic.
 * - A single tap toggles the HUD (the library fires performClick() on single-tap-confirmed).
 */
@Composable
private fun SubsamplingReaderImage(
    item: PageItem,
    spinnerColor: Color,
    scaleType: Int,
    onTap: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    lockAspect: Boolean = true,
    webtoon: Boolean = false,
) {
    val file = item.file
    if (file != null) {
        val ratio = if (item.pageW > 0 && item.pageH > 0) item.pageW.toFloat() / item.pageH.toFloat() else null
        AndroidView(
            factory = { ctx ->
                SubsamplingScaleImageView(ctx).apply {
                    setMinimumScaleType(scaleType)
                    setMinimumDpi(1)
                    if (webtoon) {
                        // Scroll-only pages: panning off lets drags reach the LazyColumn (the view
                        // releases touch interception when pan is disabled, exactly like Mihon's
                        // webtoon reader), and zoom off keeps the strip as one continuous scroll.
                        setPanEnabled(false)
                        setZoomEnabled(false)
                        setQuickScaleEnabled(false)
                    } else {
                        // Paged: panning on so a zoomed page can be moved around; page swipes pass
                        // through via the library's edge-swipe logic.
                        setPanEnabled(true)
                        setQuickScaleEnabled(true)
                    }
                    // Single-tap on the page toggles the HUD, like Tadami.
                    setOnClickListener { onTap() }
                }
            },
            update = { view ->
                val f = item.file
                if (f != null && view.sWidth == 0) {
                    view.setImage(ImageSource.uri(view.context, Uri.fromFile(f)))
                }
            },
            modifier = modifier.then(
                if (lockAspect) {
                    if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.heightIn(min = 200.dp)
                } else {
                    Modifier
                }
            )
        )
    } else if (item.error != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
                .clickable {
                    item.error = null
                    item.done = false
                    onRetry()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Page failed — tap to retry",
                style = MaterialTheme.typography.bodySmall,
                color = spinnerColor.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
                color = spinnerColor
            )
        }
    }
}
