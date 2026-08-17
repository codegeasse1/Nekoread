package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.pm.PackageManager
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import com.example.data.local.ExtensionEntity
import com.example.data.local.ExtensionRepoEntity
import com.example.data.local.ExtensionSourceEntity
import com.example.data.local.MangaEntity
import com.example.ui.MainViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassSearchBar
import com.example.ui.components.MangaGridCard
import com.example.ui.components.MangaListCard
import com.example.ui.theme.GlassCardBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.NekoGoldBadge
import com.example.ui.theme.NekoVioletPrimary
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAB_SOURCES = 0
private const val TAB_GLOBAL = 1
private const val TAB_CATALOG = 2
private const val TAB_EXTENSIONS = 3
private const val TAB_REPOS = 4

private fun formatTimestamp(ts: Long): String =
    if (ts <= 0L) "Never fetched"
    else SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(ts))

private fun isMangaDexBacked(source: ExtensionSourceEntity): Boolean =
    source.baseUrl.contains("mangadex.org")

private fun contentWarningLabel(cw: String): String? = when (cw) {
    "CONTENT_WARNING_SAFE" -> "Safe"
    "CONTENT_WARNING_SUGGESTIVE" -> "Suggestive"
    "CONTENT_WARNING_EROTICA" -> "Erotica"
    "CONTENT_WARNING_PORN" -> "Porn"
    else -> cw.ifBlank { null }
}

/** True when a NEWER build of an installed extension exists in its repo. Compares numeric
 *  versionCodes (the same check Mihon/Tadami use for their update badges) — the display
 *  versionName is just for showing "v1 → v2". */
private fun hasUpdate(ext: ExtensionEntity): Boolean {
    if (!ext.isInstalled) return false
    val installed = ext.installedVersionCode?.toLongOrNull() ?: return false
    val available = ext.versionCode.toLongOrNull() ?: return false
    return available > installed
}

/**
 * Loads the extension's launcher icon straight from its stored APK (the same source Tadami shows)
 * without installing it. Falls back to the repo index icon URL when no APK is present or readable.
 */
private fun loadApkIcon(context: android.content.Context, apkPath: String): ImageBitmap? {
    return try {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_META_DATA) ?: return null
        val appInfo = info.applicationInfo ?: return null
        appInfo.sourceDir = apkPath
        appInfo.publicSourceDir = apkPath
        val icon = appInfo.loadIcon(pm) ?: return null
        val w = if (icon.intrinsicWidth > 0) icon.intrinsicWidth else 96
        val h = if (icon.intrinsicHeight > 0) icon.intrinsicHeight else 96
        icon.toBitmap(w, h).asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}

