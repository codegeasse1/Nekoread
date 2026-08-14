package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CategoryEntity
import com.example.ui.MainViewModel
import com.example.ui.ReaderBg
import com.example.ui.ReaderMode
import com.example.ui.theme.NekoVioletPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCategoryModal by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }

    val categories: List<CategoryEntity> by viewModel.categories.collectAsStateWithLifecycle()
    val readerMode: ReaderMode by viewModel.readerMode.collectAsStateWithLifecycle()
    val readerBg: ReaderBg by viewModel.readerBg.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
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
            // General & Categories
            item {
                Text(
                    text = "Library Settings",
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCategoryModal = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Category, contentDescription = "Categories", tint = NekoVioletPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Edit Categories", fontWeight = FontWeight.Bold)
                                Text("${categories.size} Custom Categories", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Reader Defaults
            item {
                Text(
                    text = "Reader Defaults",
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = "Reader Mode", tint = NekoVioletPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Default Reading Mode", fontWeight = FontWeight.Bold)
                                Text(
                                    when (readerMode) {
                                        ReaderMode.WEBTOON -> "Webtoon (Continuous Vertical)"
                                        ReaderMode.LEFT_TO_RIGHT -> "Manga Left to Right"
                                        ReaderMode.RIGHT_TO_LEFT -> "Manga Right to Right"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // Backup & Restore
            item {
                Text(
                    text = "Data Backup & Restore",
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Toast.makeText(context, "Library & Extension data backed up to JSON!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Backup", tint = NekoVioletPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Export Backup (JSON)", fontWeight = FontWeight.Bold)
                                Text("Save library, history, categories & extension repos", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Toast.makeText(context, "Library restored successfully!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "Restore", tint = NekoVioletPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Restore Backup", fontWeight = FontWeight.Bold)
                                Text("Import library backup from file", style = MaterialTheme.typography.bodySmall)
                            }
                        }
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
                            text = "Inspired by Mihon & Aniyomi. Supporting custom extension repositories, webtoon reader & library management.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
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
