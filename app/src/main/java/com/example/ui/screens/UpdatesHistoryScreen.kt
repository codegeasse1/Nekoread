package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.MangaEntity
import com.example.diagnostics.AppScrollProbe
import com.example.ui.components.FloatingTopAppBar
import com.example.ui.components.GlassCard
import com.example.ui.components.proButtonGradient()
import com.example.ui.components.ProEmptyCard
import com.example.ui.components.ProEmptyHistoryArt
import com.example.ui.components.ProSegmented
import com.example.ui.components.ProTitle
import com.example.ui.theme.GlassCardBorder
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import com.example.ui.MainViewModel
import com.example.ui.theme.proPrimary()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesHistoryScreen(
    viewModel: MainViewModel,
    historyManga: List<MangaEntity>,
    onMangaClick: (String) -> Unit,
    onReadChapterClick: (String, String) -> Unit,
    onClearHistory: () -> Unit,
    onRemoveHistory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val tabs = listOf("History", "Updates")

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            // Floating rounded glass pill (Hikari/taskbar style), matching the bottom nav pill.
            FloatingTopAppBar {
                Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(proButtonGradient()).padding(7.dp)) {
                            Icon(Icons.Default.History, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("History ", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                                Text("& Updates", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium.copy(brush = proButtonGradient()))
                            }
                            Text("Your recent activity and latest updates", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                        }
                        IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ProSegmented(
                        options = listOf("History" to Icons.Default.History, "Updates" to Icons.Default.Notifications),
                        selected = selectedTabIndex,
                        onSelect = { selectedTabIndex = it }
                    )
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
                0 -> HistoryList(
                    historyManga = historyManga,
                    onMangaClick = onMangaClick,
                    onReadChapterClick = onReadChapterClick,
                    onRemoveHistory = onRemoveHistory
                )
                1 -> UpdatesList(
                    historyManga = historyManga,
                    onMangaClick = onMangaClick
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all history?") },
            text = { Text("This removes every title from your reading history. Library entries are kept.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearConfirm = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun HistoryList(
    historyManga: List<MangaEntity>,
    onMangaClick: (String) -> Unit,
    onReadChapterClick: (String, String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onExplore: (() -> Unit)? = null
) {
    if (historyManga.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            ProEmptyCard(
                title = "No reading",
                titleAccent = "history yet",
                body = "Start reading chapters from your library or extension sources!",
                primaryLabel = "Explore Sources",
                primaryIcon = Icons.Default.CompassCalibration,
                onPrimary = { onExplore?.invoke() },
                art = { ProEmptyHistoryArt() }
            )
        }
    } else {
        val historyListState = rememberLazyListState()
        AppScrollProbe("history", historyListState)
        LazyColumn(
            state = historyListState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(historyManga, key = { it.id }) { manga ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMangaClick(manga.id) }
                        .testTag("history_item_${manga.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = manga.coverUrl,
                            contentDescription = manga.title,
                            modifier = Modifier
                                .size(width = 54.dp, height = 72.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = manga.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = manga.lastReadChapterName ?: "Chapter 1",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = proPrimary(),
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "Page ${manga.lastReadPage}",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }

                        if (manga.lastReadChapterId != null) {
                            Button(
                                onClick = { onReadChapterClick(manga.id, manga.lastReadChapterId!!) },
                                modifier = Modifier.testTag("resume_button_${manga.id}")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume")
                            }
                        }

                        IconButton(
                            onClick = { onRemoveHistory(manga.id) },
                            modifier = Modifier.testTag("remove_history_${manga.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove from history",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdatesList(
    historyManga: List<MangaEntity>,
    onMangaClick: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Newly Released Chapters",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(historyManga, key = { "upd_${it.id}" }) { manga ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMangaClick(manga.id) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = manga.coverUrl,
                        contentDescription = manga.title,
                        modifier = Modifier
                            .size(width = 54.dp, height = 72.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = manga.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "New Chapter Released Today!",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = "New",
                        tint = proPrimary(),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
