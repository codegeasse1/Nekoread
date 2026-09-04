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
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    LaunchedEffect(chapter.id, retryKey, isWebtoon) {
        pageLoading = true
        pageError = null
        try {
            // Webtoon modes fetch page DESCRIPTORS (URLs) and download each page's bytes to a cache
            // file for the subsampling renderer; paged modes keep Coil page-image models.
            pages = if (isWebtoon) {
                viewModel.repository.getChapterPageDescriptors(chapter.id)
            } else {
                viewModel.repository.getChapterPageImageModels(chapter.id)
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
                val p = if (isWebtoon) {
                    viewModel.repository.getChapterPageDescriptors(next.id)
                } else {
                    viewModel.repository.getChapterPageImageModels(next.id)
                }
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
    val imageLoader = LocalImageLoader.current
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
    val pageAspectRatios = remember { mutableStateMapOf<String, Float>() }

    val displayDecodeWidth = if (isWebtoon) webtoonDecodeWidth else screenWidthPx
    val prewarmAfter = if (isWebtoon) 8 else 4
    // Pages whose DISPLAY-size decode has finished (so the window doesn't re-decode them). Keyed on
    // the decode width so changing the image-quality setting re-prewarms at the new size.
    val displayPrewarmed = remember(displayDecodeWidth) { mutableStateMapOf<String, Boolean>() }

    // Webtoon pages whose cache file is ready (or is being fetched) — the prewarm loop and the
    // visible page items share one download per image URL instead of racing each other.
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
    // source.) The loop walks a window around the current page, nearest page first:
    //   - WEBTOON mode: the "prewarm" for each page is making sure its bytes are on disk
    //     (single-flighted via WebtoonPageCache, so the visible item never re-fetches) and learning
    //     its true aspect ratio from the file, so the list reserves the exact slot height up front.
    //     The subsampling view then region-decodes straight from the cache file — no giant
    //     full-height bitmap is ever held in memory (this is what keeps long-strip scrolling
    //     smooth, yomi's approach).
    //   - PAGED mode: the old Coil two-step — RATIO (a 64px decode for far-ahead pages) and DISPLAY
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
            val from = (globCur - 2).coerceAtLeast(0)
            val to = (globCur + prewarmAfter).coerceAtMost(total - 1)

            // Pick the nearest not-yet-processed page in the window.
            var candidate: Triple<Int, Int, Any>? = null
            for (g in from..to) {
                var seg = 0
                while (seg < segs.size && g >= starts[seg] + segs[seg].size) seg++
                if (seg >= segs.size) continue
                val m = segs[seg][g - starts[seg]]
                val inDisplay = g >= globCur - 2
                if (isWebtoon) {
                    val key = pageKey(m)
                    // Skip pages that just failed — the visible item retries on its own; this loop
                    // would otherwise hot-spin on the same failure every 120ms.
                    if (downloadFailed.containsKey(key) && now - downloadFailed[key]!! < 8000) continue
                    val needRatio = !pageAspectRatios.containsKey(key)
                    val needDownload = inDisplay && !webtoonDownloaded.containsKey(key)
                    if (needRatio || needDownload) { candidate = Triple(seg, g, m); break }
                } else {
                    val needRatio = !pageAspectRatios.containsKey(m.toString())
                    val needDisplay = inDisplay && !displayPrewarmed.containsKey(m.toString())
                    if (needRatio || needDisplay) { candidate = Triple(seg, g, m); break }
                }
            }
            if (candidate == null) { delay(120); continue }
            val (_, g, m) = candidate
            val inDisplay = g >= globCur - 2
            try {
                if (isWebtoon) {
                    // Webtoon prewarm = make sure the page's bytes are on disk (single-flighted) and
                    // learn its true aspect ratio from the file. The visible item then just points the
                    // subsampling view at the local file — no giant full-height bitmap, no re-fetch.
                    val key = pageKey(m)
                    if (!webtoonDownloaded.containsKey(key) || !pageAspectRatios.containsKey(key)) {
                        val desc = m as? MangaSource.PageDescriptor
                        if (desc != null && source != null) {
                            WebtoonPageCache.fileFor(desc, source, webtoonCacheDir)
                            webtoonDownloaded[key] = true
                            val d = WebtoonPageCache.dimensions(desc, webtoonCacheDir)
                            if (d != null && d.first > 0 && d.second > 0) {
                                pageAspectRatios[key] = d.first.toFloat() / d.second
                            }
                        } else {
                            // Not a descriptor / no source — nothing to pre-download; mark done so the
                            // loop moves on (the item falls back to its Coil path).
                            webtoonDownloaded[key] = true
                        }
                    }
                } else {
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
                }
            } catch (e: Throwable) {
                // A failed page shouldn't wedge the loop on the same entry forever.
                if (isWebtoon) {
                    val key = pageKey(m)
                    downloadFailed[key] = System.currentTimeMillis()
                    if (looksLikeCloudflare(e)) pendingCfVerify = true
                } else {
                    displayPrewarmed[m.toString()] = true
                }
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
                                    // Webtoon pages render with the subsampling view: download the
                                    // page's bytes once to a cache file (through the source's own
                                    // client), then region-decode only the visible slice from disk —
                                    // never a giant full-height bitmap, which is what made long-strip
                                    // scrolling stutter. Paged modes (and the defensive fallback for a
                                    // non-descriptor page) keep the Coil path.
                                    if (isWebtoon && pageModel is MangaSource.PageDescriptor && source != null) {
                                        val key = pageKey(pageModel)
                                        val ratio = pageAspectRatios[key]
                                        val boxModifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (ratio != null && ratio > 0f) Modifier.aspectRatio(ratio)
                                                else Modifier.height(fallbackPageHeight)
                                            )
                                            .clipToBounds()
                                            .testTag("reader_page_${segIdx}_$pi")
                                        WebtoonSubsamplingItem(
                                            desc = pageModel,
                                            source = source,
                                            cacheDir = webtoonCacheDir,
                                            modifier = boxModifier,
                                            spinnerColor = contentTextColor,
                                            onRatioKnown = { r ->
                                                // The item learns the page's true ratio as soon as its
                                                // own download finishes (nearest pages are usually
                                                // downloaded by the prewarm first, but this covers the
                                                // case where the item wins the race).
                                                if (r > 0f && r.isFinite()) pageAspectRatios[key] = r
                                            }
                                        )
                                    } else {
                                        val ratio = pageAspectRatios[pageModel.toString()]
                                        if (ratio != null && ratio > 0f) {
                                            // True height known: size the slot exactly so the list never
                                            // relayouts when the strip finishes decoding. aspectRatio
                                            // derives the height from the item's ACTUAL measured width,
                                            // and the ratio comes from the same display decode that gets
                                            // rendered, so slot and bitmap always match to the pixel —
                                            // no hairline seams between consecutive pages.
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(ratio)
                                                    .clipToBounds()
                                                    .testTag("reader_page_${segIdx}_$pi")
                                            ) {
                                                ReaderPageImage(
                                                    model = pageModel,
                                                    contentDescription = "Page ${pi + 1}",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.FillWidth,
                                                    spinnerColor = contentTextColor,
                                                    decodeWidthPx = webtoonDecodeWidth,
                                                    crossfade = webtoonFade,
                                                    rgb565 = useRgb565
                                                )
                                            }
                                        } else {
                                            // Ratio not known yet: bounded fallback height so the image
                                            // can load; it snaps to its true height once the prewarm
                                            // learns it (one-time resize, pages far ahead are already
                                            // pre-sized). Clipped so an overshooting guess can't bleed
                                            // over the next strip.
                                            ReaderPageImage(
                                                model = pageModel,
                                                contentDescription = "Page ${pi + 1}",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(fallbackPageHeight)
                                                    .clipToBounds()
                                                    .testTag("reader_page_${segIdx}_$pi"),
                                                contentScale = ContentScale.FillWidth,
                                                spinnerColor = contentTextColor,
                                                decodeWidthPx = webtoonDecodeWidth,
                                                crossfade = webtoonFade,
                                                rgb565 = useRgb565
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
                                    val start = streamSegments.take(seg).sumOf { it.size } + seg
                                    listState.scrollToItem(start + targetPage)
                                } else {
                                    pagerState.scrollToPage(targetPage)
                                }
                            }
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
