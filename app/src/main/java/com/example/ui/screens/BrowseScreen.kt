package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ExtensionRepoEntity
import com.example.data.local.ExtensionSourceEntity
import com.example.data.local.MangaEntity
import com.example.ui.MainViewModel
import com.example.ui.components.MangaGridCard
import com.example.ui.theme.NekoGoldBadge
import com.example.ui.theme.NekoVioletPrimary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    viewModel: MainViewModel,
    onMangaClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Sources", "Catalog", "Extension Repos")

    val extensionSources: List<ExtensionSourceEntity> by viewModel.extensionSources.collectAsStateWithLifecycle()
    val extensionRepos: List<ExtensionRepoEntity> by viewModel.extensionRepos.collectAsStateWithLifecycle()

    val catalogResults: List<MangaEntity> by viewModel.catalogResults.collectAsStateWithLifecycle()
    val catalogLoading: Boolean by viewModel.catalogLoading.collectAsStateWithLifecycle()
    val catalogError: String? by viewModel.catalogError.collectAsStateWithLifecycle()
    val catalogSourceName: String by viewModel.catalogSourceName.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var activeSourceId by remember { mutableStateOf("mangadex") }
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var repoUrlInput by remember { mutableStateOf("") }
    var repoNameInput by remember { mutableStateOf("") }

    // Debounced real search against the active source.
    LaunchedEffect(searchQuery, activeSourceId) {
        if (selectedTabIndex == 1) {
            delay(350)
            viewModel.loadCatalog(activeSourceId, searchQuery)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Browse & Extensions",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                )

                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                selectedTabIndex = index
                                if (index == 1 && catalogResults.isEmpty() && !catalogLoading) {
                                    viewModel.loadCatalog(activeSourceId, searchQuery)
                                }
                            },
                            text = { Text(title, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.testTag("browse_tab_$index")
                        )
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
            when (selectedTabIndex) {
                0 -> SourcesTabContent(
                    sources = extensionSources,
                    onBrowseSource = { source ->
                        activeSourceId = source.id
                        searchQuery = ""
                        viewModel.loadCatalog(source.id, "")
                        selectedTabIndex = 1
                    }
                )
                1 -> CatalogTabContent(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    results = catalogResults,
                    isLoading = catalogLoading,
                    error = catalogError,
                    sourceName = catalogSourceName,
                    onRetry = { viewModel.loadCatalog(activeSourceId, searchQuery) },
                    onMangaClick = onMangaClick
                )
                2 -> ReposTabContent(
                    repos = extensionRepos,
                    onAddRepoClick = { showAddRepoDialog = true },
                    onDeleteRepo = { viewModel.deleteExtensionRepo(it) }
                )
            }
        }
    }

    if (showAddRepoDialog) {
        AlertDialog(
            onDismissRequest = { showAddRepoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Repo",
                        tint = NekoVioletPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Extension Repository")
                }
            },
            text = {
                Column {
                    Text(
                        text = "Paste a custom Mihon / Aniyomi extension repository index URL.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = repoUrlInput,
                        onValueChange = { repoUrlInput = it },
                        label = { Text("Repository Index URL") },
                        placeholder = { Text("https://raw.githubusercontent.com/.../index.json") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("repo_url_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repoNameInput,
                        onValueChange = { repoNameInput = it },
                        label = { Text("Repository Name (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (repoUrlInput.isNotBlank()) {
                            viewModel.addExtensionRepo(repoUrlInput.trim(), repoNameInput.trim())
                            repoUrlInput = ""
                            repoNameInput = ""
                            showAddRepoDialog = false
                        }
                    },
                    modifier = Modifier.testTag("add_repo_confirm_button")
                ) {
                    Text("Add Repository")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRepoDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SourcesTabContent(
    sources: List<ExtensionSourceEntity>,
    onBrowseSource: (ExtensionSourceEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Available Sources (${sources.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(sources, key = { it.id }) { source ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("source_card_${source.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NekoVioletPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = source.name.take(1),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Working source",
                                tint = NekoVioletPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "v${source.version} • ${source.lang.uppercase()} • ${source.sourceType}",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Live data from ${source.baseUrl}",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }

                    Button(
                        onClick = { onBrowseSource(source) },
                        modifier = Modifier.testTag("browse_source_${source.id}")
                    ) {
                        Text("Browse")
                    }
                }
            }
        }
    }
}

@Composable
fun CatalogTabContent(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    results: List<MangaEntity>,
    isLoading: Boolean,
    error: String?,
    sourceName: String,
    onRetry: () -> Unit,
    onMangaClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search $sourceName...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("catalog_search_bar")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "Latest from $sourceName" else "Results from $sourceName",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onPrimaryContainer)
                    )
                }
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading from $sourceName...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }

            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Couldn't reach $sourceName",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Button(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }
            }

            results.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No manga found. Try a different search.",
                        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(results, key = { it.id }) { manga ->
                        MangaGridCard(
                            manga = manga,
                            onClick = { onMangaClick(manga.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReposTabContent(
    repos: List<ExtensionRepoEntity>,
    onAddRepoClick: () -> Unit,
    onDeleteRepo: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Extension Repositories (${repos.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Button(
                    onClick = onAddRepoClick,
                    modifier = Modifier.testTag("add_repo_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Repo")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Repo")
                }
            }
        }

        items(repos, key = { it.id }) { repo ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("repo_card_${repo.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (repo.isOfficial) Icons.Default.Verified else Icons.Default.Public,
                        contentDescription = "Repo Icon",
                        tint = if (repo.isOfficial) NekoVioletPrimary else NekoGoldBadge,
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = repo.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = repo.url,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${repo.extensionCount} Extensions",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onPrimaryContainer)
                            )
                        }
                    }

                    if (!repo.isOfficial) {
                        IconButton(onClick = { onDeleteRepo(repo.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Repo",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
