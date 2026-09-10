package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.CategoryEntity
import com.example.data.local.MangaEntity
import com.example.diagnostics.AppDiagnostics
import com.example.diagnostics.AppScrollProbe
import com.example.diagnostics.cellCost
import com.example.ui.MainViewModel
import com.example.ui.components.FloatingTopAppBar
import com.example.ui.components.GlassSearchBar
import com.example.ui.components.MangaGridCard
import com.example.ui.components.MangaListCard
import com.example.ui.components.coverModelFor
import com.example.ui.theme.GlassCardBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    mangaList: List<MangaEntity>,
    onMangaClick: (String) -> Unit,
    onReadClick: (String, String) -> Unit,
    onNavigateToBrowse: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppDiagnostics.noteCompose("library")
    var isGridView by remember { mutableStateOf(true) }
    var showSearchField by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryText by remember { mutableStateOf("") }

    // Long-press multi-select removal (Tadami-style): long-press a card to enter selection mode,
    // tap to toggle more, then remove the selected titles from the library.
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var showClearLibraryConfirm by remember { mutableStateOf(false) }

    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
    }

    fun toggleSelect(id: String) {
        if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
        if (selectedIds.isEmpty()) selectionMode = false
    }

    val searchQuery by viewModel.librarySearchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val categories: List<CategoryEntity> by viewModel.categories.collectAsStateWithLifecycle()

    // Tadami-style home: a "Continue Reading" hero banner on top, a horizontal "Recently Read"
    // row beneath it, then the full library grid below. Only shown on the unfiltered library
    // view (no search, "All" category, not in selection mode).
    val showHomeSections = !selectionMode && searchQuery.isBlank() && selectedCategory == "All"
    // Remembered: this scans (and sorts) the whole library, and computing it inline on every
    // recomposition put that work on the main thread while the grid was scrolling.
    val continueManga = remember(mangaList, showHomeSections) {
        if (showHomeSections) {
            mangaList.asSequence()
                .filter { it.lastReadChapterId != null }
                .maxByOrNull { it.lastReadTimestamp }
        } else null
    }
    val recentlyRead = remember(mangaList, continueManga) {
        val hero = continueManga
        if (hero != null) {
            mangaList.asSequence()
                .filter { it.lastReadChapterId != null && it.id != hero.id }
                .sortedByDescending { it.lastReadTimestamp }
                .take(8)
                .toList()
        } else emptyList()
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            // Floating rounded glass pill (Hikari/taskbar style), matching the bottom nav pill.
            FloatingTopAppBar {
                // Compact two-row header: title + actions on the first row, category chips on their
                // own full-width row below so every option stays visible and nothing gets clipped.
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(horizontal = 4.dp)
                    ) {
                        if (selectionMode) {
                            IconButton(
                                onClick = { exitSelection() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("selection_close")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel")
                            }
                            Text(
                                text = "${selectedIds.size} selected",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    viewModel.removeFromLibrary(selectedIds.toList())
                                    exitSelection()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("remove_selected_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove from library")
                            }
                        } else {
                            if (showSearchField) {
                                GlassSearchBar(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setLibrarySearchQuery(it) },
                                    placeholder = "Search library...",
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("library_search_input")
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Logo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Library (${mangaList.size})",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { showSearchField = !showSearchField },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("search_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (showSearchField) Icons.Default.Clear else Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            }
                            IconButton(
                                onClick = { isGridView = !isGridView },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("view_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle Layout"
                                )
                            }
                            IconButton(
                                onClick = { showClearLibraryConfirm = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("clear_library_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear library")
                            }
                        }
                    }

                    if (!selectionMode) {
                        // Full-width category chips row — several fit at once, the rest scroll.
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedCategory == "All",
                                    onClick = { viewModel.setSelectedCategory("All") },
                                    label = { Text("All") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .height(30.dp)
                                        .testTag("category_all")
                                )
                            }

                            items(categories) { category: CategoryEntity ->
                                FilterChip(
                                    selected = selectedCategory == category.name,
                                    onClick = { viewModel.setSelectedCategory(category.name) },
                                    label = { Text(category.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .height(30.dp)
                                        .testTag("category_${category.name}")
                                )
                            }

                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = { showAddCategoryDialog = true },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Add Category",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("New")
                                        }
                                    },
                                    modifier = Modifier
                                        .height(30.dp)
                                        .testTag("add_category_chip")
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(
                    onClick = onNavigateToBrowse,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                modifier = Modifier.testTag("browse_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Manga")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Explore", fontWeight = FontWeight.Bold)
                }
            }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (mangaList.isEmpty()) {
                // Empty State Illustration
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Empty Library",
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No results found for '$searchQuery'" else "Your library is empty",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Search the MangaDex catalog (via the Explore button) to add real manga and manhwa to your library.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onNavigateToBrowse,
                        modifier = Modifier.testTag("empty_explore_button")
                    ) {
                        Text("Browse Extension Catalog")
                    }
                }
            } else {
                if (isGridView) {
                    val libraryGridState = rememberLazyGridState()
                    AppScrollProbe("library", libraryGridState)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 130.dp),
                        state = libraryGridState,
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (continueManga != null) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ContinueReadingHero(
                                    manga = continueManga,
                                    onResume = {
                                        continueManga.lastReadChapterId?.let { onReadClick(continueManga.id, it) }
                                    },
                                    onOpen = { onMangaClick(continueManga.id) }
                                )
                            }
                            if (recentlyRead.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    RecentlyReadRow(
                                        mangaList = recentlyRead,
                                        onMangaClick = onMangaClick,
                                        onReadClick = onReadClick
                                    )
                                }
                            }
                        }
                        items(mangaList, key = { it.id }, contentType = { "manga" }) { manga ->
                            MangaGridCard(
                                manga = manga,
                                onClick = {
                                    if (selectionMode) toggleSelect(manga.id) else onMangaClick(manga.id)
                                },
                                onReadClick = if (!selectionMode && manga.lastReadChapterId != null) {
                                    { onReadClick(manga.id, manga.lastReadChapterId!!) }
                                } else null,
                                selected = manga.id in selectedIds,
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedIds.add(manga.id)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    val libraryListState = rememberLazyListState()
                    AppScrollProbe("library", libraryListState)
                    LazyColumn(
                        state = libraryListState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (continueManga != null) {
                            item {
                                ContinueReadingHero(
                                    manga = continueManga,
                                    onResume = {
                                        continueManga.lastReadChapterId?.let { onReadClick(continueManga.id, it) }
                                    },
                                    onOpen = { onMangaClick(continueManga.id) }
                                )
                            }
                            if (recentlyRead.isNotEmpty()) {
                                item {
                                    RecentlyReadRow(
                                        mangaList = recentlyRead,
                                        onMangaClick = onMangaClick,
                                        onReadClick = onReadClick
                                    )
                                }
                            }
                        }
                        items(mangaList, key = { it.id }, contentType = { "manga" }) { manga ->
                            MangaListCard(
                                manga = manga,
                                onClick = {
                                    if (selectionMode) toggleSelect(manga.id) else onMangaClick(manga.id)
                                },
                                selected = manga.id in selectedIds,
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedIds.add(manga.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Add New Category") },
            text = {
                OutlinedTextField(
                    value = newCategoryText,
                    onValueChange = { newCategoryText = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCategoryText.isNotBlank()) {
                            viewModel.addCategory(newCategoryText.trim())
                            newCategoryText = ""
                            showAddCategoryDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearLibraryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearLibraryConfirm = false },
            title = { Text("Clear library?") },
            text = { Text("Remove every title from your library? Reading history for those titles is cleared too.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearLibrary()
                        showClearLibraryConfirm = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLibraryConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Hoisted out of the hero composable: an identical `Brush` allocation (and shader) per recomposition
// is pure waste, and this one never changes.
private val heroScrim = Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))
private val HeroShape = RoundedCornerShape(22.dp)
private val HeroResumeShape = RoundedCornerShape(20.dp)
// The hero's four text styles, hoisted exactly as Type.kt defines them (labelLarge and
// headlineSmall are NOT overridden by the theme, so they keep Material's default metrics;
// bodyMedium IS overridden). Rebuilding `MaterialTheme.typography.x.copy(...)` per composition
// also allocates a TextStyle and walks the CompositionLocal.
private val HeroKickerStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp,
)
private val HeroTitleStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 32.sp,
    letterSpacing = 0.sp,
    color = Color.White,
)
private val HeroChapterStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.2.sp,
    color = Color(0xFFB9C0D6),
)
private val HeroResumeStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp,
    color = Color.White,
)


/** Tadami-style hero banner: the manga you were most recently reading, with a Resume button. */
@Composable
private fun ContinueReadingHero(
    manga: MangaEntity,
    onResume: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppDiagnostics.noteCompose("hero")
    // Plain surface instead of Material3 `Card`: a Card is a pointerInput + `Modifier.surface`
    // + elevation shadow + a CompositionLocalProvider for its content colour — several nodes per
    // hero for what is a static rounded rectangle. The app-level frame logs showed the library
    // hero costing ~140ms to compose (`msg 143ms composed=herox1` -> `frame anim=145ms`), and the
    // hero is the one big cell that is always on screen, so cutting all of it is worth it.
    Box(
        modifier = modifier
            .cellCost("hero")
            .fillMaxWidth()
            .height(210.dp)
            .clip(HeroShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, GlassCardBorder, HeroShape)
            .clickable { onOpen() }
            .testTag("continue_reading_hero")
    ) {
        val ctx = LocalContext.current
        val coverModel = coverModelFor(manga)
        // Bounded request + plain `AsyncImage` instead of `SubcomposeAsyncImage` with no size:
        // this is the widest cell on the screen, so an unbounded model decoded and uploaded far
        // more pixels than it can display. The tinted Box under it is the placeholder/error state.
        val heroRequest = remember(manga.id, coverModel) {
            ImageRequest.Builder(ctx)
                .data(coverModel)
                .size(1080, 560)
                // Its own memory-cache key: shared with the grid/list "cover:<id>" key it would
                // hand a 1080x560 bitmap to a 130dp grid cell (and keep ~600k pixels resident
                // per manga) — the grid must cache its own small bitmap.
                .memoryCacheKey("cover-hero:${manga.id}")
                .diskCacheKey("cover:${manga.id}:${manga.coverUrl}")
                .build()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        AsyncImage(
            model = heroRequest,
            contentDescription = manga.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                // The bottom scrim (so the title/Resume stay readable over any cover) rides the
                // image's own draw instead of a second full-size Box: one fewer layout node per
                // hero, and the gradient shader is the hoisted [heroScrim] rather than a
                // per-composition `background(brush)`.
                .drawWithContent {
                    drawContent()
                    drawRect(brush = heroScrim)
                }
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "CONTINUE READING",
                style = HeroKickerStyle.copy(color = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = manga.title,
                style = HeroTitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Chapter ${manga.lastReadChapterName ?: "1"}",
                style = HeroChapterStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Hand-drawn pill instead of a Material3 `Button`: a Button is a Surface + Row +
            // minimum-interactive-size + ripple indication + a content-colour
            // CompositionLocalProvider for a single label. Same primary pill with a white label,
            // now one clickable Box + Text.
            Box(
                modifier = Modifier
                    .clip(HeroResumeShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onResume() }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "▶ Resume", style = HeroResumeStyle)
            }
        }
    }
}

/** Tadami-style horizontal row of recently read titles, shown under the hero banner. */
@Composable
private fun RecentlyReadRow(
    mangaList: List<MangaEntity>,
    onMangaClick: (String) -> Unit,
    onReadClick: (String, String) -> Unit
) {
    AppDiagnostics.noteCompose("recentRow")
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Recently Read",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(mangaList, key = { it.id }, contentType = { "manga" }) { manga ->
                MangaGridCard(
                    manga = manga,
                    onClick = { onMangaClick(manga.id) },
                    onReadClick = manga.lastReadChapterId?.let { { onReadClick(manga.id, it) } },
                    modifier = Modifier.width(120.dp)
                )
            }
        }
    }
}
