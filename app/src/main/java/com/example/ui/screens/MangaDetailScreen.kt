package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.CategoryEntity
import com.example.data.local.ChapterEntity
import com.example.diagnostics.AppScrollProbe
import com.example.diagnostics.cellCost
import com.example.data.local.MangaEntity
import com.example.ui.MainViewModel
import com.example.ui.theme.GlassCardBorder
import com.example.ui.theme.NekoGoldBadge
import com.example.ui.theme.NekoVioletPrimary
import com.example.util.sortChapters
import com.example.ui.components.coverModelFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


// Chapter-row text styles, hoisted out of composition: `MaterialTheme.typography.titleMedium`
// overridden exactly as Type.kt does (SansSerif / 16sp / 22sp / 0.15sp) plus the read/unread weight
// variants, and the default `bodySmall` metrics for the subtitle. Every chapter row recomposes
// while the list scrolls, so going through `.copy(...)` per row is real per-frame allocation.
private val ChapterTitleStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.15.sp
)
private val ChapterTitleReadStyle = ChapterTitleStyle.copy(fontWeight = FontWeight.Normal)
private val ChapterSubtitleStyle = TextStyle(
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.4.sp
)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MangaDetailScreen(
    viewModel: MainViewModel,
    manga: MangaEntity?,
    chapters: List<ChapterEntity>,
    isLoading: Boolean,
    loadError: String?,
    onRetry: () -> Unit,
    onBackClick: () -> Unit,
    onChapterClick: (String) -> Unit,
    onTagClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (manga == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading manga details...")
                    }
                }
                loadError != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Couldn't load this manga",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = loadError,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Button(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
                else -> Text("Manga details not found.")
            }
        }
        return
    }

    var isDescriptionExpanded by remember { mutableStateOf(false) }
    // Default to ascending (chapter 1 at the top, true reading order) â users expect the first
    // chapter first, and "Read" to open it, not the newest/last chapter at the bottom.
    var isSortAscending by remember { mutableStateOf(true) }
    var showCategoryDialog by remember { mutableStateOf(false) }

    val categories: List<CategoryEntity> by viewModel.categories.collectAsStateWithLifecycle()

    // For extension sources, the manga page URL + the exact UA its requests send, so a manually
    // solved Cloudflare challenge binds a cf_clearance that the chapter/detail requests accept.
    // The URL is built by the source itself via HttpSource.getMangaUrl â extensions store bare
    // slugs/paths as the manga url (TheBlank stores "a-naughty" whose real page is
    // "https://theblank.net/serie/a-naughty"), so a naive "baseUrl + '/' + url" join opens a 404.
    // getMangaWebUrl may hit the network (TheBlank's getMangaUrl boots tokens), so resolve off
    // the main thread when the button is tapped.
    val isExtensionManga = remember(manga.id) { manga.id.startsWith("ext_") }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var webviewTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    val coverModel = coverModelFor(manga)
    // Hoisted image requests. These used to be built inline at every call site
    // (`ImageRequest.Builder(ctx).data(coverModelFor(manga)).crossfade(true).build()`), so each
    // recomposition of the hero, and each recomposition of *every* chapter row, allocated a fresh
    // request, re-resolved the cover model, and started a new crossfade. The chapter list reuses a
    // single thumbnail for all rows, so one shared request covers the whole list. None of them
    // crossfade now: a fade is a Choreographer animation per image landing, which the
    // `[scroll chapters]` logs showed as `anim` cost. Sizes are bounded, and the hero card request
    // deliberately uses the grid key/size (`cover:<id>`, 360x500) so it reuses the exact bitmap
    // the library grid already decoded for this manga instead of decoding its own.
    val heroBgRequest = remember(manga.id, coverModel) {
        ImageRequest.Builder(ctx)
            .data(coverModel)
            .size(1080, 620)
            .memoryCacheKey("detailHero:${manga.id}")
            .diskCacheKey("cover:${manga.id}:${manga.coverUrl}")
            .build()
    }
    val heroCoverRequest = remember(manga.id, coverModel) {
        ImageRequest.Builder(ctx)
            .data(coverModel)
            .size(360, 500)
            .memoryCacheKey("cover:${manga.id}")
            .diskCacheKey("cover:${manga.id}:${manga.coverUrl}")
            .build()
    }
    val chapterThumbRequest = remember(manga.id, coverModel) {
        ImageRequest.Builder(ctx)
            .data(coverModel)
            .size(100, 140)
            .memoryCacheKey("chapterThumb:${manga.id}")
            .diskCacheKey("cover:${manga.id}:${manga.coverUrl}")
            .build()
    }
    // The hero gradient used to be rebuilt (Brush + Shader) on every recomposition of the hero
    // item; hoist it against the one theme colour it depends on.
    val heroBottomColor = MaterialTheme.colorScheme.background
    val heroGradient = remember(heroBottomColor) {
        Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.4f), heroBottomColor))
    }

    fun openVerifyWebView() {
        scope.launch {
            val src = viewModel.repository.sourceForManga(manga.id)
            val url = withContext(Dispatchers.IO) {
                runCatching { src.getMangaWebUrl(manga.id) }.getOrDefault(src.baseUrl)
            }
            webviewTarget = url to src.userAgent
        }
    }

    val readOrder = remember(chapters) {
        // Numbered chapters sort by their number; unnumbered ones by the number in their own name
        // (stable across refreshes â position/date-based ordering flipped on every re-fetch).
        sortChapters(chapters)
    }
    val sortedChapters = remember(chapters, isSortAscending, readOrder) {
        if (isSortAscending) readOrder else readOrder.asReversed()
    }

    val firstUnreadChapter = remember(readOrder) {
        readOrder.firstOrNull { !it.read } ?: readOrder.firstOrNull()
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            if (firstUnreadChapter != null) {
                FloatingActionButton(
                    onClick = { onChapterClick(firstUnreadChapter.id) },
                    containerColor = NekoVioletPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("resume_reading_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start Reading")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (manga.lastReadChapterName != null) {
                                "Resume ${manga.lastReadChapterName}"
                            } else {
                                val n = firstUnreadChapter?.chapterNumber ?: 0f
                                if (n > 0f) "Read Ch. ${n.toInt()}" else "Read First Chapter"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        val chapterListState = rememberLazyListState()
        AppScrollProbe("chapters", chapterListState)
        LazyColumn(
            state = chapterListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // Hero Section
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    // Cover background (no blur: blur on the hero forces a per-frame software/GPU
                    // re-blur while the list scrolls, which is a major scroll-stutter source on
                    // many devices — the gradient overlay below already keeps the text readable).
                    AsyncImage(
                        model = heroBgRequest,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            // The gradient overlay (so the white title/author text stays
                            // readable) is drawn by the image itself rather than by a second
                            // full-height Box: one fewer layout node per hero, and the gradient
                            // is the hoisted [heroGradient] rather than a fresh
                            // Brush.verticalGradient (and its shader) rebuilt per recomposition.
                            .drawWithContent {
                                drawContent()
                                drawRect(brush = heroGradient)
                            },
                        contentScale = ContentScale.Crop,
                    )

                    // Cover Card & Details Row
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 70.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Plain clipped image instead of a Material3 `Card(elevation = 8.dp)`:
                        // the elevation shadow is a real offscreen layer rebuilt as the hero
                        // recomposes, and all the Card added here was the 12dp rounded corners.
                        AsyncImage(
                            model = heroCoverRequest,
                            contentDescription = manga.title,
                            modifier = Modifier
                                .width(110.dp)
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = manga.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "${manga.author} • ${manga.artist}",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = manga.type,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (manga.type == "MANHWA") NekoVioletPrimary else Color(0xFF009688))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = manga.status,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (manga.status == "COMPLETED") Color(0xFF00E676) else NekoGoldBadge,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Source: ${manga.sourceName}",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                            )
                        }
                    }
                }
            }

            // Action Bar (Add to Library / Category / Verify)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (manga.inLibrary) {
                                    viewModel.toggleLibraryStatus(manga.id)
                                } else {
                                    showCategoryDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (manga.inLibrary) MaterialTheme.colorScheme.surfaceVariant else NekoVioletPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("in_library_button")
                        ) {
                            Icon(
                                imageVector = if (manga.inLibrary) Icons.Default.Check else Icons.Default.FavoriteBorder,
                                contentDescription = "Library Status"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (manga.inLibrary) "In Library" else "Add to Library",
                                maxLines = 1
                            )
                        }

                        // Cloudflare / site verification â some sources challenge the chapter/detail
                        // pages too, so open the manga's own page here and solve it.
                        if (isExtensionManga) {
                            OutlinedButton(
                                onClick = { openVerifyWebView() },
                                modifier = Modifier.testTag("detail_verify_webview_button")
                            ) {
                                Icon(Icons.Default.Language, contentDescription = "Verify in WebView")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Verify", maxLines = 1)
                            }
                        }
                    }

                    if (manga.inLibrary) {
                        OutlinedButton(
                            onClick = { showCategoryDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("category_select_button")
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = "Category")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(manga.category, maxLines = 1)
                        }
                    }
                }
            }

            // Description / Synopsis + clickable genre tags
            item {
                val genres = manga.genres
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Synopsis",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = manga.description.ifBlank { "No synopsis available for this title." },
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { isDescriptionExpanded = !isDescriptionExpanded }
                    )

                    if (genres.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Genres",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // Tapping a tag opens this source's catalog filtered to that tag/genre.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            genres.forEach { tag ->
                                // Plain clickable Box instead of `Surface(onClick = ...)`: a
                                // Surface brings its own surface modifier node, interaction
                                // source and content-colour provider for a small static chip.
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .border(1.dp, GlassCardBorder, RoundedCornerShape(16.dp))
                                        .clickable { onTagClick(tag) }
                                        .testTag("genre_tag_$tag")
                                ) {
                                    Text(
                                        text = tag,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Chapter Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            isLoading -> "Loading chapters..."
                            loadError != null && chapters.isEmpty() -> "Failed to load chapters"
                            else -> "${readOrder.size} Chapters"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    if (loadError != null && chapters.isEmpty()) {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier.testTag("retry_chapters_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { isSortAscending = !isSortAscending },
                            modifier = Modifier.testTag("sort_chapters_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort Chapters",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Chapter Rows
            items(sortedChapters, key = { it.id }) { chapter ->
                Row(
                    modifier = Modifier
                        .cellCost("chrow")
                        .fillMaxWidth()
                        .clickable { onChapterClick(chapter.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("chapter_item_${chapter.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Chimahon-style chapter row: every row carries the cover as a small
                    // thumbnail (cached on disk so it reappears instantly), and tapping the row
                    // selects that chapter. Plain `AsyncImage` over a tinted Box, not
                    // `SubcomposeAsyncImage` (a SubcomposeLayout on every row was the dominant
                    // composition cost), and the request is the screen-level [chapterThumbRequest]:
                    // one shared, non-crossfading request for the whole list, rather than one
                    // rebuilt (and one fade started) per row per recomposition.
                    Box(
                        modifier = Modifier
                            .width(50.dp)
                            .height(70.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = chapterThumbRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Plain clickable Box instead of a Material3 `IconButton`: an IconButton is a
                    // Box + minimum-interactive-size + ripple + a content-colour provider per
                    // icon, twice per row, down a list that recomposes constantly while
                    // scrolling. Same 44dp touch target, same icon and tint.
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.toggleChapterRead(chapter.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (chapter.read) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Read State",
                            tint = if (chapter.read) MaterialTheme.colorScheme.outline else NekoVioletPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chapter.name,
                            style = if (chapter.read) ChapterTitleReadStyle else ChapterTitleStyle,
                            color = if (chapter.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "${chapter.releaseDate} • ${chapter.scanlator}",
                            style = ChapterSubtitleStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.toggleChapterBookmark(chapter.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (chapter.bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (chapter.bookmarked) NekoGoldBadge else MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            }
        }
    }

    if (showCategoryDialog) {
        var selectedCat by remember { mutableStateOf(manga.category) }

        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("Set Category for '${manga.title}'") },
            text = {
                Column {
                    categories.forEach { cat: CategoryEntity ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCat = cat.name }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = selectedCat == cat.name,
                                onClick = { selectedCat = cat.name }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cat.name)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateMangaCategory(manga.id, selectedCat)
                        showCategoryDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val target = webviewTarget
    if (target != null) {
        WebViewDialog(
            url = target.first,
            userAgent = target.second,
            onDismiss = {
                webviewTarget = null
                // Re-fetch chapters/details after verification so the solved cookies take effect.
                onRetry()
            }
        )
    }
}
