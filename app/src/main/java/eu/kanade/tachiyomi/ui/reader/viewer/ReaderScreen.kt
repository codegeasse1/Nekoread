package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import coil.compose.LocalImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
import com.example.data.local.ChapterEntity
import com.example.data.local.MangaEntity
import com.example.data.coil.cropBorders
import com.example.data.reader.WebtoonPageCache
import com.example.data.source.MangaSource
import com.example.ui.MainViewModel
import com.example.ui.ColorFilterMode
import com.example.ui.ReaderBg
import com.example.ui.ReaderFit
import com.example.ui.ReaderHideThreshold
import com.example.ui.ReaderMode
import com.example.ui.ReaderOrientation
import com.example.ui.TappingInvertMode
import com.example.ui.WebtoonScaleType
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderDiagnostics
import com.example.ui.components.coverModelFor
import com.example.ui.looksLikeCloudflare
import com.example.util.chapterIdentity
import com.example.util.sortChapters
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonConfig
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonTrailer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// How many in-window webtoon pages the prewarm fetches/decodes at once. A small concurrent batch
// keeps the ±8/±20 page window filled ahead of the scroll even when every page costs a full download +
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
    val pinchToZoom: Boolean by viewModel.pinchToZoom.collectAsStateWithLifecycle()
    val webtoonCropBorders: Boolean by viewModel.webtoonCropBorders.collectAsStateWithLifecycle()
    val cropBordersPaged: Boolean by viewModel.cropBordersPaged.collectAsStateWithLifecycle()
    val cropBordersContinuous: Boolean by viewModel.cropBordersContinuous.collectAsStateWithLifecycle()
    val webtoonSidePadding: Int by viewModel.webtoonSidePadding.collectAsStateWithLifecycle()
    val webtoonNavigationMode: Int by viewModel.webtoonNavigationMode.collectAsStateWithLifecycle()
    val webtoonNavInverted: TappingInvertMode by viewModel.webtoonNavInverted.collectAsStateWithLifecycle()
    val webtoonSmallerTapZone: Boolean by viewModel.webtoonSmallerTapZone.collectAsStateWithLifecycle()
    val webtoonScaleType: WebtoonScaleType by viewModel.webtoonScaleType.collectAsStateWithLifecycle()
    val longStripGapSmartScale: Boolean by viewModel.longStripGapSmartScale.collectAsStateWithLifecycle()
    val webtoonDisableZoomOut: Boolean by viewModel.webtoonDisableZoomOut.collectAsStateWithLifecycle()
    val webtoonPageTransitions: Boolean by viewModel.webtoonPageTransitions.collectAsStateWithLifecycle()
    val webtoonSmoothAutoScroll: Boolean by viewModel.webtoonSmoothAutoScroll.collectAsStateWithLifecycle()
    val alwaysDecodeLongStripWithSSIV: Boolean by viewModel.alwaysDecodeLongStripWithSSIV.collectAsStateWithLifecycle()
    val continuousVerticalTappingByPage: Boolean by viewModel.continuousVerticalTappingByPage.collectAsStateWithLifecycle()
    val readerHideThreshold: ReaderHideThreshold by viewModel.readerHideThreshold.collectAsStateWithLifecycle()
    val doubleTapAnimDuration: Int by viewModel.doubleTapAnimDuration.collectAsStateWithLifecycle()
    val showReadingMode: Boolean by viewModel.showReadingMode.collectAsStateWithLifecycle()
    val customBrightness: Boolean by viewModel.customBrightness.collectAsStateWithLifecycle()
    val customBrightnessValue: Int by viewModel.customBrightnessValue.collectAsStateWithLifecycle()
    val colorFilter: Boolean by viewModel.colorFilter.collectAsStateWithLifecycle()
    val colorFilterValue: Int by viewModel.colorFilterValue.collectAsStateWithLifecycle()
    val colorFilterMode: Int by viewModel.colorFilterMode.collectAsStateWithLifecycle()
    val grayscale: Boolean by viewModel.grayscale.collectAsStateWithLifecycle()
    val invertedColors: Boolean by viewModel.invertedColors.collectAsStateWithLifecycle()
    val imageEnhance: Boolean by viewModel.imageEnhance.collectAsStateWithLifecycle()

    // Grayscale / inverted-colors / enhance combined matrix. When
    // grayscale is enabled the image is desaturated; inverted flips the colors; image enhance
    // applies a subtle contrast + saturation boost so pages pop. All three are plain color
    // matrices applied at draw time, so they work live in both modes without any re-decode or lag.
    val grayInvMatrix = remember(grayscale, invertedColors, imageEnhance) {
        if (!grayscale && !invertedColors && !imageEnhance) {
            null
        } else {
            val m = android.graphics.ColorMatrix()
            if (grayscale) m.setSaturation(0f)
            if (invertedColors) {
                m.postConcat(
                    android.graphics.ColorMatrix(
                        floatArrayOf(
                            -1f, 0f, 0f, 0f, 255f,
                            0f, -1f, 0f, 0f, 255f,
                            0f, 0f, -1f, 0f, 255f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                )
            }
            if (imageEnhance) {
                // Real-time "enhance": a gentle saturation + contrast boost. It's a color matrix
                // (GPU-composited at draw time), never a re-decode, so scrolling stays smooth.
                val sat = android.graphics.ColorMatrix()
                sat.setSaturation(1.15f)
                m.postConcat(sat)
                val c = 1.15f
                val t = 0.5f * (1f - c) * 255f
                m.postConcat(
                    android.graphics.ColorMatrix(
                        floatArrayOf(
                            c, 0f, 0f, 0f, t,
                            0f, c, 0f, 0f, t,
                            0f, 0f, c, 0f, t,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                )
            }
            m
        }
    }
    val webtoonColorFilter = remember(grayInvMatrix) {
        grayInvMatrix?.let { android.graphics.ColorMatrixColorFilter(it) }
    }

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
            // Every mode fetches page DESCRIPTORS (URLs). Page bytes are downloaded once per page
            // to an on-device cache file through the source's own client (see the prewarm loop
            // below) — both the pager viewer's page holders and the webtoon viewer's items render
            // from those files, so there's no separate source-aware Coil-model fetch (that used to
            // double the chapter-open cost on slow sources).
            pages = viewModel.repository.getChapterPageDescriptors(chapter.id)
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

    // Reading order for all of this manga's chapters (also used by the prev/next arrow buttons and
    // the webtoon auto-continue below).
    val sortedChapters = remember(allChapters) { sortChapters(allChapters) }

    // The previous chapter in reading order (skipping cross-source duplicates) — the one the user
    // jumped FROM via an in-reader next-arrow / chapter-list tap. It's prepended to the webtoon
    // stream (see the seed effect below) so that scrolling UP after a jump returns to it, instead
    // of the stream starting at the jumped-to chapter with nothing above it (scroll-up hit a wall).
    val prevForStream = remember(sortedChapters, chapter.id) {
        val idx = sortedChapters.indexOfFirst { it.id == chapter.id }
        if (idx <= 0) return@remember null
        val curKey = chapterIdentity(chapter)
        var i = idx - 1
        while (i >= 0) {
            if (chapterIdentity(sortedChapters[i]) != curKey) return@remember sortedChapters[i]
            i--
        }
        null
    }

    // Seed the webtoon stream with the current chapter's pages once loaded. When the reader was
    // opened via an in-reader chapter jump (startAtBeginning), the PREVIOUS chapter's pages are
    // fetched and prepended too, so scrolling up can return to the chapter the user came from —
    // same as when that chapter was reached by scrolling down into it. If the previous chapter
    // fails to load, the stream falls back to the current chapter alone.
    LaunchedEffect(pages, chapter.id, isWebtoon, startAtBeginning, prevForStream?.id) {
        if (!isWebtoon) return@LaunchedEffect
        val cur = pages ?: return@LaunchedEffect
        if (cur.isEmpty()) return@LaunchedEffect
        // Already seeded with these pages — don't wipe a stream that auto-continue has grown.
        if (streamSegments.isNotEmpty() && streamSegments.last() === cur) return@LaunchedEffect
        val prev = prevForStream
        if (startAtBeginning && prev != null) {
            val prevPages = try {
                viewModel.repository.getChapterPageDescriptors(prev.id)
            } catch (e: Throwable) {
                null
            }
            if (prevPages != null && prevPages.isNotEmpty()) {
                streamQueue = listOf(prev, chapter)
                streamSegments = listOf(prevPages, cur)
                return@LaunchedEffect
            }
        }
        streamQueue = listOf(chapter)
        streamSegments = listOf(cur)
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

    // Global adapter position the webtoon viewer should open at. When the previous chapter was
    // prepended the current chapter sits at segment 1, so its first page is one divider plus the
    // previous segment's page count in. Computed from the LIVE stream so a failed previous-chapter
    // fetch (single-segment stream) still lands on the right page.
    val webtoonInitialPos = remember(streamSegments, streamQueue, initialPageIndex, chapter.id) {
        val segIdx = streamQueue.indexOfFirst { it.id == chapter.id }.coerceAtLeast(0)
        streamSegments.take(segIdx).sumOf { it.size } + segIdx + initialPageIndex
    }

    // Webtoon reader state (yomi-style RecyclerView viewer): the current (segment, page, pageTotal)
    // reported by the viewer, whether the user is near the end of the stream (drives the
    // auto-continue load of the next chapter), and a handle on the viewer itself (used by the page
    // slider to jump to a page). The viewer replaces the Compose LazyColumn for webtoon modes.
    var viewerPos by remember(chapter.id) { mutableStateOf(Triple(0, 1, pages?.size ?: 1)) }
    var viewerNearEnd by remember(chapter.id) { mutableStateOf(false) }
    val viewerRef = remember(chapter.id) { mutableStateOf<WebtoonViewer?>(null) }

    // Paged reader state (chimahon pager viewer): the native viewer reports the current 1-based
    // page via onPageChanged, and a handle on the viewer lets the page slider jump to a page. The
    // viewer itself owns all paging/zoom/tap behavior (see ChimahonPagerReader).
    var pagerPos by remember(chapter.id) { mutableStateOf(1) }
    val pagerViewRef = remember(chapter.id) { mutableStateOf<PagerViewer?>(null) }

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
                // seed the page state so the HUD reads correctly until the viewer reports in. When
                // the previous chapter was prepended (in-reader jump) the current chapter lives at
                // segment 1, not 0.
                val seg = if (startAtBeginning && prevForStream != null) 1 else 0
                viewerPos = Triple(seg, (target + 1).coerceIn(1, list.size), list.size)
            } else {
                // The chimahon pager positions itself on creation (setPages with the initial page);
                // seed the HUD state so the page slider reads correctly until the viewer reports.
                pagerPos = (target + 1).coerceIn(1, list.size)
            }
        } catch (_: Exception) {
        }
    }

    // The chapter currently on screen (in webtoon modes this can advance past the starting chapter).
    // In webtoon mode the position comes from the yomi viewer's page-change reports; in paged modes
    // from the chimahon pager's.
    val streamPosition by remember {
        derivedStateOf {
            if (isWebtoon) viewerPos else Triple(0, pagerPos, pages?.size ?: 1)
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
                pagerPos.coerceIn(1, pages?.size ?: 1)
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

    // Previous/next chapters are found by POSITION in the chapter list rather than by comparing
    // chapter numbers: sources that don't number their chapters store -1 for every chapter, which
    // made both buttons permanently disabled and silently killed the webtoon auto-continue
    // ("End of Chapter / you caught up" even when dozens of chapters were still ahead).
    //
    // On top of the position, next/prev SKIP cross-source duplicates: a source can expose the same
    // chapter number from several scanlators (e.g. "Chapter 1" from WebToon AND from Asura Scans),
    // so the adjacent list entry is often that very chapter again. Walking past entries with the
    // same chapter identity keeps the buttons (and the webtoon auto-continue) landing on a
    // genuinely different chapter, never a re-read of the current one.
    val activeIdx = remember(sortedChapters, activeChapter) {
        sortedChapters.indexOfFirst { it.id == activeChapter.id }
    }
    fun nextAfter(from: ChapterEntity, idx: Int, forward: Boolean): ChapterEntity? {
        val curKey = chapterIdentity(from)
        var i = idx + if (forward) 1 else -1
        while (i in sortedChapters.indices) {
            if (chapterIdentity(sortedChapters[i]) != curKey) return sortedChapters[i]
            i += if (forward) 1 else -1
        }
        return null
    }
    val prevChapter = remember(sortedChapters, activeIdx, activeChapter) {
        nextAfter(activeChapter, activeIdx, forward = false)
    }
    val nextChapter = remember(sortedChapters, activeIdx, activeChapter) {
        nextAfter(activeChapter, activeIdx, forward = true)
    }

    // The next chapter after the LAST one already streamed (used for auto-continue).
    val streamNextChapter = remember(sortedChapters, streamQueue) {
        val last = streamQueue.lastOrNull() ?: return@remember null
        val lastIdx = sortedChapters.indexOfFirst { it.id == last.id }
        nextAfter(last, lastIdx, forward = true)
    }

    fun loadNextIntoStream(next: ChapterEntity) {
        coroutineScope.launch {
            webtoonLoadingNext = true
            webtoonError = null
            try {
                val descs = viewModel.repository.getChapterPageDescriptors(next.id)
                streamQueue = streamQueue + next
                streamSegments = streamSegments + listOf(descs)
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

    val displayDecodeWidth = if (isWebtoon) webtoonDecodeWidth else screenWidthPx

    // Pages whose cache file is downloaded (or attempted). The prewarm loop owns this: it
    // fetches every in-window page's bytes ONCE into WebtoonPageCache's on-device cache dir (see the
    // loop below), and the visible items render from those files — so a scroll never re-fetches a
    // page. The visible item has its own retry + button if it wins the race with the prewarm.
    val webtoonDownloaded = remember { mutableStateMapOf<String, Boolean>() }
    // Pages currently being downloaded by the prewarm pipeline (tracked so the non-stalling loop
    // never launches the same page twice while a download is in flight).
    val webtoonInFlight = remember { mutableStateMapOf<String, Boolean>() }
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
    // ahead and 8 pages behind display-ready. Every in-window page's bytes are downloaded ONCE
    // (through the source's own client + Descrambler, single-flighted via WebtoonPageCache) to an
    // on-device cache file — both the webtoon viewer's items and the chimahon pager's page holders
    // render from those files, so no page is ever fetched twice (the thing that made slow sources
    // like comix stutter). In WEBTOON mode short pages additionally get their display-size decode
    // warmed into Coil's memory cache with the exact request the item will use, so a page scrolling
    // in is an instant cache hit; TALL strips (height > 3x width — yomi/mihon's ImageUtil.isTallImage
    // rule) are left to the subsampling view's region-decode from the file — never a giant
    // full-height bitmap. Paged-mode pages are region-decoded by the pager's subsampling views
    // too, so only the download needs pre-warming there. Pages are processed in a small CONCURRENT
    // batch so the window fills ahead of the scroll — a sequential one-at-a-time loop can't keep
    // up when every page costs a full download + descramble (comix).
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
            // Preload window: 8 pages behind and 20 ahead of the current page are made
            // display-ready (Coil memory-cache warm for short pages, cache-file download for tall
            // strips) so that scrolling — and jumping straight to any page — shows the neighbours
            // instantly instead of decoding them on first scroll-in. The webtoon window leans
            // further AHEAD because that's the direction the user scrolls; the extra lead time is
            // what lets slow sources (comix's big descrambled images) finish downloading before a
            // page enters the viewport, so its holder never binds against a placeholder height.
            val warmFrom = (globCur - 8).coerceAtLeast(0)
            val warmTo = (if (isWebtoon) globCur + 20 else globCur + 8).coerceAtMost(total - 1)

            // Collect the nearest not-yet-downloaded pages, walking outward from the current
            // page (current, +1, -1, +2, -2, ...), then launch a small CONCURRENT batch of
            // downloads so the ±8 window fills ahead of the scroll.
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
                    // Only descriptor pages are pre-processed (anything else renders via the
                    // item's own fallback).
                    if (m !is MangaSource.PageDescriptor) continue
                    val key = pageKey(m)
                    // Skip pages that just failed — the visible item retries on its own; this
                    // loop would otherwise hot-spin on the same failure every 120ms.
                    if (downloadFailed.containsKey(key) && now - downloadFailed[key]!! < 8000) continue
                    // The viewer's page holder downloads a page the moment it's shown; this loop
                    // just fills the window AHEAD so pages are already on disk when the holder
                    // asks (single-flighted via WebtoonPageCache) — the yomi trick that keeps
                    // both the pager and the long-strip reader smooth on slow sources.
                    if (!webtoonDownloaded.containsKey(key) && !webtoonInFlight.containsKey(key)) {
                        collected.add(Triple(seg, g, m))
                    }
                }
            }

            // Non-stalling download pipeline: launch up to WEBTOON_BATCH downloads at a time as
            // fire-and-forget children and keep scanning immediately, so ONE slow page (a big
            // comix image) can never stall the whole window — the old awaitAll blocked on the
            // slowest member of each batch before starting the next. Each finished download frees
            // a slot that the next loop tick fills with the nearest pending page, keeping the
            // window topped up while the user pages or scrolls.
            val room = (WEBTOON_BATCH - webtoonInFlight.size).coerceAtLeast(0)
            for ((seg, g, m) in collected.take(room)) {
                val key = pageKey(m)
                if (webtoonInFlight.containsKey(key) || webtoonDownloaded.containsKey(key)) continue
                webtoonInFlight[key] = true
                launch {
                    try {
                        if (source != null) {
                            val file = WebtoonPageCache.fileFor(m, source, webtoonCacheDir)
                            webtoonDownloaded[key] = true
                            if (isWebtoon) {
                                // Warm Coil's MEMORY cache for SHORT webtoon pages with the exact
                                // request the item will use (same file, size, policies), so a page
                                // scrolling in is an instant cache hit instead of a fresh decode
                                // (and every re-scroll is free). Tall strips are left to the
                                // subsampling view's region-decode from the file. meta() also caches
                                // the page's animated flag, so the page holder's own meta() query is
                                // a pure map lookup and it pre-sizes the frame to the real strip
                                // height before the page ever enters the viewport.
                                val mm = WebtoonPageCache.meta(m, webtoonCacheDir)
                                val tall = mm == null || mm.isTall
                                if (!tall) {
                                    runCatching {
                                        imageLoader.execute(
                                            ImageRequest.Builder(context)
                                                .data(file)
                                                .size(Size(displayDecodeWidth, Dimension.Undefined))
                                                .memoryCachePolicy(CachePolicy.ENABLED)
                                                .diskCachePolicy(CachePolicy.DISABLED)
                                                // Same as the page holder's request: hardware
                                                // bitmaps when not cropping (GPU-backed, no
                                                // texture-upload churn while scrolling; Coil 2
                                                // caches them in memory, so this warm still hits
                                                // when the page scrolls in).
                                                .allowHardware(!cropBorders)
                                                .apply {
                                                    // Keep the cache key in sync with the page
                                                    // holder's request so a cropped decode is what
                                                    // gets warmed (and never collides with the
                                                    // uncropped one).
                                                    if (cropBorders) cropBorders(true)
                                                }
                                                .build(),
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        // Effect restarts (scroll/key change) cancel in-flight work — don't
                        // treat a cancellation as a page failure.
                        if (e is CancellationException) throw e
                        // A failed page shouldn't wedge the loop on the same entry forever.
                        downloadFailed[key] = System.currentTimeMillis()
                        if (looksLikeCloudflare(e)) pendingCfVerify = true
                    } finally {
                        webtoonInFlight.remove(key)
                    }
                }
            }
            delay(60)
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

    // Custom brightness: a positive value raises the window's screen brightness (0..1); a negative
    // value is instead rendered as a dark overlay (see the overlay Box below). Reset to the
    // system-auto level when disabled / on leave.
    DisposableEffect(customBrightness, customBrightnessValue, activity) {
        val window = activity?.window
        if (customBrightness && customBrightnessValue > 0 && window != null) {
            val lp = window.attributes
            lp.screenBrightness = (customBrightnessValue / 100f).coerceIn(0f, 1f)
            window.attributes = lp
        }
        onDispose {
            if (window != null) {
                val lp = window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
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
                    // Snapshot of webtoon-mode settings; rebuilt whenever any pref changes so the
                    // viewer re-applies crop / tap zones / zoom / page scale live (see
                    // WebtoonConfig). The continuous (no-gap) long-strip crop is the existing
                    // "Crop borders" setting (keeps the bottom-bar crop toggle + old pref
                    // working); gaps mode has its own dedicated toggle.
                    val webtoonConfig = remember(
                        cropBorders, cropBordersContinuous, webtoonSidePadding, webtoonNavigationMode,
                        webtoonNavInverted, webtoonSmallerTapZone, webtoonScaleType, longStripGapSmartScale,
                        webtoonDisableZoomOut, webtoonPageTransitions, readerHideThreshold, doubleTapAnimDuration,
                        alwaysDecodeLongStripWithSSIV, continuousVerticalTappingByPage, webtoonSmoothAutoScroll,
                        doubleTapZoom, pinchToZoom, webtoonFade,
                    ) {
                        WebtoonConfig().apply {
                            this.cropBordersWebtoon = cropBorders
                            this.continuousCropBorders = cropBordersContinuous
                            this.webtoonSidePadding = webtoonSidePadding
                            this.navigationMode = webtoonNavigationMode
                            this.tappingInverted = webtoonNavInverted
                            this.smallerTapZone = webtoonSmallerTapZone
                            this.webtoonScaleType = webtoonScaleType
                            this.longStripGapSmartScale = longStripGapSmartScale
                            this.webtoonDisableZoomOut = webtoonDisableZoomOut
                            this.usePageTransitions = webtoonPageTransitions
                            this.readerHideThreshold = readerHideThreshold
                            this.doubleTapAnimDuration = doubleTapAnimDuration
                            this.alwaysDecodeLongStripWithSSIV = alwaysDecodeLongStripWithSSIV
                            this.continuousVerticalTappingByPage = continuousVerticalTappingByPage
                            this.smoothAutoScroll = webtoonSmoothAutoScroll
                            this.doubleTapZoom = doubleTapZoom
                            this.pinchToZoom = pinchToZoom
                            this.fadeIn = webtoonFade
                        }
                    }
                    YomiWebtoonReader(
                        source = source,
                        cacheDir = webtoonCacheDir,
                        streamQueue = streamQueue,
                        streamSegments = streamSegments,
                        segSizes = streamSegments.map { it.size },
                        bgColor = bgColor,
                        textColor = contentTextColor,
                        gaps = readerMode == ReaderMode.WEBTOON_GAPS,
                        config = webtoonConfig,
                        onHideMenu = { showHud = false },
                        colorFilter = webtoonColorFilter,
                        decodeWidth = displayDecodeWidth,
                        rgb565 = useRgb565,
                        autoScroll = autoScroll,
                        autoScrollSpeedDp = autoScrollSpeedDp,
                        initialPageIndex = webtoonInitialPos,
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
                    if (pageList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = contentTextColor)
                        }
                    } else {
                        // Paged modes (left-to-right, right-to-left, vertical) render through the
                        // chimahon pager viewer: one page per screen with chimahon's tap zones,
                        // double-tap/pinch zoom, page transitions and crop settings, pages
                        // downloaded once to the on-device cache and region-decoded from disk.
                        // The config snapshot is rebuilt whenever a relevant setting changes; the
                        // viewer applies it live (see PagerConfig). With tap-to-change-pages off,
                        // taps only toggle the menu (navigation mode 5 = disabled).
                        val pagerConfig = remember(
                            readerFit, readerMode, cropBordersPaged, doubleTapZoom, pinchToZoom,
                            webtoonNavigationMode, webtoonNavInverted, webtoonSmallerTapZone,
                            doubleTapAnimDuration, webtoonPageTransitions, bgColor,
                        ) {
                            PagerConfig().apply {
                                this.imageScaleType = when (readerFit) {
                                    ReaderFit.FIT, ReaderFit.SMART_FIT ->
                                        SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
                                    ReaderFit.STRETCH, ReaderFit.FIT_HEIGHT ->
                                        SubsamplingScaleImageView.SCALE_TYPE_CENTER_CROP
                                    ReaderFit.FIT_WIDTH ->
                                        SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH
                                    ReaderFit.ORIGINAL_SIZE ->
                                        SubsamplingScaleImageView.SCALE_TYPE_CUSTOM
                                }
                                this.imageZoomType = when (readerMode) {
                                    ReaderMode.RIGHT_TO_LEFT ->
                                        ReaderPageImageView.Config.ZoomStartPosition.RIGHT
                                    ReaderMode.VERTICAL ->
                                        ReaderPageImageView.Config.ZoomStartPosition.CENTER
                                    else ->
                                        ReaderPageImageView.Config.ZoomStartPosition.LEFT
                                }
                                this.imageCropBorders = cropBordersPaged
                                this.doubleTapZoom = doubleTapZoom
                                this.enablePinchToZoom = pinchToZoom
                                this.usePageTransitions = webtoonPageTransitions
                                this.doubleTapAnimDuration = doubleTapAnimDuration
                                this.navigationMode = when {
                                    !tapToChangePages -> 5
                                    webtoonNavigationMode != 5 -> webtoonNavigationMode
                                    else -> 4
                                }
                                this.tappingInverted = webtoonNavInverted
                                this.smallerTapZone = webtoonSmallerTapZone
                                this.isVertical = readerMode == ReaderMode.VERTICAL
                                this.pageCanvasColor = bgColor.toArgb()
                            }
                        }
                        ChimahonPagerReader(
                            source = source,
                            cacheDir = webtoonCacheDir,
                            pages = pageList.filterIsInstance<MangaSource.PageDescriptor>(),
                            bgColor = bgColor,
                            textColor = contentTextColor,
                            vertical = readerMode == ReaderMode.VERTICAL,
                            reversed = readerMode == ReaderMode.RIGHT_TO_LEFT,
                            config = pagerConfig,
                            initialPageIndex = initialPageIndex,
                            viewerRef = pagerViewRef,
                            onPageChanged = { page, _ -> pagerPos = page },
                            onMenuTap = { showHud = !showHud },
                            onZoom = { showHud = false },
                            colorFilter = webtoonColorFilter,
                            decodeWidth = displayDecodeWidth,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

    // Bookmark state is tracked locally: activeChapter is a snapshot that won't update when
    // the DB row flips under it, so we mirror the toggle here and reset whenever the chapter
    // changes.
    var chapterBookmarked by remember(activeChapter.id) { mutableStateOf(activeChapter.bookmarked) }

    // Color/brightness overlays (chimahon's ReaderContentOverlay), drawn above the page content but
    // below the chrome so the menu stays legible: a dark veil when custom brightness is negative,
    // plus the blend-mode color filter when enabled.
    val overlayBlendMode = ColorFilterMode.entries.getOrElse(colorFilterMode) { ColorFilterMode.DEFAULT }.blendMode
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (customBrightness && customBrightnessValue < 0) {
                    Modifier.background(Color.Black.copy(alpha = (-customBrightnessValue / 100f).coerceIn(0f, 1f)))
                } else {
                    Modifier
                }
            )
            .then(
                if (colorFilter) {
                    Modifier.drawBehind {
                        drawRect(color = Color(colorFilterValue), blendMode = overlayBlendMode)
                    }
                } else {
                    Modifier
                }
            )
    )

        // Yomi-style reader chrome: top bar (bookmark / overflow / auto-scroll), chapter
        // navigator pill, bottom toolbar and the settings sheets/dialogs live in YomiReaderChrome.
        // The manga cover is passed in so the chapter-list sheet shows a thumbnail per row
        // (chimahon-style) instead of plain text rows.
        val chapterCoverModel = coverModelFor(manga)
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
                        // onSeekPage delivers a 0-based index; the pager's moveToPage is 1-based.
                        pagerViewRef.value?.moveToPage(targetPage + 1)
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
            cropBordersPaged = cropBordersPaged,
            onToggleCropBordersPaged = { viewModel.setCropBordersPaged(!cropBordersPaged) },
            cropBordersContinuous = cropBordersContinuous,
            onToggleCropBordersContinuous = { viewModel.setCropBordersContinuous(!cropBordersContinuous) },
            doubleTapZoom = doubleTapZoom,
            onToggleDoubleTapZoom = { viewModel.setDoubleTapZoom(!doubleTapZoom) },
            pinchToZoom = pinchToZoom,
            onTogglePinchToZoom = { viewModel.setPinchToZoom(!pinchToZoom) },
            tapToChangePages = tapToChangePages,
            onToggleTapToChangePages = { viewModel.setTapToChangePages(!tapToChangePages) },
            webtoonSidePadding = webtoonSidePadding,
            onWebtoonSidePaddingChange = { viewModel.setWebtoonSidePadding(it) },
            webtoonNavigationMode = webtoonNavigationMode,
            onWebtoonNavigationModeChange = { viewModel.setWebtoonNavigationMode(it) },
            webtoonNavInverted = webtoonNavInverted,
            onWebtoonNavInvertedChange = { viewModel.setWebtoonNavInverted(it) },
            webtoonSmallerTapZone = webtoonSmallerTapZone,
            onToggleWebtoonSmallerTapZone = { viewModel.setWebtoonSmallerTapZone(!webtoonSmallerTapZone) },
            webtoonScaleType = webtoonScaleType,
            onWebtoonScaleTypeChange = { viewModel.setWebtoonScaleType(it) },
            longStripGapSmartScale = longStripGapSmartScale,
            onToggleLongStripGapSmartScale = { viewModel.setLongStripGapSmartScale(!longStripGapSmartScale) },
            webtoonDisableZoomOut = webtoonDisableZoomOut,
            onToggleWebtoonDisableZoomOut = { viewModel.setWebtoonDisableZoomOut(!webtoonDisableZoomOut) },
            webtoonPageTransitions = webtoonPageTransitions,
            onToggleWebtoonPageTransitions = { viewModel.setWebtoonPageTransitions(!webtoonPageTransitions) },
            webtoonSmoothAutoScroll = webtoonSmoothAutoScroll,
            onToggleWebtoonSmoothAutoScroll = { viewModel.setWebtoonSmoothAutoScroll(!webtoonSmoothAutoScroll) },
            alwaysDecodeLongStripWithSSIV = alwaysDecodeLongStripWithSSIV,
            onToggleAlwaysDecodeLongStripWithSSIV = { viewModel.setAlwaysDecodeLongStripWithSSIV(!alwaysDecodeLongStripWithSSIV) },
            continuousVerticalTappingByPage = continuousVerticalTappingByPage,
            onToggleContinuousVerticalTappingByPage = { viewModel.setContinuousVerticalTappingByPage(!continuousVerticalTappingByPage) },
            readerHideThreshold = readerHideThreshold,
            onReaderHideThresholdChange = { viewModel.setReaderHideThreshold(it) },
            doubleTapAnimDuration = doubleTapAnimDuration,
            onDoubleTapAnimDurationChange = { viewModel.setDoubleTapAnimDuration(it) },
            showReadingMode = showReadingMode,
            onToggleShowReadingMode = { viewModel.setShowReadingMode(!showReadingMode) },
            customBrightness = customBrightness,
            onToggleCustomBrightness = { viewModel.setCustomBrightness(!customBrightness) },
            customBrightnessValue = customBrightnessValue,
            onCustomBrightnessValueChange = { viewModel.setCustomBrightnessValue(it) },
            colorFilter = colorFilter,
            onToggleColorFilter = { viewModel.setColorFilter(!colorFilter) },
            colorFilterValue = colorFilterValue,
            onColorFilterValueChange = { viewModel.setColorFilterValue(it) },
            colorFilterMode = colorFilterMode,
            onColorFilterModeChange = { viewModel.setColorFilterMode(it) },
            grayscale = grayscale,
            onToggleGrayscale = { viewModel.setGrayscale(!grayscale) },
            invertedColors = invertedColors,
            onToggleInvertedColors = { viewModel.setInvertedColors(!invertedColors) },
            imageEnhance = imageEnhance,
            onToggleImageEnhance = { viewModel.setImageEnhance(!imageEnhance) },
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
                viewModel.setShowReadingMode(true)
                viewModel.setWebtoonFade(false)
                viewModel.setAutoScroll(false)
                viewModel.setAutoScrollSpeedDp(80f)
                viewModel.setReaderQuality(75)
                viewModel.setCropBorders(false)
                viewModel.setCropBordersPaged(false)
                viewModel.setCropBordersContinuous(false)
                viewModel.setDoubleTapZoom(true)
                viewModel.setPinchToZoom(true)
                viewModel.setTapToChangePages(false)
                viewModel.setWebtoonSidePadding(0)
                viewModel.setWebtoonNavigationMode(5)
                viewModel.setWebtoonNavInverted(TappingInvertMode.NONE)
                viewModel.setWebtoonSmallerTapZone(false)
                viewModel.setWebtoonScaleType(WebtoonScaleType.FIT)
                viewModel.setLongStripGapSmartScale(false)
                viewModel.setWebtoonDisableZoomOut(false)
                viewModel.setWebtoonPageTransitions(true)
                viewModel.setWebtoonSmoothAutoScroll(true)
                viewModel.setAlwaysDecodeLongStripWithSSIV(false)
                viewModel.setContinuousVerticalTappingByPage(false)
                viewModel.setReaderHideThreshold(ReaderHideThreshold.LOW)
                viewModel.setDoubleTapAnimDuration(500)
                viewModel.setCustomBrightness(false)
                viewModel.setCustomBrightnessValue(0)
                viewModel.setColorFilter(false)
                viewModel.setColorFilterValue(0)
                viewModel.setColorFilterMode(0)
                viewModel.setGrayscale(false)
                viewModel.setInvertedColors(false)
                viewModel.setImageEnhance(false)
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
            onSelectChapter = { onChapterChange(it) },
            chapterCoverModel = chapterCoverModel,
        )

        if (ReaderDiagnostics.ENABLED) {
            ReaderDiagnosticsOverlay()
        }
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

/** Test-build on-screen diagnostics for the comix long-strip lag: live page-path / decode info
 *  with one-tap copy to clipboard and clear. TEMPORARY — removed once the lag is fixed. */
@Composable
private fun ReaderDiagnosticsOverlay() {
    val context = LocalContext.current
    ReaderDiagnostics.init(context)
    var text by remember { mutableStateOf(ReaderDiagnostics.text()) }
    DisposableEffect(Unit) {
        val prev = ReaderDiagnostics.onUpdate
        ReaderDiagnostics.onUpdate = { text = ReaderDiagnostics.text() }
        onDispose { ReaderDiagnostics.onUpdate = prev }
    }
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 8.dp)
            .fillMaxWidth(0.9f),
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Reader dx",
                        color = Color(0xFFFFFFFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row {
                        TextButton(
                            onClick = {
                                ReaderDiagnostics.copy(context)
                                Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Text("Copy", color = Color(0xFF4FC3F7), fontSize = 12.sp)
                        }
                        TextButton(onClick = { ReaderDiagnostics.clear() }) {
                            Text("Clear", color = Color(0xFF90A4AE), fontSize = 12.sp)
                        }
                    }
                }
                Text(
                    text = text.ifEmpty { "no events yet — open a comix manhwa and scroll" },
                    color = Color(0xFFDDDDDD),
                    fontSize = 9.sp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
                ReaderDiagnostics.path()?.let { p ->
                    Text(
                        text = p,
                        color = Color(0xFF90A4AE),
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
