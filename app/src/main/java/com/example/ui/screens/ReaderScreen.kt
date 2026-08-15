package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import coil.request.ImageRequest
import com.example.data.local.ChapterEntity
import com.example.data.local.MangaEntity
import com.example.util.describe
import com.example.ui.MainViewModel
import com.example.ui.ReaderBg
import com.example.ui.ReaderFit
import com.example.ui.ReaderMode
import kotlinx.coroutines.launch
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

    LaunchedEffect(chapter.id, retryKey) {
        pageLoading = true
        pageError = null
        pageImageErrors = emptyMap()
        queuedChapters.clear()
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
    }

    // Display size + image loader used to downsample reader pages to screen width (much cheaper to
    // decode than full-resolution, which is what made webtoon scrolling lag).
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val imageLoader = LocalImageLoader.current
    val screenW = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    // Height budget for decoding webtoon pages: cap so an extremely tall page can't decode into a
    // ~65MB bitmap (which, stacked across visible pages, blew up memory → freeze + crash). Coil
    // fits the decode inside this box; pages taller than ~3 screens are downscaled slightly.
    val screenH = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val webtoonDecodeH = (screenH * 3).coerceAtLeast(1200)
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
        val cur = pages
        if (cur != null) addAll(cur)
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
        val cur = pages
        if (cur != null) {
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
    LaunchedEffect(lastVisibleEntry, entries.size, queuedChapters.size) {
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

    // Preload the next few pages (webtoon) so scrolling ahead doesn't wait on the network.
    LaunchedEffect(firstVisible, entries.size) {
        if (readerMode != ReaderMode.WEBTOON || entries.isEmpty()) return@LaunchedEffect
        val loader = imageLoader
        val start = firstVisible.coerceIn(0, entries.size - 1)
        val end = minOf(start + 6, entries.size - 1)
        for (i in start..end) {
            val m = entries[i]
            if (m is DividerItem || m is LoadingItem) continue
            loader?.enqueue(
                ImageRequest.Builder(context).data(m).size(screenW, Int.MAX_VALUE).crossfade(false).build()
            )
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
                                    val retries = pageRetries[i] ?: 0
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
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            LoadableReaderImage(
                                                stableKey = item,
                                                model = model,
                                                contentDescription = "Page ${i + 1}",
                                                contentScale = ContentScale.FillWidth,
                                                spinnerColor = contentTextColor,
                                                onError = { msg ->
                                                    pageImageErrors = pageImageErrors + (i to msg)
                                                },
                                                onRetry = {
                                                    pageImageErrors = pageImageErrors - i
                                                    pageRetries[i] = (pageRetries[i] ?: 0) + 1
                                                },
                                                // A loading page keeps a minimum height (with a
                                                // spinner, like Tadami) so the list stays scrollable
                                                // instead of collapsing to zero height and getting
                                                // stuck mid-chapter.
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 200.dp)
                                                    .testTag("reader_page_$i")
                                            )
                                        }
                                    }
                                }
                            }
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
                            val pageUrl = pageList[pageIndex]
                            val retries = pageRetries[pageIndex] ?: 0
                            key(pageUrl, retries) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    LoadableReaderImage(
                                        stableKey = pageUrl,
                                        model = pageUrl,
                                        contentDescription = "Page ${pageIndex + 1}",
                                        contentScale = if (readerFit == ReaderFit.FIT_WIDTH) ContentScale.FillWidth else ContentScale.Fit,
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
                                label = if (readerFit == ReaderFit.FIT_WIDTH) "Fit Width" else "Fit Screen"
                            ) {
                                viewModel.setReaderFit(
                                    if (readerFit == ReaderFit.FIT_WIDTH) ReaderFit.FIT else ReaderFit.FIT_WIDTH
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
                                        pagerState.scrollToPage(targetPage.coerceIn(0, (pages?.size ?: 1) - 1))
                                    }
                                }
                            }
                        },
                        valueRange = 1f..(
                            if (readerMode == ReaderMode.WEBTOON)
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
                        Text("Webtoon (Continuous Vertical)")
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
                        Text("Manga Left-to-Right")
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
                        Text("Manga Right-to-Left (Traditional)")
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
// chapter at once (that froze/lagged the whole app).
private const val QUEUED_LOAD_TIMEOUT_MS = 25_000L
private const val MAX_QUEUED_CHAPTERS = 8

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
) {
    // stableKey is the source model object (stable across recompositions); `model` may be a fresh
    // ImageRequest wrapper each recomposition, so never key state on it.
    var loading by remember(stableKey) { mutableStateOf(true) }
    var failed by remember(stableKey) { mutableStateOf(false) }
    val painter = rememberAsyncImagePainter(
        model = model,
        onState = { state ->
            loading = state is AsyncImagePainter.State.Empty || state is AsyncImagePainter.State.Loading
            failed = state is AsyncImagePainter.State.Error
            if (state is AsyncImagePainter.State.Error) {
                onError(state.result.throwable.message ?: "image load failed")
            }
        }
    )
    Box(modifier = modifier) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale
        )
        if (loading) {
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
