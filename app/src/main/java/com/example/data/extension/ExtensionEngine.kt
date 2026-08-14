package com.example.data.extension

import com.example.data.local.CategoryEntity
import com.example.data.local.ExtensionRepoEntity
import com.example.data.local.ExtensionSourceEntity

// Static seed configuration for first launch.
// The actual network behavior lives in data/source/MangaDexSource.kt —
// sources registered in SourceRegistry are the ones that really work.
object ExtensionEngine {

    val defaultCategories = listOf(
        CategoryEntity("c1", "Reading", 1),
        CategoryEntity("c2", "Favorites", 2),
        CategoryEntity("c3", "Manhwa", 3),
        CategoryEntity("c4", "Manga", 4),
        CategoryEntity("c5", "Completed", 5),
        CategoryEntity("c6", "Plan to Read", 6)
    )

    val defaultRepos = listOf(
        ExtensionRepoEntity(
            id = "mihon-official",
            name = "Mihon Official Extension Repo",
            url = "https://raw.githubusercontent.com/mihonapp/mihon-extensions/repo/index.json",
            extensionCount = 142,
            isOfficial = true
        ),
        ExtensionRepoEntity(
            id = "aniyomi-anime-manga",
            name = "Aniyomi Extensions Repository",
            url = "https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo/index.json",
            extensionCount = 98,
            isOfficial = true
        ),
        ExtensionRepoEntity(
            id = "keiyoushi-community",
            name = "Keiyoushi Community Extension Repo",
            url = "https://raw.githubusercontent.com/keiyoushi/extensions/main/index.json",
            extensionCount = 310,
            isOfficial = false
        )
    )

    // Only sources that have a working MangaSource implementation are listed here.
    // Adding a new source = implement data/source/MangaSource.kt + register it in SourceRegistry.
    val defaultSources = listOf(
        ExtensionSourceEntity(
            id = "mangadex",
            name = "MangaDex",
            version = "2.0.18",
            lang = "en",
            iconUrl = "",
            repoId = "builtin",
            isInstalled = true,
            isNsfw = false,
            baseUrl = "https://mangadex.org",
            sourceType = "MANGA"
        )
    )
}
