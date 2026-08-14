package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    val libraryManga by viewModel.libraryManga.collectAsStateWithLifecycle()
    val historyManga by viewModel.readingHistory.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .border(width = 1.dp, color = GlassCardBorder)
                        .testTag("bottom_nav")
                ) {
                    bottomNavScreens.forEach { screen ->
                        NavigationBarItem(
                            icon = screen.icon,
                            label = {
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.testTag("nav_item_${screen.route}")
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = Modifier.padding(innerPadding)
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
                        navController.navigate(Screen.Browse.route)
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
                    }
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
                    }
                )
            }

            composable(
                route = "reader/{mangaId}/{chapterId}",
                arguments = listOf(
                    navArgument("mangaId") { type = NavType.StringType },
                    navArgument("chapterId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val mangaId = backStackEntry.arguments?.getString("mangaId") ?: ""
                val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""

                val mangaState by viewModel.repository.getMangaFlow(mangaId).collectAsStateWithLifecycle(initialValue = null)
                val chaptersState by viewModel.repository.getChaptersFlow(mangaId).collectAsStateWithLifecycle(initialValue = emptyList())
                val currentChapter = chaptersState.firstOrNull { it.id == chapterId }

                ReaderScreen(
                    viewModel = viewModel,
                    manga = mangaState,
                    chapter = currentChapter,
                    allChapters = chaptersState,
                    onBackClick = { navController.popBackStack() },
                    onChapterChange = { newChapterId ->
                        navController.navigate("reader/$mangaId/$newChapterId") {
                            popUpTo("reader/$mangaId/$chapterId") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
