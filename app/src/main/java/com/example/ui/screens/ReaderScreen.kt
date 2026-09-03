package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import com.example.data.reader.TileBitmapCache
import com.example.data.reader.WebtoonFileCache
import com.example.data.reader.WebtoonTile
import com.example.data.reader.webtoonSliceCount
import com.example.data.source.ExtensionPageImage
import com.example.data.source.MangaSource
import com.example.ui.MainViewModel
import com.example.ui.ReaderBg
import com.example.ui.ReaderFit
import com.example.ui.ReaderMode
import com.example.ui.ReaderOrientation
import com.example.ui.looksLikeCloudflare
import com.example.util.sortChapters
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.limitedParallelism
import kotlinx.coroutines.withContext

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

    // Display width drives the tile-slicing math (strip aspect ratio -> slice count at this width).
    val screenWidthPx = LocalContext.current.resources.displayMetrics.widthPixels

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

    // Opening a chapter never "starts reading it": merely opening one used to save whatever page
    // the reader transiently showed as progress (and mark the chapter read), so a never-read
    // chapter could come back marked "read at page 5/8/10" and a chapter last left at page 7
    // could reopen at a random page. Only a chapter the user has actually read resumes where it
    // was left (lastPageRead); everything else starts at page 1. In-reader prev/next chapter
    // navigation always starts at the beginning (startAtBeginning).
    val resumeTargetPage = remember(chapter.id) {
        if (startAtBeginning || !chapter.read) 1
        else chapter.lastPageRead.coerceAtLeast(1)
    }
    val initialPageIndex = resumeTargetPage - 1

    // Webtoon Vertical List State. The list is in TILE units (a page contributes one item as a
    // placeholder and several slice items once its aspect ratio is known), so it must always be
    // created at item 0 — seeding it with a PAGE index used to open mid-list on a random slice of
    // a random page. The effect below scrolls it to the first tile of the resume page once the
    // geometry above that page is known.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 0)

    // Paged Reader State (shared by horizontal + vertical pagers; pager units ARE pages, so the
    // initial page is a real page here).
    val pagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { pages?.size ?: 0 }
    )

    // Force the reader to open on the intended starting page once a chapter's content is on
    // screen. Keying on the chapter id makes the start page reliable on every navigation path.
    // In webtoon mode the resume target is a PAGE, but the list is in TILE units, so we wait for
    // the pages above it to be aspect-resolved (the warm-up resolver is downloading them) and
    // land on the first tile of the resume page. The landing is then re-checked a few times:
    // if any strip above the target still had an unknown ratio when the first scroll ran (an
    // unknown page counts as ONE placeholder item instead of its real slice count), the first
    // landing sits short, and the re-check corrects it once the ratios have arrived and the
    // list has rebuilt into pre-sized tiles.
    LaunchedEffect(chapter.id, pages, isWebtoon, startAtBeginning) {
        val list = pages ?: return@LaunchedEffect
        if (list.isEmpty()) return@LaunchedEffect
        val targetPage = if (startAtBeginning) 0 else initialPageIndex.coerceIn(0, list.lastIndex)
        try {
            if (isWebtoon) {
                if (targetPage > 0) {
                    for (i in 0..50) {
                        var allKnown = true
                        for (p in 0..targetPage) {
                            if (readerAspectRatios[pageUrl(list[p])] == null) {
                                allKnown = false
                                break
                            }
                        }
                        if (allKnown) break
                        delay(100)
                    }
                }
                val index = list.take(targetPage).sumOf { webtoonItemCount(it, screenWidthPx) }
                listState.scrollToItem(index.coerceAtLeast(0))
                if (targetPage > 0) {
                    for (i in 0 until 3) {
                        delay(350)
                        val corrected = list.take(targetPage)
                            .sumOf { webtoonItemCount(it, screenWidthPx) }
                            .coerceAtLeast(0)
                        if (listState.firstVisibleItemIndex == corrected) break
                        listState.scrollToItem(corrected)
                    }
                }
            } else {
                pagerState.scrollToPage(targetPage.coerceAtMost(list.lastIndex))
            }
        } catch (_: Exception) {
        }
    }

    // The chapter currently on screen (in webtoon modes this can advance past the starting chapter).
    // Walked in TILE units: a strip whose recorded aspect ratio is tall enough to slice contributes
    // webtoonSliceCount items, so the current page is found by counting tiles back from the first
    // visible list index (which is a tile index, not a page index).
    val streamPosition by remember {
        derivedStateOf {
            if (isWebtoon && streamSegments.isNotEmpty()) {
                val g = listState.firstVisibleItemIndex
                var seg = 0
                var page = 1
                var cursor = 0
                for (i in streamSegments.indices) {
                    if (i > 0) cursor += 1 // divider item before this segment
                    val segPages = streamSegments[i]
                    val count = segPages.sumOf { webtoonItemCount(it, screenWidthPx) }
                    if (g < cursor + count) {
                        seg = i
                        var remaining = g - cursor
                        page = 1
                        for (model in segPages) {
                            val c = webtoonItemCount(model, screenWidthPx)
                            if (remaining < c) break
                            remaining -= c
                            page += 1
                        }
                        page = page.coerceIn(1, segPages.size.coerceAtLeast(1))
                        break
                    }
                    cursor += count
                }
                Triple(seg, page, streamSegments[seg].size)
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

    // Save reading progress on page / active-chapter change. Three rules keep merely OPENING a
    // chapter from ever corrupting its stored progress — which is what made never-read chapters
    // reopen at random pages 5/8/10 and made resume positions drift:
    //  1. The first sighting of a chapter only anchors the current page and never writes, so a
    //     bare open (or a freshly auto-continued chapter arriving at page 1) can't mark the
    //     chapter "read at" a transient page.
    //  2. A page change inside a chapter is persisted only once the page has held still for a
    //     moment, so pages merely scrolled past never save — the page the reader stops on does.
    //  3. Crossing into a different chapter flushes the one just left (its anchor is its last
    //     settled page, so a fast scroll through a chapter's tail still records where it ended).
    var progressAnchor by remember(chapter.id) { mutableStateOf<Pair<String, Int>?>(null) }
    LaunchedEffect(currentPage, activeChapter.id) {
        if (currentPage <= 0) return@LaunchedEffect
        val anchor = progressAnchor
        if (anchor == null) {
            progressAnchor = activeChapter.id to currentPage
            return@LaunchedEffect
        }
        if (anchor.first != activeChapter.id) {
            if (anchor.second > 1) {
                val name = allChapters.firstOrNull { it.id == anchor.first }?.name ?: ""
                viewModel.saveProgress(manga.id, anchor.first, name, anchor.second)
            }
            progressAnchor = activeChapter.id to currentPage
            return@LaunchedEffect
        }
        if (anchor.second == currentPage) return@LaunchedEffect
        progressAnchor = activeChapter.id to currentPage
        delay(300)
        viewModel.saveProgress(manga.id, activeChapter.id, activeChapter.name, currentPage)
    }

    // Leaving the reader (back button, prev/next navigation, ...) flushes the last page the
    // reader actually reached — progressAnchor tracks every page change instantly, so even an
    // exit mid-scroll records where the user ended up rather than the last paused page.
    DisposableEffect(Unit) {
        onDispose {
            val a = progressAnchor
            if (a != null && a.second > 1) {
                val name = allChapters.firstOrNull { it.id == a.first }?.name ?: ""
                viewModel.saveProgress(manga.id, a.first, name, a.second)
            }
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

    // Tiled webtoon renderer. Every strip is split into <=2048px-tall slices that
    // are region-decoded from the on-disk file as HARDWARE bitmaps (the giant full strip is never
    // materialised, so no slice ever exceeds the GPU's texture size and every draw is cheap). The
    // resolver below keeps the window ahead downloaded and its aspect ratios recorded, so a slice
    // that scrolls into view decodes instantly at its final, pre-sized slot -- no fetch, no decode,
    // no relayout at the cut boundary.
    val context = LocalContext.current
    val density = LocalDensity.current
    // The reader's "Image quality" setting picks the decode width (50/75/100%); tiles are always
    // displayed at full strip width, so decode cost scales with this.
    val webtoonDecodeWidth = (screenWidthPx * readerQuality / 100f).roundToInt().coerceAtLeast(360)
    val source = remember(manga) {
        if (manga == null) null
        else runCatching { viewModel.repository.sourceForManga(manga.id) }.getOrNull()
    }
    val webtoonCacheDir = remember { File(context.cacheDir, "webtoon_tiles") }

    // Per-chapter tiled item list for the stream. Rebuilt whenever an aspect ratio is recorded
    // (each page's ratio is recorded exactly once, so a map-size change means a new ratio → that
    // page's placeholder upgrades into pre-sized tile slices). Keying on the live size — a read in
    // composition scope — is what makes the LazyColumn's item count always match the slice math.
    val streamTiledItems = remember(streamSegments, readerAspectRatios.size, screenWidthPx, source) {
        streamSegments.mapIndexed { segIdx, segPages ->
            buildWebtoonItems(segPages, segIdx, screenWidthPx, readerAspectRatios, source)
        }
    }

    // Download + record aspect ratios for the given pages (bounded concurrency: 3 at a time, so a
    // burst of parallel requests never trips the CDN). Once a page's ratio is recorded the render
    // list slices it into pre-sized tiles; the tile fetcher then region-decodes them from these
    // files on demand.
    suspend fun resolvePages(segPages: List<Any>) {
        val src = source ?: return
        if (segPages.isEmpty()) return
        withContext(Dispatchers.IO) {
            for (chunk in segPages.chunked(3)) {
                if (!isActive) return@withContext
                coroutineScope {
                    chunk.forEach { model ->
                        launch {
                            val url = pageUrl(model)
                            if (readerAspectRatios[url] != null) return@launch
                            val tile = WebtoonTile(
                                imageUrl = url,
                                requestUrl = (model as? ExtensionPageImage)?.pageUrl ?: "",
                                source = src,
                                sliceIndex = 0,
                                sliceCount = 1,
                            )
                            val file = WebtoonFileCache.resolve(tile, webtoonCacheDir) ?: return@launch
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(file.absolutePath, opts)
                            if (opts.outWidth > 0 && opts.outHeight > 0) {
                                readerAspectRatios[url] = opts.outWidth.toFloat() / opts.outHeight
                            }
                        }
                    }
                }
            }
        }
    }

    // Decode-ahead pool: strictly limited to 2 parallel slice decodes, separate from Coil's own
    // pool, so slices are decoded here in the background and are already in TileBitmapCache when
    // they scroll into view — the tile fetcher then returns them instantly instead of
    // region-decoding (and, on a cold file, network-downloading) on the scroll path.
    val decodeAheadDispatcher = remember { Dispatchers.IO.limitedParallelism(2) }
    var decodeAheadBusy by remember(chapter.id) { mutableStateOf(false) }

    // Background-decode the next pages' not-yet-decoded slices. One pass runs at a time; each
    // pass decodes up to 8 slices in scroll order from [fromPage], so a scrolled-into-view tile
    // is a cache hit rather than a decode. Slices whose page ratio is still unknown are skipped
    // (they're re-picked by the next pass once the rolling resolver records the ratio).
    fun decodeAhead(segIdx: Int, fromPage: Int, toPage: Int) {
        val segs = streamSegments
        if (segs.isEmpty()) return
        val src = source ?: return
        val segPages = segs.getOrNull(segIdx) ?: return
        if (segPages.isEmpty()) return
        if (decodeAheadBusy) return
        val from = fromPage.coerceAtLeast(0)
        val to = toPage.coerceAtMost(segPages.lastIndex)
        if (to < from) return
        val pending = ArrayList<WebtoonTile>()
        for (p in from..to) {
            val model = segPages[p]
            val url = pageUrl(model)
            val ratio = readerAspectRatios[url]
            if (ratio == null || ratio <= 0f) continue
            val count = webtoonSliceCount(screenWidthPx, ratio)
            if (count <= 1) continue
            val requestUrl = (model as? ExtensionPageImage)?.pageUrl ?: ""
            for (i in 0 until count) {
                val tile = WebtoonTile(url, requestUrl, src, i, count)
                if (TileBitmapCache.peek(tile, webtoonDecodeWidth) == null) pending += tile
            }
        }
        if (pending.isEmpty()) return
        decodeAheadBusy = true
        coroutineScope.launch {
            try {
                withContext(decodeAheadDispatcher) {
                    for (tile in pending.take(8)) {
                        TileBitmapCache.decode(tile, webtoonCacheDir, webtoonDecodeWidth)
                    }
                }
            } finally {
                decodeAheadBusy = false
            }
        }
    }

    // Latest in-flight jump warm-up job; cancelling the previous one means a fast slider drag only
    // ever runs the warm for the most recent target.
    var warmJob by remember(chapter.id) { mutableStateOf<Job?>(null) }

    // Warm a window around a jump target: download + resolve those pages so the jump lands on
    // already-sliced, already-cached tiles.
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
            resolvePages(segPages.subList(from, to + 1))
        }
    }

    // Resuming into the middle of a long chapter: resolve the FULL prefix up to the resume point
    // (plus a window ahead) so the opening scroll can land on the exact first tile of the resume
    // page — while any strip above it has an unknown ratio it counts as a single placeholder item,
    // so an unresolved prefix is exactly what used to make the resume land a page or three short
    // (5/8/10 instead of the stored page). Those prefix files are on disk from the earlier read,
    // so this is mostly cheap bounds-decode reads.
    LaunchedEffect(pages, chapter.id, isWebtoon, startAtBeginning) {
        if (isWebtoon && pages != null && !startAtBeginning && initialPageIndex > 0) {
            val list = pages!!
            if (list.isEmpty()) return@LaunchedEffect
            val upTo = (initialPageIndex + 6).coerceAtMost(list.lastIndex)
            coroutineScope.launch {
                resolvePages(list.subList(0, upTo + 1))
            }
        }
    }

    // Rolling resolver -- ONE persistent loop for webtoon mode, NOT keyed on the current page.
    // Whenever the scroll window slides it downloads (and aspect-resolves) the pages just behind
    // and well ahead of the current one, then background-decodes the slices just ahead of the
    // current page into TileBitmapCache. Continuous scroll past 100-slice chapters therefore
    // never waits at a slice boundary for a decode (or, worse, a download) on the scroll path.
    LaunchedEffect(pages, streamSegments, isWebtoon, source, webtoonDecodeWidth) {
        if (!isWebtoon) return@LaunchedEffect
        var lastWindowKey = ""
        while (isActive) {
            val segs = streamSegments
            if (segs.isEmpty() || segs.any { it.isEmpty() }) { delay(150); continue }
            val segIdx = streamPosition.first.coerceIn(0, segs.lastIndex)
            val segPages = segs[segIdx]
            if (segPages.isEmpty()) { delay(150); continue }
            val cur = (currentPage - 1).coerceIn(0, segPages.lastIndex)
            val from = (cur - 2).coerceAtLeast(0)
            val to = (cur + 12).coerceAtMost(segPages.lastIndex)
            val key = "$segIdx:$from:$to"
            if (key != lastWindowKey) {
                lastWindowKey = key
                resolvePages(segPages.subList(from, to + 1))
                decodeAhead(segIdx, cur, cur + 6)
            }
            delay(150)
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
                                val tiled = streamTiledItems.getOrNull(segIdx)
                                if (tiled != null) {
                                    itemsIndexed(
                                        tiled,
                                        key = { _, it -> it.key }
                                    ) { _, item ->
                                        when (item) {
                                            is WebtoonListItem.Slice -> WebtoonTileView(
                                                item = item,
                                                decodeWidthPx = webtoonDecodeWidth,
                                                fade = webtoonFade,
                                                spinnerColor = contentTextColor,
                                                testTag = "reader_page_${segIdx}_${item.pageIndex}"
                                            )
                                            is WebtoonListItem.Placeholder -> WebtoonPagePlaceholder(
                                                spinnerColor = contentTextColor,
                                                testTag = "reader_page_${segIdx}_${item.pageIndex}"
                                            )
                                        }
                                    }
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
                                    var index = streamSegments.take(seg).sumOf { s ->
                                        s.sumOf { webtoonItemCount(it, screenWidthPx) }
                                    } + seg
                                    val segPages = streamSegments[seg]
                                    val t = targetPage.coerceIn(0, segPages.lastIndex)
                                    index += segPages.take(t).sumOf { webtoonItemCount(it, screenWidthPx) }
                                    listState.scrollToItem(index)
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

// Aspect ratio (width/height) of each webtoon page, recorded when the resolver decodes it. The
// item builder reads this at composition so every strip's slots are pre-sized to its real height
// up front — the list never relayouts as pages finish loading. Snapshot-backed so recording a
// ratio recomposes just that slot.
private val readerAspectRatios = mutableStateMapOf<String, Float>()

// Stable identity for a reader page (its image URL for extension pages, its string otherwise).
private fun pageUrl(model: Any): String = (model as? ExtensionPageImage)?.imageUrl ?: model.toString()

// Number of LazyColumn items one page contributes: 1 while its aspect ratio is unknown (a
// placeholder), otherwise the engine slice count at the current display width. This is the single
// source of truth shared by the item builder, the scroll-position walk and the slider jump math,
// so all three always agree on where each tile sits in the list.
private fun webtoonItemCount(model: Any, screenWidthPx: Int): Int {
    val ratio = readerAspectRatios[pageUrl(model)]
    if (ratio == null || ratio <= 0f) return 1
    return webtoonSliceCount(screenWidthPx, ratio)
}

// One flattened webtoon item for the stream's LazyColumn. A page whose aspect ratio is known is
// expanded into one pre-sized Slice per tile; while it's still unknown it renders as a Placeholder
// that upgrades once the resolver records its ratio.
private sealed interface WebtoonListItem {
    val key: String
    val pageModel: Any
    val pageIndex: Int

    data class Slice(
        override val key: String,
        override val pageModel: Any,
        override val pageIndex: Int,
        val requestUrl: String,
        val source: MangaSource,
        val ratio: Float,
        val sliceIndex: Int,
        val sliceCount: Int,
    ) : WebtoonListItem

    data class Placeholder(
        override val key: String,
        override val pageModel: Any,
        override val pageIndex: Int,
    ) : WebtoonListItem
}

private fun buildWebtoonItems(
    segPages: List<Any>,
    segIdx: Int,
    screenWidthPx: Int,
    ratios: Map<String, Float>,
    source: MangaSource?,
): List<WebtoonListItem> {
    val items = ArrayList<WebtoonListItem>()
    segPages.forEachIndexed { pi, model ->
        val url = pageUrl(model)
        val ratio = ratios[url]
        if (ratio == null || ratio <= 0f || source == null) {
            items += WebtoonListItem.Placeholder(
                key = "s${segIdx}_p$pi",
                pageModel = model,
                pageIndex = pi,
            )
        } else {
            val count = webtoonSliceCount(screenWidthPx, ratio)
            val requestUrl = (model as? ExtensionPageImage)?.pageUrl ?: ""
            for (i in 0 until count) {
                items += WebtoonListItem.Slice(
                    key = "s${segIdx}_p${pi}_t$i",
                    pageModel = model,
                    pageIndex = pi,
                    requestUrl = requestUrl,
                    source = source,
                    ratio = ratio,
                    sliceIndex = i,
                    sliceCount = count,
                )
            }
        }
    }
    return items
}

/**
 * One tile of a webtoon strip, rendered by Coil from a [WebtoonTile] model. The fetcher
 * region-decodes just this slice out of the on-disk file as a HARDWARE bitmap (the giant strip is
 * never materialised), and the slot is pre-sized from the strip's recorded aspect ratio — so the
 * tile paints at its final size with no fetch, no decode, no relayout at the slice boundary. A
 * failed tile auto-retries like the paged reader.
 */
@Composable
private fun WebtoonTileView(
    item: WebtoonListItem.Slice,
    decodeWidthPx: Int,
    fade: Boolean,
    spinnerColor: Color,
    testTag: String
) {
    val context = LocalContext.current
    var attempt by remember(item.key) { mutableStateOf(0) }
    var gaveUp by remember(item.key) { mutableStateOf(false) }
    var retrying by remember(item.key) { mutableStateOf(false) }

    // After a failed attempt: pause briefly, then bump the attempt counter to trigger a fresh
    // request (or mark the tile as given-up once the retry budget is spent).
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

    val tile = remember(item) {
        WebtoonTile(
            imageUrl = pageUrl(item.pageModel),
            requestUrl = item.requestUrl,
            source = item.source,
            sliceIndex = item.sliceIndex,
            sliceCount = item.sliceCount,
        )
    }
    val request = remember(item, attempt, decodeWidthPx) {
        ImageRequest.Builder(context)
            .data(tile)
            .size(Size(Dimension.Pixels(decodeWidthPx), Dimension.Undefined))
            // Tile bitmaps are owned by TileBitmapCache (decode-ahead fills it before tiles
            // scroll in); Coil's own memory cache is disabled so a bitmap is never held twice,
            // and the fetcher's cache hit makes every (re)request effectively free.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .apply { if (fade) crossfade(true) }
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

    // Pre-sized from the strip's recorded aspect ratio: slice height = full strip height / count,
    // so this tile's slot is exactly the height its decoded region will occupy.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(item.ratio * item.sliceCount)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painter,
            contentDescription = "Page ${item.pageIndex + 1}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillWidth
        )
        if (state is AsyncImagePainter.State.Error && gaveUp) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Couldn't load page",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = spinnerColor.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

// Slot shown while a page's aspect ratio is still unknown (the resolver records it moments later,
// upgrading this placeholder into pre-sized tile slices).
@Composable
private fun WebtoonPagePlaceholder(
    spinnerColor: Color,
    testTag: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(600.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = spinnerColor.copy(alpha = 0.6f),
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp)
        )
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
