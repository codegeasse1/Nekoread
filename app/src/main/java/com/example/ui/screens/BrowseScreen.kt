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
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import coil.compose.AsyncImage
import com.example.data.local.ExtensionEntity
import com.example.data.local.ExtensionRepoEntity
import com.example.data.local.ExtensionSourceEntity
import com.example.data.local.MangaEntity
import com.example.ui.MainViewModel
import com.example.ui.components.MangaGridCard
import com.example.ui.theme.NekoGoldBadge
import com.example.ui.theme.NekoVioletPrimary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTimestamp(ts: Long): String =
    if (ts <= 0L) "Never fetched"
    else SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(ts))

private fun isMangaDexBacked(source: ExtensionSourceEntity): Boolean =
    source.baseUrl.contains("mangadex.org")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    viewModel: MainViewModel,
    onMangaClick: (String) -> Unit,
    onOpenWebView: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Sources", "Catalog", "Extensions", "Extension Repos")

    val extensionSources: List<ExtensionSourceEntity> by viewModel.extensionSources.collectAsStateWithLifecycle()
    val extensionRepos: List<ExtensionRepoEntity> by viewModel.extensionRepos.collectAsStateWithLifecycle()
    val extensions: List<ExtensionEntity> by viewModel.extensions.collectAsStateWithLifecycle()
    val opMessage: String? by viewModel.opMessage.collectAsStateWithLifecycle()
    val opBusy: String? by viewModel.opBusy.collectAsStateWithLifecycle()

    val catalogResults: List<MangaEntity> by viewModel.catalogResults.collectAsStateWithLifecycle()
    val catalogLoading: Boolean by viewModel.catalogLoading.collectAsStateWithLifecycle()
    val catalogError: String? by viewModel.catalogError.collectAsStateWithLifecycle()
    val catalogSourceName: String by viewModel.catalogSourceName.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var activeSourceId by remember { mutableStateOf("mangadex") }
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var repoUrlInput by remember { mutableStateOf("") }
    var repoNameInput by remember { mutableStateOf("") }
    var repoToDelete by remember { mutableStateOf<ExtensionRepoEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Show operation results (repo add/refresh/delete, install errors) in a snackbar.
    LaunchedEffect(opMessage) {
        val msg = opMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearOpMessage()
        }
    }

    // Debounced real search against the active source.
    LaunchedEffect(searchQuery, activeSourceId) {
        if (selectedTabIndex == 1) {
            delay(350)
            viewModel.loadCatalog(activeSourceId, searchQuery)
        }
    }

    val repoNameById: Map<String, String> = extensionRepos.associate { it.id to it.name }

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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTabIndex) {
                0 -> SourcesTabContent(
                    sources = extensionSources.filter { it.isInstalled },
                    onBrowseSource = { source ->
                        activeSourceId = source.id
                        searchQuery = ""
                        viewModel.loadCatalog(source.id, "")
                        selectedTabIndex = 1
                    },
                    onOpenWebView = { source ->
                        val url = source.baseUrl.ifBlank { "https://mangadex.org" }
                        onOpenWebView(url)
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
                2 -> ExtensionsTabContent(
                    extensions = extensions,
                    repoNameById = repoNameById,
                    busyKey = opBusy,
                    onInstall = { viewModel.installExtension(it) },
                    onUninstall = { viewModel.uninstallExtension(it) }
                )
                3 -> ReposTabContent(
                    repos = extensionRepos,
                    busyKey = opBusy,
                    onAddRepoClick = { showAddRepoDialog = true },
                    onRefreshRepo = { viewModel.refreshExtensionRepo(it) },
                    onDeleteRepo = { repoToDelete = it }
                )
            }
        }
    }

    if (showAddRepoDialog) {
        AddRepoDialog(
            url = repoUrlInput,
            name = repoNameInput,
            onUrlChange = { repoUrlInput = it },
            onNameChange = { repoNameInput = it },
            isBusy = opBusy == "repo_add",
            onConfirm = {
                if (repoUrlInput.isNotBlank()) {
                    viewModel.addExtensionRepo(repoUrlInput.trim(), repoNameInput.trim())
                    repoUrlInput = ""
                    repoNameInput = ""
                    showAddRepoDialog = false
                }
            },
            onDismiss = { showAddRepoDialog = false }
        )
    }

    repoToDelete?.let { repo ->
        AlertDialog(
            onDismissRequest = { repoToDelete = null },
            title = { Text("Delete repository?") },
            text = {
                Text(
                    "\"${repo.name}\" and its ${repo.extensionCount} extensions will be removed from this app. " +
                        "Installed extension files will be deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExtensionRepo(repo.id)
                        repoToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { repoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AddRepoDialog(
    url: String,
    name: String,
    onUrlChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    text = "Paste any Mihon / Aniyomi / Tadami extension repo index URL. It is fetched " +
                        "over the network and its real extension list is shown in the Extensions tab.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text("Repository Index URL") },
                    placeholder = { Text("https://raw.githubusercontent.com/.../index.json") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("repo_url_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Repository Name (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isBusy) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Fetching repository index...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = url.isNotBlank() && !isBusy,
                modifier = Modifier.testTag("add_repo_confirm_button")
            ) {
                Text(if (isBusy) "Fetching..." else "Add Repository")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SourcesTabContent(
    sources: List<ExtensionSourceEntity>,
    onBrowseSource: (ExtensionSourceEntity) -> Unit,
    onOpenWebView: (ExtensionSourceEntity) -> Unit
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

        if (sources.isEmpty()) {
            item {
                Text(
                    text = "Install an extension to add more sources. The built-in MangaDex source is always available.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
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
                    if (source.iconUrl.isNotBlank()) {
                        AsyncImage(
                            model = source.iconUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
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
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (isMangaDexBacked(source)) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Working source",
                                    tint = NekoVioletPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "v${source.version} • ${source.lang.uppercase()}${if (source.isNsfw) " • NSFW" else ""}",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (isMangaDexBacked(source)) "Live data from MangaDex" else source.baseUrl,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isMangaDexBacked(source)) {
                        Button(
                            onClick = { onBrowseSource(source) },
                            modifier = Modifier.testTag("browse_source_${source.id}")
                        ) {
                            Text("Browse")
                        }
                    } else {
                        OutlinedButton(onClick = { onOpenWebView(source) }) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open in WebView")
                        }
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
fun ExtensionsTabContent(
    extensions: List<ExtensionEntity>,
    repoNameById: Map<String, String>,
    busyKey: String?,
    onInstall: (String) -> Unit,
    onUninstall: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column {
                Text(
                    text = "Extensions (${extensions.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Install an extension to add its sources to the Sources tab. " +
                        "Each extension is downloaded from its repo and verified before installing.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }

        if (extensions.isEmpty()) {
            item {
                Text(
                    text = "No extensions loaded. Add a repository first (Extension Repos tab), or " +
                        "refresh the built-in repos — the list will populate from the real repo index.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }

        items(extensions, key = { it.packageName }) { ext ->
            val repoName = repoNameById[ext.repoId] ?: "Custom Repo"
            val isBusy = busyKey == "install_${ext.packageName}" || busyKey == "uninstall_${ext.packageName}"

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("extension_card_${ext.packageName}")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ext.iconUrl.isNotBlank()) {
                            AsyncImage(
                                model = ext.iconUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (ext.nsfw) NekoGoldBadge else NekoVioletPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ext.name.take(1),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = ext.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (ext.nsfw) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = NekoGoldBadge,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "NSFW",
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "v${ext.versionName} (${ext.versionCode}) • $repoName",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (ext.contentWarning.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = ext.contentWarning,
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (ext.isInstalled) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Installed${if (ext.installError != null) " • Error" else ""}",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (ext.installError != null) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }

                            if (ext.installError != null && !ext.isInstalled) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = ext.installError,
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        } else if (ext.isInstalled) {
                            OutlinedButton(
                                onClick = { onUninstall(ext.packageName) },
                                modifier = Modifier.testTag("uninstall_${ext.packageName}")
                            ) {
                                Text("Uninstall")
                            }
                        } else {
                            Button(
                                onClick = { onInstall(ext.packageName) },
                                modifier = Modifier.testTag("install_${ext.packageName}")
                            ) {
                                Text("Install")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReposTabContent(
    repos: List<ExtensionRepoEntity>,
    busyKey: String?,
    onAddRepoClick: () -> Unit,
    onRefreshRepo: (String) -> Unit,
    onDeleteRepo: (ExtensionRepoEntity) -> Unit
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
                Column {
                    Text(
                        text = "Extension Repositories (${repos.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (busyKey == "repo_add") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Fetching repository...",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }

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

        if (repos.isEmpty()) {
            item {
                Text(
                    text = "No repositories added. Use \"Add Repo\" to add any Mihon / Aniyomi / Tadami " +
                        "extension repo by its index.json URL.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }

        items(repos, key = { it.id }) { repo ->
            val repoBusy = busyKey == "repo_refresh_${repo.id}" || busyKey == "repo_delete_${repo.id}"

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
                        imageVector = Icons.Default.Public,
                        contentDescription = "Repo Icon",
                        tint = NekoVioletPrimary,
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

                        Row(verticalAlignment = Alignment.CenterVertically) {
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

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Updated: ${formatTimestamp(repo.lastUpdated)}",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }

                    if (repoBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    } else {
                        IconButton(
                            onClick = { onRefreshRepo(repo.id) },
                            modifier = Modifier.testTag("refresh_repo_${repo.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Repo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = { onDeleteRepo(repo) },
                            modifier = Modifier.testTag("delete_repo_${repo.id}")
                        ) {
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
