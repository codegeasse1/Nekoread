package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manga")
data class MangaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val coverUrl: String,
    val author: String = "Unknown",
    val artist: String = "Unknown",
    val description: String = "",
    val sourceId: String,
    val sourceName: String,
    val status: String = "ONGOING", // ONGOING, COMPLETED, HIATUS
    val type: String = "MANHWA", // MANHWA, MANGA, MANHUA
    val inLibrary: Boolean = false,
    val category: String = "Reading", // Default, Reading, Completed, Plan to Read, Favorites, Manhwa
    val lastReadChapterId: String? = null,
    val lastReadChapterName: String? = null,
    val lastReadPage: Int = 1,
    val lastReadTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val bookmarkCount: Int = 0,
    val rating: Float = 4.8f,
    val genres: String = "Action, Fantasy, System" // Comma separated
)

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey val id: String,
    val mangaId: String,
    val chapterNumber: Float,
    val name: String,
    val scanlator: String = "Official",
    val releaseDate: String = "Recently",
    val read: Boolean = false,
    val bookmarked: Boolean = false,
    val lastPageRead: Int = 1,
    val totalPages: Int = 20,
    val fetchUrl: String = "",
    val dateUpload: Long = System.currentTimeMillis()
)

/**
 * An extension repository (same concept as a Mihon/Aniyomi/Tadami repo). Everything here is real
 * data fetched from the repo's index.json — the name and extensionCount come from the server, and
 * the extension catalog is stored in the [ExtensionEntity] table.
 */
@Entity(tableName = "extension_repos")
data class ExtensionRepoEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val extensionCount: Int = 0,
    val lastUpdated: Long = 0L,
    val addedDate: Long = System.currentTimeMillis()
)

/**
 * A single extension available from a repo (e.g. "MangaDex" APK from the keiyoushi repo).
 * Install/uninstall actually downloads the APK into app-private storage and validates it against
 * the extension manifest before marking [isInstalled].
 *
 * Keyed on (packageName, repoId) — two repos can ship the SAME extension (e.g. keiyoushi's newer
 * "The Blank" + a personal fork), and both versions must be listed like in Mihon/Tadami, with the
 * user picking which repo's build to install.
 */
@Entity(tableName = "extensions", primaryKeys = ["packageName", "repoId"])
data class ExtensionEntity(
    val packageName: String,
    val repoId: String,
    val name: String,
    val versionName: String,
    val versionCode: String,
    val libVersion: String,
    val contentWarning: String = "",
    val apkUrl: String,
    val iconUrl: String = "",
    val nsfw: Boolean = false,
    val isInstalled: Boolean = false,
    val installedVersionName: String? = null,
    val installedVersionCode: String? = null,
    val installError: String? = null,
    // JSON array of {id,name,lang,baseUrl} source descriptors from the repo index — used to
    // activate this extension's sources in the Sources tab when it is installed.
    val sourcesJson: String = ""
)

/**
 * A browsable source. Two kinds of rows:
 *  - the built-in source ("mangadex", repoId = "builtin") — implemented by MangaSource impls.
 *  - sources that ship inside an installed extension ([extensionPkg] != ""). Browsing works for
 *    sources backed by a real MangaSource implementation; others open the real site in a browser.
 */
@Entity(tableName = "extension_sources")
data class ExtensionSourceEntity(
    @PrimaryKey val id: String, // "mangadex" for built-in, otherwise "<extensionPkg>:<sourceId>"
    val extensionPkg: String = "", // owning extension package ("" = built-in)
    val repoId: String = "builtin",
    val name: String,
    val version: String,
    val lang: String = "en",
    val iconUrl: String = "",
    val isInstalled: Boolean = true,
    val isNsfw: Boolean = false,
    val baseUrl: String = "",
    val sourceType: String = "MANGA",
    val sourceName: String = "" // raw source id from the repo index ("" = built-in)
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int
)
