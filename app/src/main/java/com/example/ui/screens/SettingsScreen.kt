package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tag
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CategoryEntity
import com.example.ui.MainViewModel
import com.example.ui.ReaderBg
import com.example.ui.ReaderMode
import com.example.ui.components.FloatingTopAppBar
import com.example.ui.components.GlassCard
import com.example.ui.components.ProDropdownPill
import com.example.ui.components.ProPrimaryButton
import com.example.ui.components.ProSettingRow
import com.example.ui.components.ProSettingsSection
import com.example.ui.components.ProTitle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import com.example.ui.theme.GlassCardBorder
import com.example.ui.theme.NekoGoldBadge
import com.example.BuildConfig
import com.example.updater.AppUpdater
import com.example.updater.UpdateDownloadService
import com.example.util.BuildInfo
import kotlinx.coroutines.launch
import com.example.ui.theme.AppAccent
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import com.example.ui.components.proButtonGradient
import com.example.ui.components.proPrimary
import androidx.compose.foundation.layout.Box

private fun readerModeLabel(mode: ReaderMode): String = when (mode) {
    ReaderMode.WEBTOON -> "Long strip"
    ReaderMode.WEBTOON_GAPS -> "Long strip with gaps"
    ReaderMode.LEFT_TO_RIGHT -> "Left to Right"
    ReaderMode.RIGHT_TO_LEFT -> "Right to Left"
    ReaderMode.VERTICAL -> "Vertical"
}

