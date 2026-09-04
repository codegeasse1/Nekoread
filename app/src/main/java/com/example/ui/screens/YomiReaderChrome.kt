@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.ViewComfy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.ChapterEntity
import com.example.ui.ReaderBg
import com.example.ui.ReaderFit
import com.example.ui.ReaderMode
import com.example.ui.ReaderOrientation

private val ChromeBarColor = Color(0xCC161926)
private val SheetColor = Color(0xFF1B1E2A)
private val CardColor = Color(0x2E3A3F52)
private val Accent = Color(0xFF8AB4F8)
private val OnDark = Color(0xFFEDEDF0)
private val Muted = Color(0xFF9AA0B4)

/**
 * Yomi-style reader chrome ported into Nekoread: a dark top bar with bookmark / overflow /
 * expandable auto-scroll controls, a floating chapter navigator pill with prev/next + page slider,
 * and a 5-button bottom bar (reading mode, orientation, crop borders, chapter list, settings) that
 * opens yomi-style Reading mode / General / Color settings sheets. The chrome is stateless: the
 * screen passes current values in and receives intents out.
 */
@Composable
fun YomiReaderChrome(
    visible: Boolean,
    mangaTitle: String,
    chapterTitle: String,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onReloadChapter: () -> Unit,
    onBack: () -> Unit,
    isWebtoon: Boolean,
    autoScroll: Boolean,
    autoScrollSpeedDp: Float,
    onToggleAutoScroll: () -> Unit,
    onAutoScrollSpeedChange: (Float) -> Unit,
    prevEnabled: Boolean,
    onPrevChapter: () -> Unit,
    nextEnabled: Boolean,
    onNextChapter: () -> Unit,
    currentPage: Int,
    totalPages: Int,
    onSeekPage: (Int) -> Unit,
    readerMode: ReaderMode,
    onSelectReaderMode: (ReaderMode) -> Unit,
    readerFit: ReaderFit,
    onSelectReaderFit: (ReaderFit) -> Unit,
    readerOrientation: ReaderOrientation,
    onSelectReaderOrientation: (ReaderOrientation) -> Unit,
    cropBorders: Boolean,
    onToggleCropBorders: () -> Unit,
    readerBg: ReaderBg,
    onSelectReaderBg: (ReaderBg) -> Unit,
    showPageNumber: Boolean,
    onToggleShowPageNumber: () -> Unit,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: () -> Unit,
    webtoonFade: Boolean,
    onToggleWebtoonFade: () -> Unit,
    readerQuality: Int,
    onSelectReaderQuality: (Int) -> Unit,
    onResetSettings: () -> Unit,
    seriesOverrideEnabled: Boolean,
    onToggleSeriesOverride: () -> Unit,
    chapters: List<ChapterEntity>,
    activeChapterId: String,
    onSelectChapter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }
    var showOrientationDialog by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var autoScrollExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
            ) {
                TopReaderBar(
                    mangaTitle = mangaTitle,
                    chapterTitle = chapterTitle,
                    bookmarked = bookmarked,
                    onToggleBookmarked = onToggleBookmarked,
                    onOpenInWebView = onOpenInWebView,
                    onReloadChapter = onReloadChapter,
                    onBack = onBack,
                    isWebtoon = isWebtoon,
                    autoScroll = autoScroll,
                    autoScrollSpeedDp = autoScrollSpeedDp,
                    onToggleAutoScroll = onToggleAutoScroll,
                    onAutoScrollSpeedChange = onAutoScrollSpeedChange,
                    autoScrollExpanded = autoScrollExpanded,
                    onToggleExpand = { autoScrollExpanded = !autoScrollExpanded },
                )
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChapterNavigatorPill(
                        prevEnabled = prevEnabled,
                        onPrevChapter = onPrevChapter,
                        nextEnabled = nextEnabled,
                        onNextChapter = onNextChapter,
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onSeekPage = onSeekPage,
                        showPageNumber = showPageNumber,
                    )
                    BottomReaderBar(
                        onClickReadingMode = { showModeDialog = true },
                        onClickOrientation = { showOrientationDialog = true },
                        cropEnabled = cropBorders,
                        onClickCropBorder = onToggleCropBorders,
                        onClickChapterList = { showChapterList = true },
                        onClickSettings = { showSettings = true },
                    )
                }
            }
        }

        if (showSettings) {
            ReaderSettingsSheet(
                onDismiss = { showSettings = false },
                readerMode = readerMode,
                onSelectReaderMode = onSelectReaderMode,
                readerFit = readerFit,
                onSelectReaderFit = onSelectReaderFit,
                readerOrientation = readerOrientation,
                onSelectReaderOrientation = onSelectReaderOrientation,
                readerBg = readerBg,
                onSelectReaderBg = onSelectReaderBg,
                showPageNumber = showPageNumber,
                onToggleShowPageNumber = onToggleShowPageNumber,
                keepScreenOn = keepScreenOn,
                onToggleKeepScreenOn = onToggleKeepScreenOn,
                webtoonFade = webtoonFade,
                onToggleWebtoonFade = onToggleWebtoonFade,
                readerQuality = readerQuality,
                onSelectReaderQuality = onSelectReaderQuality,
                cropBorders = cropBorders,
                onToggleCropBorders = onToggleCropBorders,
                autoScroll = autoScroll,
                autoScrollSpeedDp = autoScrollSpeedDp,
                onToggleAutoScroll = onToggleAutoScroll,
                onAutoScrollSpeedChange = onAutoScrollSpeedChange,
                onResetSettings = onResetSettings,
                seriesOverrideEnabled = seriesOverrideEnabled,
                onToggleSeriesOverride = onToggleSeriesOverride,
            )
        }

        if (showModeDialog) {
            ReadingModeDialog(
                onDismiss = { showModeDialog = false },
                readerMode = readerMode,
                onSelectReaderMode = onSelectReaderMode,
            )
        }

        if (showOrientationDialog) {
            OrientationDialog(
                onDismiss = { showOrientationDialog = false },
                readerOrientation = readerOrientation,
                onSelectReaderOrientation = onSelectReaderOrientation,
            )
        }

        if (showChapterList) {
            ChapterListSheet(
                onDismiss = { showChapterList = false },
                chapters = chapters,
                activeChapterId = activeChapterId,
                onSelectChapter = onSelectChapter,
            )
        }
    }
}

