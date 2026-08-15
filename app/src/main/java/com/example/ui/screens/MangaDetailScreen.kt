package com.example.ui.screens

import android.util.Base64
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.CategoryEntity
import com.example.data.local.ChapterEntity
import com.example.data.local.MangaEntity
import com.example.ui.MainViewModel
import com.example.ui.theme.NekoGoldBadge
import com.example.ui.theme.NekoVioletPrimary

@OptIn(ExperimentalMaterial3Api::class)
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
    var isSortAscending by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showWebview by remember { mutableStateOf(false) }

    val categories: List<CategoryEntity> by viewModel.categories.collectAsStateWithLifecycle()

    // For extension sources, the manga page URL + the exact UA its requests send, so a manually
    // solved Cloudflare challenge binds a cf_clearance that the chapter/detail requests accept.
    val verifyTarget = remember(manga.id) {
        if (manga.id.startsWith("ext_")) {
            runCatching {
                val src = viewModel.repository.sourceForManga(manga.id)
                val raw = manga.id.substringAfter(":")
                val decoded = Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP).toString(Charsets.UTF_8)
                // Manga urls are stored without a leading slash (and sometimes without a slash at
                // all, e.g. TheBlank's "serie/capitalist-harem"), so join with a "/" or the
                // WebView opens a broken URL like "https://theblank.netcapitalist-harem".
                val url = if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                    decoded
                } else {
                    src.baseUrl.trimEnd('/') + "/" + decoded.trimStart('/')
                }
                url to src.userAgent
            }.getOrNull()
        } else {
            null
        }
    }

    val sortedChapters = remember(chapters, isSortAscending) {
        if (isSortAscending) chapters.sortedBy { it.chapterNumber } else chapters.sortedByDescending { it.chapterNumber }
    }

    val firstUnreadChapter = remember(chapters) {
        chapters.sortedBy { it.chapterNumber }.firstOrNull { !it.read } ?: chapters.firstOrNull()
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
                            text = if (manga.lastReadChapterName != null) "Resume ${manga.lastReadChapterName}" else "Read Ch. ${firstUnreadChapter.chapterNumber.toInt()}",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
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
                    // Blurred Cover Background
                    AsyncImage(
                        model = manga.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(20.dp),
                        contentScale = ContentScale.Crop
                    )

                    // Gradient Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )

                    // Cover Card & Details Row
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 70.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            AsyncImage(
                                model = manga.coverUrl,
                                contentDescription = manga.title,
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(160.dp),
                                contentScale = ContentScale.Crop
                            )
                        }

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
                                Surface(
                                    color = if (manga.type == "MANHWA") NekoVioletPrimary else Color(0xFF009688),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = manga.type,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = manga.status,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (manga.status == "COMPLETED") Color(0xFF00E676) else NekoGoldBadge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
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

            // Action Bar (In Library / Category / Download)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                        Text(if (manga.inLibrary) "In Library" else "Add to Library")
                    }

                    if (manga.inLibrary) {
                        OutlinedButton(
                            onClick = { showCategoryDialog = true },
                            modifier = Modifier.testTag("category_select_button")
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = "Category")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(manga.category)
                        }
                    }

                    // Cloudflare / site verification — some sources challenge the chapter/detail
                    // pages too, so open the manga's own page here and solve it.
                    if (verifyTarget != null) {
                        OutlinedButton(
                            onClick = { showWebview = true },
                            modifier = Modifier.testTag("detail_verify_webview_button")
                        ) {
                            Icon(Icons.Default.Language, contentDescription = "Verify in WebView")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Verify in WebView")
                        }
                    }
                }
            }

            // Description / Synopsis
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Synopsis",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = manga.description,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { isDescriptionExpanded = !isDescriptionExpanded }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Genres: ${manga.genres}",
                        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary)
                    )
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
                            else -> "${chapters.size} Chapters"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    if (loadError != null && chapters.isEmpty()) {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier.testTag("retry_chapters_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry"
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { isSortAscending = !isSortAscending },
                            modifier = Modifier.testTag("sort_chapters_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort Chapters"
                            )
                        }
                    }
                }
            }

            // Chapter Rows
            items(sortedChapters, key = { it.id }) { chapter ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChapterClick(chapter.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("chapter_item_${chapter.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.toggleChapterRead(chapter.id) }
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
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (chapter.read) FontWeight.Normal else FontWeight.Bold,
                                color = if (chapter.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "${chapter.releaseDate} • ${chapter.scanlator}",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleChapterBookmark(chapter.id) }
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

    if (showWebview && verifyTarget != null) {
        WebViewDialog(
            url = verifyTarget.first,
            userAgent = verifyTarget.second,
            onDismiss = {
                showWebview = false
                // Re-fetch chapters/details after verification so the solved cookies take effect.
                onRetry()
            }
        )
    }
}
