package com.example.readerbridge

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.local.ChapterEntity
import io.aatricks.easyreader.ui.ExploreRoute
import io.aatricks.easyreader.ui.LibraryRoute
import io.aatricks.easyreader.ui.ReaderRoute
import io.aatricks.easyreader.ui.ScrollRoute
import io.aatricks.easyreader.ui.SettingsRoute
import io.aatricks.easyreader.ui.screens.ReaderScreen
import io.aatricks.easyreader.ui.theme.NovelScraperTheme
import io.aatricks.easyreader.ui.viewmodel.LibraryViewModel
import io.aatricks.easyreader.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.launch

/**
 * Hosts the vendored reader engine's own reader — its NavHost, ReaderScreen, theme, settings and
 * library — fed from Nekoread's chapter data through [NekoreadChapterBridge]. This is the engine's
 * reader code running unmodified; Nekoread only supplies the HTML the reader opens.
 */
@Composable
fun NekoreadReaderHost(
    mangaTitle: String,
    chapters: List<ChapterEntity>,
    currentIndex: Int, // 1-based position of the chapter in `chapters`
    fetchPages: suspend (String) -> List<String>
) {
    val readerViewModel: ReaderViewModel = hiltViewModel()
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val hostViewModel: ReaderHostViewModel = hiltViewModel()
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by readerViewModel.uiState.collectAsState()

    LaunchedEffect(currentIndex) {
        // Make the engine's Chapters sheet list this manga's chapters (its own library DB).
        NekoreadChapterBridge.seedLibrary(hostViewModel.libraryRepository, mangaTitle, chapters, context)
        // Write bridge files for the current chapter and its neighbours so prev/next work.
        NekoreadChapterBridge.ensureWindow(context, chapters, currentIndex, fetchPages)
        // Background: write the rest so the Chapters sheet can jump anywhere.
        scope.launch {
            NekoreadChapterBridge.writeAllChapters(context, chapters, fetchPages)
        }
        val url = NekoreadChapterBridge.fileUrl(context, currentIndex)
        val itemId = hostViewModel.libraryRepository.getItemByUrl(url)?.id
        readerViewModel.loadContent(url, libraryItemId = itemId)
    }

    // When the engine navigates inside the reader (prev/next), keep the bridge-file window warm so
    // the next navigation always lands on an already-written file.
    val currentUrl = uiState.content?.url
    LaunchedEffect(currentUrl) {
        val idx = NekoreadChapterBridge.indexFromUrl(currentUrl) ?: return@LaunchedEffect
        if (idx != currentIndex) {
            NekoreadChapterBridge.ensureWindow(context, chapters, idx, fetchPages)
        }
    }

    val appearanceSettings by hostViewModel.preferencesManager.appearanceSettings.collectAsState()
    val darkTheme = when (appearanceSettings.themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }

    NovelScraperTheme(
        darkTheme = darkTheme,
        dynamicColor = appearanceSettings.dynamicColor,
        accentTheme = uiState.accentTheme
    ) {
        NavHost(navController = navController, startDestination = ReaderRoute) {
            composable<ReaderRoute> {
                ReaderScreen(
                    readerViewModel = readerViewModel,
                    libraryViewModelProvider = { libraryViewModel },
                    navController = navController,
                    onOpenFilePicker = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
            // The engine's other screens (its own web-library / explore / painting canvas) aren't
            // wired to Nekoread's data; keep the routes so the reader's internal navigation never crashes.
            composable<LibraryRoute> {
                BridgePlaceholder("Library", onBack = { navController.popBackStack() })
            }
            composable<ExploreRoute> {
                BridgePlaceholder("Explore", onBack = { navController.popBackStack() })
            }
            composable<SettingsRoute> {
                BridgePlaceholder("Settings", onBack = { navController.popBackStack() })
            }
            composable<ScrollRoute> {
                BridgePlaceholder("Scroll", onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun BridgePlaceholder(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Not available in Nekoread's reader")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Back") }
        }
    }
}
