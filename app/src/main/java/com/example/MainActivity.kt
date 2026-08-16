package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import com.example.data.source.ExtensionCoverImageFetcherFactory
import com.example.data.source.ExtensionPageImageFetcherFactory
import com.example.data.source.ExtensionPageImageKeyer
import com.example.data.source.ExtensionCoverImageKeyer
import com.example.ui.MainViewModel
import com.example.ui.screens.BrowseScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MangaDetailScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UpdatesHistoryScreen
import com.example.ui.theme.BgGradientBottom
import com.example.ui.theme.BgGradientMid
import com.example.ui.theme.BgGradientTop
import com.example.ui.theme.GlassCardBorder
import com.example.ui.theme.GlowCyan
import com.example.ui.theme.GlowViolet
import com.example.ui.theme.NekoReadTheme
import eu.kanade.tachiyomi.network.NetworkHelper

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Network stack (shared by loaded extensions + Cloudflare WebView) before anything else.
        NetworkHelper.init(applicationContext)
        // All cover thumbnails go through the SAME client the extension requests use — so they
        // carry the cf_clearance cookies (and UA) that Cloudflare-protected sources require.
        // Without this, catalog covers on CF sources always came back blank.
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(NetworkHelper.getInstance().client)
                // Memory cache sized so the reader's large decoded pages can never blow the heap
                // (covers are small thumbnails; pages also live on the disk cache below, so a
                // memory miss just re-decodes from disk instead of re-downloading).
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.12)
                        .build()
                }
                // Disk cache so a loaded cover/page stays on-device: scrolling back to a screen or
                // returning after the reader shows thumbnails instantly instead of re-downloading
                // the whole grid (which is what left covers blank/"loading" after navigation).
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("coil_disk"))
                        .maxSizePercent(0.02)
                        .build()
                }
                // Bound concurrent image fetches+decodes so the reader never fires a burst of
                // parallel requests at the CDN (which throttles every request to the host and turns
                // instant page loads into 20-30s hangs). Memory-cache hits are unaffected (they
                // don't occupy threads). 4 is plenty: visible pages + a couple ahead.
                .dispatcher(Dispatchers.IO.limitedParallelism(4))
                .components {
                    add(ExtensionPageImageFetcherFactory())
                    add(ExtensionPageImageKeyer())
                    add(ExtensionCoverImageFetcherFactory())
                    add(ExtensionCoverImageKeyer())
                }
                .crossfade(true)
                .build(),
        )

        enableEdgeToEdge()
        setContent {
            NekoReadTheme {
                // Ambient gradient + glow blobs behind the whole app: the translucent
                // glass surfaces on top pick up these colors for the frosted look.
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(BgGradientTop, BgGradientMid, BgGradientBottom)
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(380.dp)
                            .offset(x = (-110).dp, y = (-80).dp)
                            .background(GlowViolet, CircleShape)
                            .blur(90.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(440.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 80.dp, y = 100.dp)
                            .background(GlowCyan, CircleShape)
                            .blur(120.dp)
                    )
                    MainAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    object Library : Screen("library", "Library", {
        Icon(Icons.Default.CollectionsBookmark, contentDescription = "Library")
    })
    object UpdatesHistory : Screen("updates_history", "History", {
        Icon(Icons.Default.History, contentDescription = "History")
    })
    object Browse : Screen("browse", "Browse", {
        Icon(Icons.Default.Explore, contentDescription = "Browse")
    })
    object Settings : Screen("settings", "Settings", {
        Icon(Icons.Default.Settings, contentDescription = "Settings")
    })
}

@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavScreens = listOf(
        Screen.Library,
        Screen.UpdatesHistory,
        Screen.Browse,
        Screen.Settings
    )

    val showBottomBar = currentRoute in bottomNavScreens.map { it.route }

    // The reader is a true fullscreen experience (like Tadami): page content draws behind the
    // system bars, with the reader's own chrome handling the safe-area insets.
    val isReader = (currentRoute ?: "").startsWith("reader/")

    val libraryManga by viewModel.libraryManga.collectAsStateWithLifecycle()
    val historyManga by viewModel.readingHistory.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = if (isReader) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        bottomBar = {
            if (showBottomBar) {
                // Floating rounded glass nav pill (Tadami-style), not a full-width rectangle.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("bottom_nav_container")
                ) {
                    Surface(
                        shape = RoundedCornerShape(30.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f),
                        border = BorderStroke(1.dp, GlassCardBorder),
                        shadowElevation = 10.dp
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier
                                .height(64.dp)
                                .testTag("bottom_nav")
                        ) {
                            bottomNavScreens.forEach { screen ->
                                NavigationBarItem(
                                    icon = screen.icon,
                                    label = {
                                        Text(
                                            text = screen.title,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    selected = currentRoute == screen.route,
                                    onClick = {
                                        navController.navigateToTab(screen.route)
                                    },
                                    modifier = Modifier.testTag("nav_item_${screen.route}")
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = if (isReader) Modifier.fillMaxSize() else Modifier.padding(innerPadding)
        ) {
            composable(Screen.Library.route) {
                LibraryScreen(
                    viewModel = viewModel,
                    mangaList = libraryManga,
                    onMangaClick = { mangaId ->
                        navController.navigate("manga_detail/$mangaId")
                    },
                    onReadClick = { mangaId, chapterId ->
                        navController.navigate("reader/$mangaId/$chapterId")
                    },
                    onNavigateToBrowse = {
                        navController.navigateToTab(Screen.Browse.route)
                    }
                )
            }

            composable(Screen.UpdatesHistory.route) {
                UpdatesHistoryScreen(
                    viewModel = viewModel,
                    historyManga = historyManga,
                    onMangaClick = { mangaId ->
                        navController.navigate("manga_detail/$mangaId")
                    },
                    onReadChapterClick = { mangaId, chapterId ->
                        navController.navigate("reader/$mangaId/$chapterId")
                    },
                    onClearHistory = { viewModel.clearHistory() },
                    onRemoveHistory = { mangaId -> viewModel.removeHistory(mangaId) }
                )
            }

            composable(Screen.Browse.route) {
                BrowseScreen(
                    viewModel = viewModel,
                    onMangaClick = { mangaId ->
                        navController.navigate("manga_detail/$mangaId")
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }

            composable(
                route = "manga_detail/{mangaId}",
                arguments = listOf(navArgument("mangaId") { type = NavType.StringType })
            ) { backStackEntry ->
                val mangaId = backStackEntry.arguments?.getString("mangaId") ?: ""
                LaunchedEffect(mangaId) {
                    viewModel.loadMangaDetail(mangaId)
                }
                val mangaState by viewModel.repository.getMangaFlow(mangaId).collectAsStateWithLifecycle(initialValue = null)
                val chaptersState by viewModel.repository.getChaptersFlow(mangaId).collectAsStateWithLifecycle(initialValue = emptyList())
                val detailLoading by viewModel.detailLoading.collectAsStateWithLifecycle()
                val detailError by viewModel.detailError.collectAsStateWithLifecycle()

                MangaDetailScreen(
                    viewModel = viewModel,
                    manga = mangaState,
                    chapters = chaptersState,
                    isLoading = detailLoading,
                    loadError = detailError,
                    onRetry = { viewModel.loadMangaDetail(mangaId) },
                    onBackClick = { navController.popBackStack() },
                    onChapterClick = { chapterId ->
                        navController.navigate("reader/$mangaId/$chapterId")
                    },
                    onTagClick = { tag ->
                        mangaState?.sourceId?.let { viewModel.openTagSearch(it, tag) }
                        navController.navigateToTab(Screen.Browse.route)
                    }
                )
            }

            composable(
                route = "reader/{mangaId}/{chapterId}?startAtBeginning={startAtBeginning}",
                arguments = listOf(
                    navArgument("mangaId") { type = NavType.StringType },
                    navArgument("chapterId") { type = NavType.StringType },
                    navArgument("startAtBeginning") { type = NavType.BoolType; defaultValue = false }
                )
            ) { backStackEntry ->
                val mangaId = backStackEntry.arguments?.getString("mangaId") ?: ""
                val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
                val startAtBeginning = backStackEntry.arguments?.getBoolean("startAtBeginning") ?: false

                val mangaState by viewModel.repository.getMangaFlow(mangaId).collectAsStateWithLifecycle(initialValue = null)
                val chaptersState by viewModel.repository.getChaptersFlow(mangaId).collectAsStateWithLifecycle(initialValue = emptyList())
                val currentChapter = chaptersState.firstOrNull { it.id == chapterId }

                ReaderScreen(
                    viewModel = viewModel,
                    manga = mangaState,
                    chapter = currentChapter,
                    allChapters = chaptersState,
                    onBackClick = { navController.popBackStack() },
                    startAtBeginning = startAtBeginning,
                    onChapterChange = { newChapterId ->
                        // In-reader prev/next chapter navigation always starts the new chapter
                        // at its first page, never at a previously-saved position.
                        navController.navigate("reader/$mangaId/$newChapterId?startAtBeginning=true") {
                            popUpTo("reader/$mangaId/$chapterId") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

/** Switch to a bottom-nav tab, always landing on its top-level screen. */
private fun NavHostController.navigateToTab(route: String) {
    if (currentDestination?.route == route) {
        // Already on this tab â pop back to its top-level screen if we went deeper.
        popBackStack(route, inclusive = false)
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
