package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CategoryEntity
import com.example.ui.MainViewModel
import com.example.ui.ReaderBg
import com.example.ui.ReaderMode
import com.example.ui.theme.NekoVioletPrimary
import kotlinx.coroutines.launch

private fun readerModeLabel(mode: ReaderMode): String = when (mode) {
    ReaderMode.WEBTOON -> "Webtoon (Continuous Vertical)"
    ReaderMode.LEFT_TO_RIGHT -> "Manga Left to Right"
    ReaderMode.RIGHT_TO_LEFT -> "Manga Right to Left"
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

    val categories: List<CategoryEntity> by viewModel.categories.collectAsStateWithLifecycle()
    val readerMode: ReaderMode by viewModel.readerMode.collectAsStateWithLifecycle()
    val readerBg: ReaderBg by viewModel.readerBg.collectAsStateWithLifecycle()
    val showPageNumber: Boolean by viewModel.showPageNumber.collectAsStateWithLifecycle()

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
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
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
            // Reader
            item {
                Text(
                    text = "Reader",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingRow(
                            icon = Icons.Default.MenuBook,
                            title = "Default Reading Mode",
                            subtitle = readerModeLabel(readerMode),
                            onClick = { showReaderModeDialog = true }
                        )

                        SettingRow(
                            icon = Icons.Default.Palette,
                            title = "Reader Background",
                            subtitle = readerBgLabel(readerBg),
                            onClick = { showReaderBgDialog = true }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Tag, contentDescription = "Page Number", tint = NekoVioletPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Show Page Number", fontWeight = FontWeight.Bold)
                                Text("Overlay the page number in the reader", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = showPageNumber,
                                onCheckedChange = { viewModel.setShowPageNumber(it) }
                            )
                        }
                    }
                }
            }

            // Library
            item {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingRow(
                            icon = Icons.Default.Category,
                            title = "Edit Categories",
                            subtitle = "${categories.size} custom categories",
                            onClick = { showCategoryModal = true }
                        )
                    }
                }
            }

            // Data
            item {
                Text(
                    text = "Data",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingRow(
                            icon = Icons.Default.CloudUpload,
                            title = "Export Backup (JSON)",
                            subtitle = "Save library, history, categories, repos & extensions",
                            onClick = { exportLauncher.launch("nekoread-backup.json") }
                        )

                        SettingRow(
                            icon = Icons.Default.CloudDownload,
                            title = "Restore Backup",
                            subtitle = "Import a previously exported backup file",
                            onClick = { importLauncher.launch(arrayOf("application/json")) }
                        )
                    }
                }
            }

            // About
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Logo",
                            tint = NekoVioletPrimary,
                            modifier = Modifier.height(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("NekoRead v1.0.0", fontWeight = FontWeight.Bold)
                        Text(
                            text = "A manga reader built on the Tadami/Mihon extension system — install real extensions and browse real sources.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        Icon(icon, contentDescription = title, tint = NekoVioletPrimary)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