private fun readerBgLabel(bg: ReaderBg): String = when (bg) {
    ReaderBg.PURE_BLACK -> "Pure Black"
    ReaderBg.DARK_GRAY -> "Dark Gray"
    ReaderBg.CREAM -> "Cream"
    ReaderBg.WHITE -> "White"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCategoryModal by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }
    var showReaderModeDialog by remember { mutableStateOf(false) }
    var showReaderBgDialog by remember { mutableStateOf(false) }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var readerExpanded by remember { mutableStateOf(true) }
    var libraryExpanded by remember { mutableStateOf(true) }
    var dataExpanded by remember { mutableStateOf(true) }
    var updatesExpanded by remember { mutableStateOf(true) }
    var appearanceExpanded by remember { mutableStateOf(true) }
    // Bumped to recompute the update banner after a toggle / "Check now".
    var updateTick by remember { mutableIntStateOf(0) }

    val categories: List<CategoryEntity> by viewModel.categories.collectAsStateWithLifecycle()
    val readerMode: ReaderMode by viewModel.readerMode.collectAsStateWithLifecycle()
    val readerBg: ReaderBg by viewModel.readerBg.collectAsStateWithLifecycle()
    val showPageNumber: Boolean by viewModel.showPageNumber.collectAsStateWithLifecycle()
    val appAccent: AppAccent by viewModel.appAccent.collectAsStateWithLifecycle()

    // Real export: user picks where to save the backup JSON (SAF).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busyMessage = "Exporting backup..."
                try {
                    val json = viewModel.exportBackup()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray())
                    }
                    Toast.makeText(context, "Backup exported successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    busyMessage = null
                }
            }
        }
    }

    // Real import: user picks a backup JSON (SAF).
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busyMessage = "Restoring backup..."
                try {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    val error = text?.let { viewModel.importBackup(it) }
                    Toast.makeText(
                        context,
                        error ?: "Backup restored successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    busyMessage = null
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            FloatingTopAppBar {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(proButtonGradient()).padding(7.dp)) {
                        Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProTitle(text = "Settings", modifier = Modifier.weight(1f, fill = false))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Build ${BuildInfo.VERSION}", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 1)
                        }
                        Text("Customize your reading experience and manage your data", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                ProSettingsSection(
                    icon = Icons.Default.MenuBook,
                    title = "Reader",
                    subtitle = "Customize how you read your manga and manhwa",
                    expanded = readerExpanded,
                    onToggle = { readerExpanded = !readerExpanded },
                    content = {
                    ProSettingRow(
                        icon = Icons.Default.MenuBook,
                        title = "Default Reading Mode",
                        subtitle = readerModeLabel(readerMode),
                        trailing = { ProDropdownPill(readerModeLabel(readerMode), { showReaderModeDialog = true }) },
                        onClick = { showReaderModeDialog = true }
                    )
                    ProSettingRow(
                        icon = Icons.Default.Palette,
                        title = "Reader Background",
                        subtitle = readerBgLabel(readerBg),
                        trailing = { ProDropdownPill(readerBgLabel(readerBg), { showReaderBgDialog = true }) },
                        onClick = { showReaderBgDialog = true }
                    )
                    ProSettingRow(
                        icon = Icons.Default.Tag,
                        title = "Show Page Number",
                        subtitle = "Overlay the page number in the reader",
                        trailing = { Switch(checked = showPageNumber, onCheckedChange = { viewModel.setShowPageNumber(it) }) }
                    )
                }
                )
            }

            item {
                ProSettingsSection(
                    icon = Icons.Default.Folder,
                    title = "Library",
                    subtitle = "Organize and manage your library",
                    expanded = libraryExpanded,
                    onToggle = { libraryExpanded = !libraryExpanded },
                    content = {
                    ProSettingRow(
                        icon = Icons.Default.Category,
                        title = "Edit Categories",
                        subtitle = "${categories.size} custom categories",
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { showCategoryModal = true }
                    )
                }
                )
            }

            item {
                ProSettingsSection(
                    icon = Icons.Default.Palette,
                    title = "Appearance",
                    subtitle = "App theme color",
                    expanded = appearanceExpanded,
                    onToggle = { appearanceExpanded = !appearanceExpanded },
                    content = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppAccent.entries.chunked(4).forEach { rowAccents ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    rowAccents.forEach { a ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(a.buttonGradient)
                                                    .clickable { viewModel.setAppAccent(a) }
                                                    .then(
                                                        if (a == appAccent) Modifier.border(
                                                            2.dp,
                                                            Color.White,
                                                            CircleShape
                                                        ) else Modifier
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (a == appAccent) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = a.label,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (a == appAccent) MaterialTheme.colorScheme.onSurface
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = if (a == appAccent) FontWeight.Bold else FontWeight.Normal
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            }

            item {
                ProSettingsSection(
                    icon = Icons.Default.Storage,
                    title = "Data",
                    subtitle = "Backup and restore your app data",
                    expanded = dataExpanded,
                    onToggle = { dataExpanded = !dataExpanded },
                    content = {
                    ProSettingRow(
                        icon = Icons.Default.CloudUpload,
                        title = "Export Backup (JSON)",
                        subtitle = "Save library, history, categories, repos & extensions",
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { exportLauncher.launch("nekoread-backup.json") }
                    )
                    ProSettingRow(
                        icon = Icons.Default.CloudDownload,
                        title = "Restore Backup",
                        subtitle = "Import a previously exported backup file",
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { importLauncher.launch(arrayOf("application/json")) }
                    )
                }
                )
            }

            item {
                ProSettingsSection(
                    icon = Icons.Default.SystemUpdate,
                    title = "App Updates",
                    subtitle = "Keep your app up to date",
                    expanded = updatesExpanded,
                    onToggle = { updatesExpanded = !updatesExpanded },
                    content = {
                val updateInfo = remember(updateTick) { AppUpdater.currentUpdate(context) }
                val updatesEnabled = remember(updateTick) { AppUpdater.isEnabled(context) }
                var checkingNow by remember { mutableStateOf(false) }
                val updateProgress by UpdateDownloadService.progress.collectAsStateWithLifecycle()
                // Surface download errors as a Toast too — the failure would otherwise only live in
                // a notification, which is invisible without POST_NOTIFICATIONS.
                LaunchedEffect(updateProgress.error) {
                    updateProgress.error?.let {
                        Toast.makeText(context, "Update failed: $it", Toast.LENGTH_LONG).show()
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (updateInfo != null) {
                            Surface(
                                color = NekoGoldBadge,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Update available: v${BuildConfig.VERSION_NAME} → v${updateInfo.version}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row {
                                Button(
                                    onClick = {
                                        Toast.makeText(context, "Downloading update v${updateInfo.version}...", Toast.LENGTH_SHORT).show()
                                        UpdateDownloadService.start(context, updateInfo)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Update")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = { AppUpdater.viewRelease(context, updateInfo) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("View on GitHub")
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            if (updateProgress.active) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = updateProgress.message.ifBlank { "Downloading..." },
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            if (updateProgress.error != null) {
                                Text(
                                    text = "Update failed: ${updateProgress.error}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = "Updates", tint = proPrimary())
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Check for updates", fontWeight = FontWeight.Bold)
                                Text(
                                    "Notify when a new version is released",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = updatesEnabled,
                                onCheckedChange = {
                                    AppUpdater.setEnabled(context, it)
                                    updateTick++
                                }
                            )
                        }

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Check for Updates", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                Text("Get the latest version and new features", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            ProPrimaryButton(label = if (checkingNow) "..." else "Check Now", icon = Icons.Default.Download, onClick = {
                                scope.launch {
                                    checkingNow = true
                                    try {
                                        AppUpdater.runCheck(context, force = true)
                                        updateTick++
                                        val fresh = AppUpdater.currentUpdate(context)
                                        Toast.makeText(context, if (fresh != null) "Update available: v${fresh.version}" else "You're up to date", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        checkingNow = false
                                    }
                                }
                            })
                        }
                    }
                }
                }
                )
            }

            // About
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Logo",
                            tint = proPrimary(),
                            modifier = Modifier.height(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("NekoRead v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                        Text(
                            text = "A manga reader built on the Mihon and Aniyomi extension system — install real extensions and browse real sources.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Developer: codegeasse1",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline
                            ),
                            modifier = Modifier.clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/codegeasse1"))
                                )
                            }
                        )
                    }
                }
            }

            if (busyMessage != null) {
                item {
                    Text(
                        text = busyMessage!!,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showReaderModeDialog) {
        AlertDialog(
            onDismissRequest = { showReaderModeDialog = false },
            title = { Text("Default Reading Mode") },
            text = {
                Column {
                    ReaderMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setReaderMode(mode); showReaderModeDialog = false }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = readerMode == mode,
                                onClick = { viewModel.setReaderMode(mode); showReaderModeDialog = false }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(readerModeLabel(mode))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReaderModeDialog = false }) { Text("Close") }
            }
        )
    }

    if (showReaderBgDialog) {
        AlertDialog(
            onDismissRequest = { showReaderBgDialog = false },
            title = { Text("Reader Background") },
            text = {
                Column {
                    ReaderBg.entries.forEach { bg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setReaderBg(bg); showReaderBgDialog = false }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = readerBg == bg,
                                onClick = { viewModel.setReaderBg(bg); showReaderBgDialog = false }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(readerBgLabel(bg))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReaderBgDialog = false }) { Text("Close") }
            }
        )
    }

    if (showCategoryModal) {
        AlertDialog(
            onDismissRequest = { showCategoryModal = false },
            title = { Text("Manage Categories") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newCategoryInput,
                            onValueChange = { newCategoryInput = it },
                            label = { Text("Category Name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newCategoryInput.isNotBlank()) {
                                    viewModel.addCategory(newCategoryInput.trim())
                                    newCategoryInput = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    categories.forEach { cat: CategoryEntity ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat.name, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { viewModel.deleteCategory(cat.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryModal = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = proPrimary())
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
