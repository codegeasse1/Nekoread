package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.data.local.CategoryEntity
import com.example.data.local.MangaEntity
import com.example.ui.MainViewModel
import com.example.ui.components.MangaGridCard
import com.example.ui.components.MangaListCard
import com.example.ui.components.coverModelFor
import com.example.ui.theme.GlassCardBorder
import com.example.ui.theme.GlassSurface

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
    val continueManga = if (showHomeSections) {
        mangaList.asSequence()
            .filter { it.lastReadChapterId != null }
            .maxByOrNull { it.lastReadTimestamp }
    } else null
    val recentlyRead = if (continueManga != null) {
        mangaList.asSequence()
            .filter { it.lastReadChapterId != null && it.id != continueManga.id }
            .sortedByDescending { it.lastReadTimestamp }
            .take(8)
            .toList()
    } else emptyList()

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            // Rounded glass header (Tadami-style) so the whole chrome reads as frosted glass.
            Surface(
                color = GlassSurface.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                border = BorderStroke(1.dp, GlassCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    if (selectionMode) {
                        // Multi-select action bar
                        TopAppBar(
                            windowInsets = WindowInsets(0),
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                            navigationIcon = {
                                IconButton(
                                    onClick = { exitSelection() },
                                    modifier = Modifier.testTag("selection_close")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                                }
                            },
                            title = {
                                Text(
                                    text = "${selectedIds.size} selected",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        viewModel.removeFromLibrary(selectedIds.toList())
                                        exitSelection()
                                    },
                                    modifier = Modifier.testTag("remove_selected_button")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove from library")
                                }
                            }
                        )
                    } else {
                        TopAppBar(
                            windowInsets = WindowInsets(0),
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                            title = {
                                if (showSearchField) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { viewModel.setLibrarySearchQuery(it) },
                                        placeholder = { Text("Search library...") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("library_search_input"),
                                        singleLine = true,
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { viewModel.setLibrarySearchQuery("") }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Logo",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Library (${mangaList.size})",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { showSearchField = !showSearchField },
                                    modifier = Modifier.testTag("search_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = if (showSearchField) Icons.Default.Clear else Icons.Default.Search,
                                        contentDescription = "Search"
                                    )
                                }
                                IconButton(
                                    onClick = { isGridView = !isGridView },
                                    modifier = Modifier.testTag("view_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                        contentDescription = "Toggle Layout"
                                    )
                                }
                                IconButton(
                                    onClick = { showClearLibraryConfirm = true },
                                    modifier = Modifier.testTag("clear_library_button")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear library")
                                }
                            }
                        )

                        // Category Tabs Row
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                    modifier = Modifier.testTag("category_all")
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
                                    modifier = Modifier.testTag("category_${category.name}")
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
                                    modifier = Modifier.testTag("add_category_chip")
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
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 130.dp),
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
                        items(mangaList, key = { it.id }) { manga ->
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
                    LazyColumn(
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
                        items(mangaList, key = { it.id }) { manga ->
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
            text = { Text("Remove every title from your library? Your reading history is kept.") },
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

/** Tadami-style hero banner: the manga you were most recently reading, with a Resume button. */
@Composable
private fun ContinueReadingHero(
    manga: MangaEntity,
    onResume: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clickable { onOpen() }
            .testTag("continue_reading_hero"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GlassCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SubcomposeAsyncImage(
                model = coverModelFor(manga),
                contentDescription = manga.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Error -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    else -> SubcomposeAsyncImageContent()
                }
            }
            // Bottom scrim so the title/Resume stay readable over any cover.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "CONTINUE READING",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Chapter ${manga.lastReadChapterName ?: "1"}",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFB9C0D6)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Resume")
                }
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
            items(mangaList, key = { it.id }) { manga ->
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