@Composable
private fun TopReaderBar(
    mangaTitle: String,
    chapterTitle: String,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onReloadChapter: () -> Unit,
    onBack: () -> Unit,
    isWebtoon: Boolean,
    autoScroll: Boolean,
    autoScrollSpeedDp: Float,
    onToggleAutoScroll: () -> Unit,
    onAutoScrollSpeedChange: (Float) -> Unit,
    autoScrollExpanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    Surface(
        color = ChromeBarColor,
        contentColor = OnDark,
        shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("reader_back_button"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnDark)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = mangaTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OnDark
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.bodySmall.copy(color = Muted),
                        maxLines = 1,
                    )
                }
                IconButton(onClick = onToggleBookmarked) {
                    Icon(
                        imageVector = if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark",
                        tint = if (bookmarked) Accent else OnDark,
                    )
                }
                var overflowOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = OnDark)
                    }
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                        onOpenInWebView?.let { open ->
                            DropdownMenuItem(
                                text = { Text("Open in WebView") },
                                leadingIcon = { Icon(Icons.Outlined.Public, contentDescription = null) },
                                onClick = {
                                    overflowOpen = false
                                    open()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Reload chapter") },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                            onClick = {
                                overflowOpen = false
                                onReloadChapter()
                            },
                        )
                    }
                }
            }

            if (isWebtoon) {
                AnimatedVisibility(
                    visible = autoScrollExpanded,
                    enter = expandVertically() + slideInVertically { -it / 2 },
                    exit = shrinkVertically() + slideOutVertically { -it / 2 },
                ) {
                    AutoScrollControlsPanel(
                        autoScroll = autoScroll,
                        autoScrollSpeedDp = autoScrollSpeedDp,
                        onToggleAutoScroll = onToggleAutoScroll,
                        onAutoScrollSpeedChange = onAutoScrollSpeedChange,
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (autoScrollExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (autoScrollExpanded) "Collapse auto-scroll" else "Expand auto-scroll",
                            tint = OnDark.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoScrollControlsPanel(
    autoScroll: Boolean,
    autoScrollSpeedDp: Float,
    onToggleAutoScroll: () -> Unit,
    onAutoScrollSpeedChange: (Float) -> Unit,
) {
    // Nekoread stores the speed in dp/s (20..200); the slider shows yomi's 1..100 scale.
    val sliderValue = remember(autoScrollSpeedDp) {
        ((autoScrollSpeedDp - 20f) / 180f * 99f + 1f).coerceIn(1f, 100f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Auto-scroll speed",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, color = Accent),
            )
            Text(
                text = "${autoScrollSpeedDp.toInt()} dp/s",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, color = OnDark),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(OnDark.copy(alpha = 0.10f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Slider(
            value = sliderValue,
            onValueChange = { v -> onAutoScrollSpeedChange((v - 1f) / 99f * 180f + 20f) },
            valueRange = 1f..100f,
            colors = sliderAccentColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (autoScroll) Accent else Accent.copy(alpha = 0.18f))
                .clickable { onToggleAutoScroll() }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (autoScroll) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = if (autoScroll) Color(0xFF11131A) else Accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (autoScroll) "Pause" else "Start",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (autoScroll) Color(0xFF11131A) else Accent
                ),
            )
        }
    }
}

@Composable
private fun ChapterNavigatorPill(
    prevEnabled: Boolean,
    onPrevChapter: () -> Unit,
    nextEnabled: Boolean,
    onNextChapter: () -> Unit,
    currentPage: Int,
    totalPages: Int,
    onSeekPage: (Int) -> Unit,
    showPageNumber: Boolean,
) {
    val buttonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = ChromeBarColor,
        disabledContainerColor = ChromeBarColor,
        contentColor = OnDark,
        disabledContentColor = Muted,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = onPrevChapter,
            enabled = prevEnabled,
            colors = buttonColors,
        ) {
            Icon(Icons.Outlined.SkipPrevious, contentDescription = "Previous chapter")
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(ChromeBarColor)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showPageNumber) {
                Box(contentAlignment = Alignment.CenterEnd) {
                    Text(
                        text = "$currentPage",
                        style = MaterialTheme.typography.bodyMedium.copy(color = OnDark),
                    )
                    // Occupies the space of the total count so the slider doesn't shift as the
                    // current page length changes.
                    Text(
                        text = "$totalPages",
                        color = Color.Transparent,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Slider(
                value = currentPage.toFloat(),
                onValueChange = { v -> onSeekPage(v.toInt() - 1) },
                valueRange = 1f..totalPages.coerceAtLeast(1).toFloat(),
                colors = sliderAccentColors(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
                    .testTag("reader_page_slider"),
            )
            if (showPageNumber) {
                Text(
                    text = "$totalPages",
                    style = MaterialTheme.typography.bodyMedium.copy(color = OnDark),
                )
            }
        }
        FilledIconButton(
            onClick = onNextChapter,
            enabled = nextEnabled,
            colors = buttonColors,
        ) {
            Icon(Icons.Outlined.SkipNext, contentDescription = "Next chapter")
        }
    }
}

@Composable
private fun BottomReaderBar(
    onClickReadingMode: () -> Unit,
    onClickOrientation: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    onClickChapterList: () -> Unit,
    onClickSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = ChromeBarColor,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClickReadingMode) {
            Icon(Icons.Outlined.ViewComfy, contentDescription = "Reading mode", tint = OnDark)
        }
        IconButton(onClick = onClickOrientation) {
            Icon(Icons.Outlined.ScreenRotation, contentDescription = "Orientation", tint = OnDark)
        }
        IconButton(onClick = onClickCropBorder) {
            Icon(
                Icons.Outlined.Crop,
                contentDescription = "Crop borders",
                tint = if (cropEnabled) Accent else OnDark,
            )
        }
        IconButton(onClick = onClickChapterList) {
            Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "Chapter list", tint = OnDark)
        }
        IconButton(onClick = onClickSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = OnDark)
        }
    }
}

