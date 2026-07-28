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

@Entity(tableName = "extension_repos")
data class ExtensionRepoEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val extensionCount: Int = 0,
    val isOfficial: Boolean = false,
    val addedDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "extension_sources")
data class ExtensionSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val lang: String = "en",
    val iconUrl: String = "",
    val repoId: String = "official",
    val isInstalled: Boolean = true,
    val isNsfw: Boolean = false,
    val baseUrl: String = "",
    val sourceType: String = "MANHWA"
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int
)