@Composable
private fun ExtensionIconView(
    packageName: String,
    iconUrl: String,
    name: String,
    nsfw: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val apkIcon = remember(packageName) {
        if (packageName.isNotBlank()) {
            loadApkIcon(context, File(context.filesDir, "extensions/$packageName.apk").absolutePath)
        } else {
            null
        }
    }
    if (apkIcon != null) {
        Image(bitmap = apkIcon, contentDescription = null, modifier = modifier)
    } else if (iconUrl.isNotBlank()) {
        AsyncImage(model = iconUrl, contentDescription = null, modifier = modifier)
    } else {
        Box(
            modifier = modifier.background(if (nsfw) NekoGoldBadge else NekoVioletPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    viewModel: MainViewModel,
    onMangaClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // rememberSaveable (not remember): the user's tab/source/search must survive navigating to a
    // manga detail screen and back — with plain `remember` the whole Browse composable resets to
    // the Sources tab on return, which felt like being "thrown out" of the catalog.
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(TAB_SOURCES) }
    val tabs = listOf("Sources", "Global", "Catalog", "Extensions", "Repos")

    val extensionSources: List<ExtensionSourceEntity> by viewModel.extensionSources.collectAsStateWithLifecycle()
    val extensionRepos: List<ExtensionRepoEntity> by viewModel.extensionRepos.collectAsStateWithLifecycle()
    val extensions: List<ExtensionEntity> by viewModel.extensions.collectAsStateWithLifecycle()
    val opMessage: String? by viewModel.opMessage.collectAsStateWithLifecycle()
    val opBusy: String? by viewModel.opBusy.collectAsStateWithLifecycle()

    val catalogResults: List<MangaEntity> by viewModel.catalogResults.collectAsStateWithLifecycle()
    val catalogLoading: Boolean by viewModel.catalogLoading.collectAsStateWithLifecycle()
    val catalogError: String? by viewModel.catalogError.collectAsStateWithLifecycle()
    val catalogSourceName: String by viewModel.catalogSourceName.collectAsStateWithLifecycle()
    val catalogMode: String by viewModel.catalogMode.collectAsStateWithLifecycle()
    val catalogLoadingMore: Boolean by viewModel.catalogLoadingMore.collectAsStateWithLifecycle()
    val catalogHasMore: Boolean by viewModel.catalogHasMore.collectAsStateWithLifecycle()

    val globalResults: List<MangaEntity> by viewModel.globalResults.collectAsStateWithLifecycle()
    val globalLoading: Boolean by viewModel.globalLoading.collectAsStateWithLifecycle()
    val globalError: String? by viewModel.globalError.collectAsStateWithLifecycle()
    val globalSearchedSources: Int by viewModel.globalSearchedSources.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var globalQuery by rememberSaveable { mutableStateOf("") }
    var activeSourceId by rememberSaveable { mutableStateOf("") }
    var activeSourceBaseUrl by rememberSaveable { mutableStateOf("") }
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var repoUrlInput by remember { mutableStateOf("") }
    var repoNameInput by remember { mutableStateOf("") }
    var repoToDelete by remember { mutableStateOf<ExtensionRepoEntity?>(null) }

    // Cloudflare / site verification overlay (a Dialog, so closing it keeps the user exactly here).
    var webviewTarget by remember { mutableStateOf<Pair<String, String?>?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    fun sourceUserAgent(sourceId: String): String? =
        if (sourceId.isBlank()) null
        else runCatching { viewModel.repository.sourceForManga("$sourceId:x").userAgent }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    // Show operation results (repo add/refresh/delete, install errors) in a snackbar.
    LaunchedEffect(opMessage) {
        val msg = opMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearOpMessage()
        }
    }

    // A tag/genre chip tapped on a manga detail screen routes here: open that source's catalog
    // pre-filled with the tag as the search query (e.g. "tag:Action" -> all Action manga).
    val pendingSearch by viewModel.pendingCatalogSearch.collectAsStateWithLifecycle()
    LaunchedEffect(pendingSearch) {
        val ps = pendingSearch
        if (ps != null) {
            viewModel.consumePendingCatalogSearch()
            val (sourceId, tag) = ps
            if (sourceId.isNotBlank()) {
                // Drop any cached load state so the fresh tag search always runs (never swallowed
                // by the same-key no-op guard), then jump straight to the catalog with the tag.
                viewModel.clearCatalog()
                activeSourceId = sourceId
                activeSourceBaseUrl = extensionSources.firstOrNull { it.id == sourceId }?.baseUrl ?: ""
                searchQuery = "tag:$tag"
                selectedTabIndex = TAB_CATALOG
                viewModel.loadCatalog(sourceId, "tag:$tag")
            }
        }
    }

    // Debounced real search against the active source. Tag/genre searches jump straight in via the
    // pending-search handler above (no debounce), so they're skipped here — a tag search is never
    // delayed or overwritten by a stale default-catalog reload.
    LaunchedEffect(searchQuery, activeSourceId, catalogMode) {
        if (selectedTabIndex == TAB_CATALOG && activeSourceId.isNotBlank() && !searchQuery.startsWith("tag:")) {
            delay(350)
            viewModel.loadCatalog(activeSourceId, searchQuery, 1, catalogMode)
        }
    }

    // Debounced global search across every installed source.
    LaunchedEffect(globalQuery) {
        if (selectedTabIndex == TAB_GLOBAL) {
            if (globalQuery.isBlank()) {
                viewModel.clearGlobalSearch()
            } else {
                delay(400)
                viewModel.globalSearch(globalQuery)
            }
        }
    }

    val repoNameById: Map<String, String> = extensionRepos.associate { it.id to it.name }

    // Browsing a source's catalog hides the outer "Browse & Extensions" chrome (title + tab row)
    // and shows a minimal Tadami-style bar: back arrow + the source's name.
    val inExtensionMode = selectedTabIndex == TAB_CATALOG && activeSourceId.isNotBlank()

    // While inside a source's catalog, the system back button must exit back to the Sources list
    // (exactly like the on-screen back arrow) — not pop the whole Browse tab and land on Library.
    BackHandler(enabled = inExtensionMode) {
        activeSourceId = ""
        activeSourceBaseUrl = ""
        searchQuery = ""
        selectedTabIndex = TAB_SOURCES
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (inExtensionMode) {
                // Rounded glass header for the catalog-browsing mode too.
                Surface(
                    color = GlassSurface.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                    border = BorderStroke(1.dp, GlassCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        windowInsets = WindowInsets(0),
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    activeSourceId = ""
                                    activeSourceBaseUrl = ""
                                    searchQuery = ""
                                    selectedTabIndex = TAB_SOURCES
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to sources",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        title = {
                            Text(
                                text = catalogSourceName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    )
                }
            } else {
                // Rounded glass header (Tadami-style) for the tabbed Browse chrome.
                Surface(
                    color = GlassSurface.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                    border = BorderStroke(1.dp, GlassCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        TopAppBar(
                            windowInsets = WindowInsets(0),
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                            title = {
                                Text(
                                    text = "Browse & Extensions",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        )

                        ScrollableTabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = Color.Transparent,
                            edgePadding = 8.dp
                        ) {
                            // Tadami-style badge: the Extensions tab shows how many installed
                            // extensions have a newer version available in their repo.
                            val pendingUpdates = extensions.count { hasUpdate(it) }
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = {
                                        selectedTabIndex = index
                                        if (index == TAB_CATALOG && activeSourceId.isNotBlank() &&
                                            catalogResults.isEmpty() && !catalogLoading
                                        ) {
                                            viewModel.loadCatalog(activeSourceId, searchQuery)
                                        }
                                    },
                                    text = {
                                        if (index == TAB_EXTENSIONS && pendingUpdates > 0) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(title, fontWeight = FontWeight.Bold)
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(NekoGoldBadge),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (pendingUpdates > 99) "99+" else "$pendingUpdates",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    )
                                                }
                                            }
                                        } else {
                                            Text(title, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    modifier = Modifier.testTag("browse_tab_$index")
                                )
                            }
                        }
                        HorizontalDivider(color = GlassCardBorder)
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
                TAB_SOURCES -> SourcesTabContent(
                    sources = extensionSources.filter { it.isInstalled },
                    onBrowseSource = { source ->
                        activeSourceId = source.id
                        activeSourceBaseUrl = source.baseUrl
                        searchQuery = ""
                        viewModel.loadCatalog(source.id, "")
                        selectedTabIndex = TAB_CATALOG
                    },
                    onBrowsePopular = { source ->
                        activeSourceId = source.id
                        activeSourceBaseUrl = source.baseUrl
                        searchQuery = ""
                        viewModel.loadCatalog(source.id, "", 1, "popular")
                        selectedTabIndex = TAB_CATALOG
                    },
                    onOpenWebView = { source ->
                        webviewTarget = source.baseUrl to sourceUserAgent(source.id)
                    }
                )
                TAB_GLOBAL -> GlobalSearchTabContent(
                    query = globalQuery,
                    onQueryChange = { globalQuery = it },
                    results = globalResults,
                    isLoading = globalLoading,
                    error = globalError,
                    searchedSources = globalSearchedSources,
                    onMangaClick = onMangaClick
                )
                TAB_CATALOG -> CatalogTabContent(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    results = catalogResults,
                    isLoading = catalogLoading,
                    error = catalogError,
                    sourceName = catalogSourceName,
                    sourceBaseUrl = activeSourceBaseUrl,
                    hasSource = activeSourceId.isNotBlank(),
                    minimal = inExtensionMode,
                    mode = catalogMode,
                    onModeChange = { m ->
                        viewModel.setCatalogMode(m)
                        viewModel.loadCatalog(activeSourceId, if (m == "filter") searchQuery else "", 1, m)
                    },
                    onRetry = { viewModel.loadCatalog(activeSourceId, searchQuery, 1, catalogMode) },
                    onMangaClick = onMangaClick,
                    isLoadingMore = catalogLoadingMore,
                    hasMore = catalogHasMore,
                    onLoadMore = { viewModel.loadMoreCatalog() },
                    onOpenWebView = { url ->
                        webviewTarget = url to sourceUserAgent(activeSourceId)
                    }
                )
                TAB_EXTENSIONS -> ExtensionsTabContent(
                    extensions = extensions,
                    repoNameById = repoNameById,
                    busyKey = opBusy,
                    onInstall = { ext -> viewModel.installExtension(ext.packageName, ext.repoId) },
                    onUninstall = { ext -> viewModel.uninstallExtension(ext.packageName, ext.repoId) },
                    onUpdateAll = { exts -> viewModel.updateAllExtensions(exts) }
                )
                TAB_REPOS -> ReposTabContent(
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

    webviewTarget?.let { (url, ua) ->
        WebViewDialog(
            url = url,
            userAgent = ua,
            onDismiss = {
                webviewTarget = null
                // Re-load the active source so a freshly solved cf_clearance takes effect.
                viewModel.loadCatalog(activeSourceId, searchQuery)
                // If the user was verifying from the global search tab, re-run that search too —
                // a source that just got verified will now return its results.
                if (selectedTabIndex == TAB_GLOBAL && globalQuery.isNotBlank()) {
                    viewModel.globalSearch(globalQuery)
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
    onBrowsePopular: (ExtensionSourceEntity) -> Unit,
    onOpenWebView: (ExtensionSourceEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(sources, query) {
        if (query.isBlank()) sources
        else sources.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.lang.contains(query, ignoreCase = true) ||
                it.baseUrl.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            GlassSearchBar(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search sources..."
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Available Sources (${filtered.size} of ${sources.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (sources.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No sources installed. Go to the Extensions tab, install an extension, then its sources appear here to browse.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    textAlign = TextAlign.Center
                )
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it.id }) { source ->
                GlassCard(modifier = Modifier
                    .fillMaxWidth()
                    .testTag("source_card_${source.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExtensionIconView(
                            packageName = source.extensionPkg,
                            iconUrl = source.iconUrl,
                            name = source.name,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )

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

                        // Cloudflare / site verification: open the source in the in-app WebView
                        IconButton(
                            onClick = { onOpenWebView(source) },
                            modifier = Modifier.testTag("source_webview_${source.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Cloudflare / site verification",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Tadami-style quick actions: Popular (star) and Latest (refresh) open that
                        // source's catalog directly on the corresponding tab; Browse goes to Latest.
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(
                                onClick = { onBrowseSource(source) },
                                modifier = Modifier.testTag("browse_source_${source.id}")
                            ) {
                                Text("Browse")
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row {
                                IconButton(
                                    onClick = { onBrowsePopular(source) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("source_popular_${source.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Popular",
                                        tint = NekoGoldBadge,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                TextButton(
                                    onClick = { onBrowseSource(source) },
                                    modifier = Modifier.testTag("source_latest_${source.id}")
                                ) {
                                    Text("Latest")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalSearchTabContent(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<MangaEntity>,
    isLoading: Boolean,
    error: String?,
    searchedSources: Int,
    onMangaClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            GlassSearchBar(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "Search all installed sources (e.g. \"reincarnate\")..."
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotBlank() && searchedSources > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Searching $searchedSources sources",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (query.isNotBlank() && !isLoading && error == null) {
                    Text(
                        text = "${results.size} results",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        when {
            query.isBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = NekoVioletPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Search manga, manhwa and manhua by title across every installed source at once.",
                            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

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
                            text = "Searching all installed sources...",
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
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error),
                        textAlign = TextAlign.Center
                    )
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
                        text = "No manga found for \"$query\". Try a different title.",
                        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(results, key = { it.id }) { manga ->
                        MangaListCard(
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
fun CatalogTabContent(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    results: List<MangaEntity>,
    isLoading: Boolean,
    error: String?,
    sourceName: String,
    sourceBaseUrl: String,
    hasSource: Boolean,
    minimal: Boolean = false,
    mode: String = "latest",
    onModeChange: (String) -> Unit = {},
    onRetry: () -> Unit,
    onMangaClick: (String) -> Unit,
    isLoadingMore: Boolean = false,
    hasMore: Boolean = true,
    onLoadMore: () -> Unit = {},
    onOpenWebView: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (!hasSource) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = NekoVioletPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Pick a source from the Sources tab to browse it here.",
                        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@Column
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassSearchBar(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = "Search $sourceName...",
                    modifier = if (minimal && sourceBaseUrl.isNotBlank()) Modifier.weight(1f) else Modifier
                )
                if (minimal && sourceBaseUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = { onOpenWebView(sourceBaseUrl) }) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Cloudflare check",
                            tint = NekoVioletPrimary
                        )
                    }
                }
            }

            // Tadami-style catalog tabs: Popular / Latest / Filter. The search bar above acts
            // as the "Filter" — typing a query always searches, whatever tab is active.
            // ALWAYS shown — including inside an opened extension (the user taps Popular/Latest
            // right inside the source's catalog).
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                    listOf("popular" to "Popular", "latest" to "Latest", "filter" to "Filter").forEach { (m, label) ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { onModeChange(m) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    when (m) {
                                        "popular" -> Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        "latest" -> Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        else -> Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(label)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("catalog_mode_$m")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = when {
                                searchQuery.startsWith("tag:") ->
                                    "Tag: ${searchQuery.removePrefix("tag:")} • $sourceName"
                                mode == "popular" && searchQuery.isBlank() -> "Popular in $sourceName"
                                searchQuery.isBlank() -> "Latest from $sourceName"
                                else -> "Results from $sourceName"
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onPrimaryContainer)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (sourceBaseUrl.isNotBlank()) {
                        TextButton(onClick = { onOpenWebView(sourceBaseUrl) }) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cloudflare check")
                        }
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
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry")
                        }
                        if (sourceBaseUrl.isNotBlank()) {
                            OutlinedButton(onClick = { onOpenWebView(sourceBaseUrl) }) {
                                Icon(Icons.Default.Language, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verify in WebView (Cloudflare)")
                            }
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
                val gridState = rememberLazyGridState()
                // Infinite scroll: when the user scrolls near the bottom and there are more real
                // pages (page 2, page 3, ...) to load, fetch the next one and append it.
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        val total = gridState.layoutInfo.totalItemsCount
                        last >= total - 6 && hasMore && !isLoadingMore && !isLoading
                    }
                }
                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) onLoadMore()
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    state = gridState,
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
                    if (isLoadingMore || !hasMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                } else {
                                    Text(
                                        text = "End of results",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
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
    onInstall: (ExtensionEntity) -> Unit,
    onUninstall: (ExtensionEntity) -> Unit,
    onUpdateAll: (List<ExtensionEntity>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    // Show EVERY extension from every repo (exactly like Tadami). The only de-duplication is at
    // the REPO level when adding/refreshing (the same repo added twice in different URL forms, or
    // a byte-identical mirror of an existing repo, is merged into one row) — so two repos shipping
    // the SAME package from DIFFERENT builds (e.g. keiyoushi's comix + the user's own comix) each
    // keep their own row, and nothing the user added is ever hidden behind another repo's copy.
    val filtered = remember(extensions, query) {
        if (query.isBlank()) extensions
        else extensions.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    // Tadami-style sectioning: pending updates float to the top with their own "Update all"
    // header, installed extensions follow, then everything else that's still available.
    val updatesPending = remember(filtered) { filtered.filter { hasUpdate(it) } }
    val installedStable = remember(filtered) { filtered.filter { it.isInstalled && !hasUpdate(it) } }
    val available = remember(filtered) { filtered.filter { !it.isInstalled } }
    val updatingAll = busyKey == "update_all"

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            GlassSearchBar(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search ${extensions.size} extensions..."
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Extensions (${filtered.size} of ${extensions.size}) — install to add its sources",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (extensions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No extensions loaded. Add a repository first (Repos tab), or refresh the built-in repos — the list will populate from the real repo index.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    textAlign = TextAlign.Center
                )
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (updatesPending.isNotEmpty()) {
                item(key = "header_updates") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Updates pending (${updatesPending.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = NekoGoldBadge
                        )
                        if (updatingAll) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Updating...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Button(
                                onClick = { onUpdateAll(updatesPending) },
                                modifier = Modifier.testTag("update_all_button")
                            ) {
                                Text("Update all")
                            }
                        }
                    }
                }
                items(updatesPending, key = { "${it.packageName}_${it.repoId}" }) { ext ->
                    ExtensionCardRow(
                        ext = ext,
                        repoName = repoNameById[ext.repoId] ?: "Custom Repo",
                        busyKey = busyKey,
                        onInstall = onInstall,
                        onUninstall = onUninstall
                    )
                }
            }

            if (installedStable.isNotEmpty()) {
                item(key = "header_installed") {
                    SectionLabel(text = "Installed (${installedStable.size})")
                }
                items(installedStable, key = { "inst_${it.packageName}_${it.repoId}" }) { ext ->
                    ExtensionCardRow(
                        ext = ext,
                        repoName = repoNameById[ext.repoId] ?: "Custom Repo",
                        busyKey = busyKey,
                        onInstall = onInstall,
                        onUninstall = onUninstall
                    )
                }
            }

            if (available.isNotEmpty()) {
                item(key = "header_available") {
                    SectionLabel(text = "Available (${available.size})")
                }
                items(available, key = { "av_${it.packageName}_${it.repoId}" }) { ext ->
                    ExtensionCardRow(
                        ext = ext,
                        repoName = repoNameById[ext.repoId] ?: "Custom Repo",
                        busyKey = busyKey,
                        onInstall = onInstall,
                        onUninstall = onUninstall
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ExtensionCardRow(
    ext: ExtensionEntity,
    repoName: String,
    busyKey: String?,
    onInstall: (ExtensionEntity) -> Unit,
    onUninstall: (ExtensionEntity) -> Unit
) {
    val isBusy = busyKey == "install_${ext.packageName}_${ext.repoId}" || busyKey == "uninstall_${ext.packageName}_${ext.repoId}"
    val cwLabel = contentWarningLabel(ext.contentWarning)

                GlassCard(modifier = Modifier
                    .fillMaxWidth()
                    .testTag("extension_card_${ext.packageName}")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExtensionIconView(
                                packageName = ext.packageName,
                                iconUrl = ext.iconUrl,
                                name = ext.name,
                                nsfw = ext.nsfw,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )

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
                                    text = "v${ext.versionName} (${ext.versionCode}) • $repoName${cwLabel?.let { " • $it" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (ext.isInstalled) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(
                                        color = if (hasUpdate(ext)) NekoGoldBadge else MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (hasUpdate(ext)) "Update available" else "Installed${if (ext.installError != null) " • Error" else ""}",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (ext.installError != null) MaterialTheme.colorScheme.error
                                                else if (hasUpdate(ext)) Color.White
                                                else MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                    if (hasUpdate(ext)) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "v${ext.installedVersionName} → v${ext.versionName}",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
                            } else if (ext.isInstalled && hasUpdate(ext)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Button(
                                        onClick = { onInstall(ext) },
                                        modifier = Modifier.testTag("update_${ext.packageName}_${ext.repoId}")
                                    ) {
                                        Text("Update")
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedButton(
                                        onClick = { onUninstall(ext) },
                                        modifier = Modifier.testTag("uninstall_${ext.packageName}_${ext.repoId}")
                                    ) {
                                        Text("Uninstall")
                                    }
                                }
                            } else if (ext.isInstalled) {
                                OutlinedButton(
                                    onClick = { onUninstall(ext) },
                                    modifier = Modifier.testTag("uninstall_${ext.packageName}_${ext.repoId}")
                                ) {
                                    Text("Uninstall")
                                }
                            } else {
                                Button(
                                    onClick = { onInstall(ext) },
                                    modifier = Modifier.testTag("install_${ext.packageName}_${ext.repoId}")
                                ) {
                                    Text("Install")
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

            GlassCard(modifier = Modifier
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
