package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.LocalImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.local.ChapterEntity
import com.example.data.local.MangaEntity
import com.example.ui.MainViewModel
import com.example.ui.ReaderBg
import com.example.ui.ReaderFit
import com.example.ui.ReaderMode
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

    // Both long-strip modes render as one continuous vertical list; only the gap differs.
    val isWebtoon = readerMode == ReaderMode.WEBTOON || readerMode == ReaderMode.WEBTOON_GAPS

    var pages by remember { mutableStateOf<List<Any>?>(null) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var pageLoading by remember { mutableStateOf(true) }
    var retryKey by remember { mutableStateOf(0) }

    LaunchedEffect(chapter.id, retryKey) {
        pageLoading = true
        pageError = null
        try {
            pages = viewModel.repository.getChapterPageImageModels(chapter.id)
        } catch (e: Throwable) {
            pageError = e.message ?: "Failed to load chapter pages"
            pages = null
        } finally {
            pageLoading = false
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

    // Webtoon Vertical List State
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (chapter.lastPageRead - 1).coerceAtLeast(0))

    // Paged Reader State (shared by horizontal + vertical pagers)
    val pagerState = rememberPagerState(
        initialPage = (chapter.lastPageRead - 1).coerceAtLeast(0),
        pageCount = { pages?.size ?: 0 }
    )

    val currentPage by remember {
        derivedStateOf {
            val total = pages?.size ?: 0
            if (total == 0) {
                0
            } else if (isWebtoon) {
                (listState.firstVisibleItemIndex + 1).coerceAtMost(total)
            } else {
                (pagerState.currentPage + 1).coerceAtMost(total)
            }
        }
    }

    // Save reading progress on page change
    LaunchedEffect(currentPage) {
        if (currentPage > 0) {
            viewModel.saveProgress(manga.id, chapter.id, chapter.name, currentPage)
        }
    }

    // Quick-load nearby pages: warm Coil's MEMORY cache (no disk cache!) for the pages around the
    // current one, so scrolling or jumping to a page renders instantly. The image bytes are keyed
    // by their URL, never by position, so there's no risk of serving another chapter's page.
    val imageLoader = LocalImageLoader.current
    val context = LocalContext.current
    LaunchedEffect(pages, currentPage, imageLoader) {
        val list = pages ?: return@LaunchedEffect
        if (imageLoader == null || list.isEmpty()) return@LaunchedEffect
        val start = (currentPage - 2).coerceAtLeast(0)
        val end = (currentPage + 5).coerceAtMost(list.size)
        for (i in start until end) {
            val model = list[i]
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(model)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()
            )
        }
    }

    val prevChapter = remember(allChapters, chapter) {
        allChapters.sortedBy { it.chapterNumber }.lastOrNull { it.chapterNumber < chapter.chapterNumber }
    }

    val nextChapter = remember(allChapters, chapter) {
        allChapters.sortedBy { it.chapterNumber }.firstOrNull { it.chapterNumber > chapter.chapterNumber }
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
                        verticalArrangement = if (readerMode == ReaderMode.WEBTOON_GAPS) {
                            Arrangement.spacedBy(10.dp)
                        } else {
                            Arrangement.Top
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(pageList) { index, pageUrl ->
                            ReaderPageImage(
                                model = pageUrl,
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reader_page_$index"),
                                contentScale = ContentScale.FillWidth,
                                spinnerColor = contentTextColor
                            )
                        }

                        // Next Chapter Prompt Footer
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "End of ${chapter.name}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = contentTextColor
                                    )
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                if (nextChapter != null) {
                                    Button(
                                        onClick = { onChapterChange(nextChapter.id) },
                                        modifier = Modifier.testTag("next_chapter_button")
                                    ) {
                                        Text("Read Next: ${nextChapter.name}")
                                    }
                                } else {
                                    Text(
                                        text = "You have caught up with the latest released chapter!",
                                        style = MaterialTheme.typography.bodySmall.copy(color = contentTextColor.copy(alpha = 0.7f))
                                    )
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
                    if (readerMode == ReaderMode.VERTICAL) {
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
                                text = chapter.name,
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

                        Text(
                            text = "Page $currentPage / ${pages?.size ?: 0}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier.testTag("page_indicator_text")
                        )

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
                                    listState.scrollToItem(targetPage)
                                } else {
                                    pagerState.scrollToPage(targetPage)
                                }
                            }
                        },
                        valueRange = 1f..(pages?.size ?: 1).coerceAtLeast(1).toFloat(),
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

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Page Fit (paged modes)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

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
                        ReaderBgChip(
                            label = "White",
                            color = Color.White,
                            isSelected = readerBg == ReaderBg.WHITE,
                            onClick = { viewModel.setReaderBg(ReaderBg.WHITE) }
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

    webviewTarget?.let { (url, ua) ->
        WebViewDialog(
            url = url,
            userAgent = ua,
            onDismiss = { webviewTarget = null }
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
        .heightIn(min = 240.dp)
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
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
        },
        error = {
            Box(
                modifier = placeholderModifier,
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Couldn't load page",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = spinnerColor.copy(alpha = 0.7f)
                    )
                )
            }
        }
    )
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
