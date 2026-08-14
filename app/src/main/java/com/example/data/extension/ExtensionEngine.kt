package com.example.data.extension

import com.example.data.local.CategoryEntity
import com.example.data.local.ExtensionRepoEntity
import com.example.data.local.ExtensionSourceEntity

// Static seed configuration for first launch. The repos listed here are the well-known
// Mihon/Aniyomi/Tadami-compatible repos — nothing is hardcoded beyond their index URLs.
// Extension counts are fetched live from each repo's index.json the first time it is loaded.
object ExtensionEngine {

    val defaultCategories = listOf(
        CategoryEntity("c1", "Reading", 1),
        CategoryEntity("c2", "Favorites", 2),
        CategoryEntity("c3", "Manhwa", 3),
        CategoryEntity("c4", "Manga", 4),
        CategoryEntity("c5", "Completed", 5),
        CategoryEntity("c6", "Plan to Read", 6)
    )

    /**
     * Well-known repos (verified to serve real index.json files). The extensionCount of 0 is
     * intentional — it is replaced with the real count the first time the repo index is fetched.
     */
    val defaultRepos = listOf(
        ExtensionRepoEntity(
            id = "repo_keiyoushi_extensions",
            name = "Keiyoushi Community",
            url = "https://raw.githubusercontent.com/keiyoushi/extensions/main/index.json",
            extensionCount = 0,
            lastUpdated = 0L,
            addedDate = System.currentTimeMillis()
        ),
        ExtensionRepoEntity(
            id = "repo_aniyomiorg_aniyomi_extensions",
            name = "Aniyomi Extensions",
            url = "https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo/index.json",
            extensionCount = 0,
            lastUpdated = 0L,
            addedDate = System.currentTimeMillis()
        ),
        ExtensionRepoEntity(
            id = "repo_tachiyomiorg_extensions",
            name = "Tachiyomi Archive",
            url = "https://raw.githubusercontent.com/tachiyomiorg/extensions/repo/index.json",
            extensionCount = 0,
            lastUpdated = 0L,
            addedDate = System.currentTimeMillis()
        )
    )

    /**
     * The built-in MangaDex source — implemented by data/source/MangaDexSource.kt and always
     * installed. Extension-provided sources are added at runtime when their APK is installed.
     */
    val builtinSource = ExtensionSourceEntity(
        id = "mangadex",
        name = "MangaDex",
        version = "1.0",
        lang = "en",
        iconUrl = "",
        repoId = "builtin",
        isInstalled = true,
        isNsfw = false,
        baseUrl = "https://mangadex.org",
        sourceType = "MANGA",
        sourceName = "mangadex"
    )
}