@Composable
private fun ReaderSettingsSheet(
    onDismiss: () -> Unit,
    readerMode: ReaderMode,
    onSelectReaderMode: (ReaderMode) -> Unit,
    readerFit: ReaderFit,
    onSelectReaderFit: (ReaderFit) -> Unit,
    readerOrientation: ReaderOrientation,
    onSelectReaderOrientation: (ReaderOrientation) -> Unit,
    readerBg: ReaderBg,
    onSelectReaderBg: (ReaderBg) -> Unit,
    showPageNumber: Boolean,
    onToggleShowPageNumber: () -> Unit,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: () -> Unit,
    webtoonFade: Boolean,
    onToggleWebtoonFade: () -> Unit,
    readerQuality: Int,
    onSelectReaderQuality: (Int) -> Unit,
    cropBorders: Boolean,
    onToggleCropBorders: () -> Unit,
    autoScroll: Boolean,
    autoScrollSpeedDp: Float,
    onToggleAutoScroll: () -> Unit,
    onAutoScrollSpeedChange: (Float) -> Unit,
    onResetSettings: () -> Unit,
    seriesOverrideEnabled: Boolean,
    onToggleSeriesOverride: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Reading mode", "General", "Color")
    val sheetMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetColor,
        contentColor = OnDark,
    ) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = OnDark,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(title) },
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = sheetMaxHeight)
                .padding(vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when (tab) {
                0 -> SettingsReadingModeTab(
                    readerMode = readerMode,
                    onSelectReaderMode = onSelectReaderMode,
                    readerFit = readerFit,
                    onSelectReaderFit = onSelectReaderFit,
                    seriesOverrideEnabled = seriesOverrideEnabled,
                    onToggleSeriesOverride = onToggleSeriesOverride,
                )
                1 -> SettingsGeneralTab(
                    readerBg = readerBg,
                    onSelectReaderBg = onSelectReaderBg,
                    showPageNumber = showPageNumber,
                    onToggleShowPageNumber = onToggleShowPageNumber,
                    keepScreenOn = keepScreenOn,
                    onToggleKeepScreenOn = onToggleKeepScreenOn,
                    webtoonFade = webtoonFade,
                    onToggleWebtoonFade = onToggleWebtoonFade,
                    readerQuality = readerQuality,
                    onSelectReaderQuality = onSelectReaderQuality,
                    cropBorders = cropBorders,
                    onToggleCropBorders = onToggleCropBorders,
                    autoScroll = autoScroll,
                    autoScrollSpeedDp = autoScrollSpeedDp,
                    onToggleAutoScroll = onToggleAutoScroll,
                    onAutoScrollSpeedChange = onAutoScrollSpeedChange,
                )
                2 -> SettingsColorTab(readerBg = readerBg, onSelectReaderBg = onSelectReaderBg)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = onResetSettings,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
            ) {
                Text("Reset to defaults")
            }
            Spacer(Modifier.width(12.dp))
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Accent),
            ) {
                Text("Done")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ColumnScope.SettingsReadingModeTab(
    readerMode: ReaderMode,
    onSelectReaderMode: (ReaderMode) -> Unit,
    readerFit: ReaderFit,
    onSelectReaderFit: (ReaderFit) -> Unit,
    seriesOverrideEnabled: Boolean,
    onToggleSeriesOverride: () -> Unit,
) {
    SectionCard("For this series") {
        ToggleRow(
            label = "Apply to this manga only",
            subtitle = if (seriesOverrideEnabled) {
                "Editing settings for this series"
            } else {
                "Changes apply to every series"
            },
            checked = seriesOverrideEnabled,
            onCheckedChange = { onToggleSeriesOverride() },
        )
    }
    SectionCard("Reading mode") {
        val modes = listOf(
            "Paged left-to-right" to ReaderMode.LEFT_TO_RIGHT,
            "Paged right-to-left" to ReaderMode.RIGHT_TO_LEFT,
            "Paged vertical" to ReaderMode.VERTICAL,
            "Long strip" to ReaderMode.WEBTOON,
            "Long strip with gaps" to ReaderMode.WEBTOON_GAPS,
        )
        modes.chunked(2).forEach { rowModes ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowModes.forEach { (label, mode) ->
                    ModeCard(
                        selected = mode == readerMode,
                        label = label,
                        onClick = { onSelectReaderMode(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowModes.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    SectionCard("Page fit") {
        OptionRow(
            label = "Fit Screen",
            selected = readerFit == ReaderFit.FIT,
            onClick = { onSelectReaderFit(ReaderFit.FIT) },
        )
        OptionRow(
            label = "Fit Width",
            selected = readerFit == ReaderFit.FIT_WIDTH,
            onClick = { onSelectReaderFit(ReaderFit.FIT_WIDTH) },
        )
        OptionRow(
            label = "Fit Height",
            selected = readerFit == ReaderFit.FIT_HEIGHT,
            onClick = { onSelectReaderFit(ReaderFit.FIT_HEIGHT) },
        )
    }
}

@Composable
private fun ColumnScope.SettingsGeneralTab(
    readerBg: ReaderBg,
    onSelectReaderBg: (ReaderBg) -> Unit,
    showPageNumber: Boolean,
    onToggleShowPageNumber: () -> Unit,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: () -> Unit,
    webtoonFade: Boolean,
    onToggleWebtoonFade: () -> Unit,
    readerQuality: Int,
    onSelectReaderQuality: (Int) -> Unit,
    cropBorders: Boolean,
    onToggleCropBorders: () -> Unit,
    autoScroll: Boolean,
    autoScrollSpeedDp: Float,
    onToggleAutoScroll: () -> Unit,
    onAutoScrollSpeedChange: (Float) -> Unit,
) {
    SectionCard("Display") {
        ToggleRow(
            label = "Show page number",
            checked = showPageNumber,
            onCheckedChange = { onToggleShowPageNumber() },
        )
        ToggleRow(
            label = "Keep screen on",
            checked = keepScreenOn,
            onCheckedChange = { onToggleKeepScreenOn() },
        )
    }
    SectionCard("Long strip") {
        ToggleRow(
            label = "Fade pages in",
            checked = webtoonFade,
            onCheckedChange = { onToggleWebtoonFade() },
        )
        ToggleRow(
            label = "Crop borders",
            checked = cropBorders,
            onCheckedChange = { onToggleCropBorders() },
        )
        ToggleRow(
            label = "Auto-scroll",
            checked = autoScroll,
            onCheckedChange = { onToggleAutoScroll() },
        )
        if (autoScroll) {
            Text(
                text = "Auto-scroll speed: ${autoScrollSpeedDp.toInt()} dp/s",
                style = MaterialTheme.typography.bodySmall.copy(color = Muted),
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
            val sliderValue = remember(autoScrollSpeedDp) {
                ((autoScrollSpeedDp - 20f) / 180f * 99f + 1f).coerceIn(1f, 100f)
            }
            Slider(
                value = sliderValue,
                onValueChange = { v -> onAutoScrollSpeedChange((v - 1f) / 99f * 180f + 20f) },
                valueRange = 1f..100f,
                colors = sliderAccentColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            )
        }
        Text(
            text = "Image quality",
            style = MaterialTheme.typography.bodySmall.copy(color = Muted),
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
        )
        Row(
            modifier = Modifier.padding(start = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QualityChip("Low (fast)", readerQuality == 50) { onSelectReaderQuality(50) }
            QualityChip("Medium", readerQuality == 75) { onSelectReaderQuality(75) }
            QualityChip("High (sharp)", readerQuality == 100) { onSelectReaderQuality(100) }
        }
    }
}

@Composable
private fun ColumnScope.SettingsColorTab(
    readerBg: ReaderBg,
    onSelectReaderBg: (ReaderBg) -> Unit,
) {
    SectionCard("Reader background") {
        Row(
            modifier = Modifier.padding(start = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BgChip("OLED Black", Color.Black, readerBg == ReaderBg.PURE_BLACK) { onSelectReaderBg(ReaderBg.PURE_BLACK) }
            BgChip("Dark", Color(0xFF181A24), readerBg == ReaderBg.DARK_GRAY) { onSelectReaderBg(ReaderBg.DARK_GRAY) }
            BgChip("Cream", Color(0xFFFBF0D9), readerBg == ReaderBg.CREAM) { onSelectReaderBg(ReaderBg.CREAM) }
            BgChip("White", Color.White, readerBg == ReaderBg.WHITE) { onSelectReaderBg(ReaderBg.WHITE) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "The reader background doubles as the color scheme. Color filters are not available in this reader yet.",
            style = MaterialTheme.typography.bodySmall.copy(color = Muted),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ReadingModeDialog(
    onDismiss: () -> Unit,
    readerMode: ReaderMode,
    onSelectReaderMode: (ReaderMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SheetColor,
        title = {
            Text(
                text = "Reading mode",
                fontWeight = FontWeight.Bold,
                color = OnDark,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val modes = listOf(
                    "Paged left-to-right" to ReaderMode.LEFT_TO_RIGHT,
                    "Paged right-to-left" to ReaderMode.RIGHT_TO_LEFT,
                    "Paged vertical" to ReaderMode.VERTICAL,
                    "Long strip" to ReaderMode.WEBTOON,
                    "Long strip with gaps" to ReaderMode.WEBTOON_GAPS,
                )
                modes.chunked(2).forEach { rowModes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowModes.forEach { (label, mode) ->
                            ModeCard(
                                selected = mode == readerMode,
                                label = label,
                                onClick = { onSelectReaderMode(mode); onDismiss() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowModes.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Long strip renders the whole chapter as one continuous scroll.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Muted),
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Accent),
            ) {
                Text("Done")
            }
        },
    )
}

@Composable
private fun OrientationDialog(
    onDismiss: () -> Unit,
    readerOrientation: ReaderOrientation,
    onSelectReaderOrientation: (ReaderOrientation) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SheetColor,
        title = {
            Text(
                text = "Orientation",
                fontWeight = FontWeight.Bold,
                color = OnDark,
            )
        },
        text = {
            Column {
                OptionRow(
                    label = "Auto (follow system)",
                    selected = readerOrientation == ReaderOrientation.AUTO,
                    onClick = { onSelectReaderOrientation(ReaderOrientation.AUTO) },
                )
                OptionRow(
                    label = "Portrait",
                    selected = readerOrientation == ReaderOrientation.PORTRAIT,
                    onClick = { onSelectReaderOrientation(ReaderOrientation.PORTRAIT) },
                )
                OptionRow(
                    label = "Landscape",
                    selected = readerOrientation == ReaderOrientation.LANDSCAPE,
                    onClick = { onSelectReaderOrientation(ReaderOrientation.LANDSCAPE) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Accent),
            ) {
                Text("Done")
            }
        },
    )
}

@Composable
private fun ChapterListSheet(
    onDismiss: () -> Unit,
    chapters: List<ChapterEntity>,
    activeChapterId: String,
    onSelectChapter: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetColor,
        contentColor = OnDark,
    ) {
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp),
        ) {
            items(chapters, key = { it.id }) { ch ->
                val active = ch.id == activeChapterId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) Accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable {
                            onSelectChapter(ch.id)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = ch.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (active) Accent else OnDark,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            ),
                            maxLines = 1,
                        )
                        Text(
                            text = if (ch.read) "Read" else "Unread",
                            style = MaterialTheme.typography.bodySmall.copy(color = Muted),
                        )
                    }
                    if (active) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Accent)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardColor)
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, color = Accent),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        content()
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(color = OnDark),
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(color = Muted),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ModeCard(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Accent.copy(alpha = 0.18f) else CardColor)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) Accent else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.ViewComfy,
            contentDescription = null,
            tint = if (selected) Accent else Muted,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (selected) Accent else OnDark,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) Accent else Muted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(color = OnDark),
        )
    }
}

@Composable
private fun BgChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Accent.copy(alpha = 0.18f) else CardColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (selected) Accent else OnDark
            ),
        )
    }
}

@Composable
private fun QualityChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(
            color = if (selected) Accent else OnDark
        ),
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Accent.copy(alpha = 0.18f) else CardColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun sliderAccentColors() = SliderDefaults.colors(
    thumbColor = Accent,
    activeTrackColor = Accent,
    inactiveTrackColor = Muted.copy(alpha = 0.25f),
)
