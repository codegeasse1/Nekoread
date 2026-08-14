package com.example.data.repository

import com.example.data.extension.ExtensionEngine
import com.example.data.local.AppDatabase
import com.example.data.local.ChapterEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.ExtensionRepoEntity
import com.example.data.local.ExtensionSourceEntity
import com.example.data.local.MangaEntity
import com.example.data.source.MangaSource
import com.example.data.source.SourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class MangaRepository(private val db: AppDatabase) {

    val libraryManga: Flow<List<MangaEntity>> = db.mangaDao().getLibraryManga()
    val readingHistory: Flow<List<MangaEntity>> = db.mangaDao().getReadingHistory()
    val categories: Flow<List<CategoryEntity>> = db.categoryDao().getAllCategories()
    val extensionRepos: Flow<List<ExtensionRepoEntity>> = db.extensionDao().getAllRepos()
    val extensionSources: Flow<List<ExtensionSourceEntity>> = db.extensionDao().getAllSources()

    fun sourceForManga(mangaId: String): MangaSource = SourceRegistry.source(mangaId.substringBefore(":"))

    suspend fun initializeDefaultDataIfNeeded() = withContext(Dispatchers.IO) {
        val catList = db.categoryDao().getAllCategories().first()
        if (catList.isEmpty()) {
            db.categoryDao().insertCategories(ExtensionEngine.defaultCategories)
        }

        val repoList = db.extensionDao().getAllRepos().first()
        if (repoList.isEmpty()) {
            db.extensionDao().insertRepos(ExtensionEngine.defaultRepos)
        }

        val sourceList = db.extensionDao().getAllSources().first()
        if (sourceList.isEmpty()) {
            db.extensionDao().insertSources(ExtensionEngine.defaultSources)
        }
    }

    fun getMangaFlow(id: String): Flow<MangaEntity?> = db.mangaDao().getMangaByIdFlow(id)

    suspend fun getMangaById(id: String): MangaEntity? = db.mangaDao().getMangaById(id)

    fun getChaptersFlow(mangaId: String): Flow<List<ChapterEntity>> = db.chapterDao().getChaptersForManga(mangaId)

    /** Live catalog search against a real source. Results are upserted so the detail screen works offline. */
    suspend fun searchCatalog(sourceId: String, query: String, page: Int): List<MangaEntity> = withContext(Dispatchers.IO) {
        val src = SourceRegistry.source(sourceId)
        val results = if (query.isBlank()) src.latest(page) else src.search(query, page)
        if (results.isNotEmpty()) {
            // Never overwrite rows that are already tracked (library state, reading progress).
            val fresh = mutableListOf<MangaEntity>()
            for (m in results) {
                if (db.mangaDao().getMangaById(m.id) == null) fresh.add(m)
            }
            if (fresh.isNotEmpty()) {
                db.mangaDao().insertMangaList(fresh)
            }
        }
        results
    }

    /** Make sure a manga fetched from a catalog exists in the DB before opening its detail screen. */
    suspend fun ensureMangaInDb(fullMangaId: String) = withContext(Dispatchers.IO) {
        if (db.mangaDao().getMangaById(fullMangaId) != null) return@withContext
        val manga = SourceRegistry.source(fullMangaId.substringBefore(":")).getDetails(fullMangaId)
        db.mangaDao().insertManga(manga)
    }

    /** Fetch and persist the real chapter list for a manga. Returns true if new chapters were stored. */
    suspend fun loadChapters(fullMangaId: String, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!force && db.chapterDao().getChaptersListForManga(fullMangaId).isNotEmpty()) {
            return@withContext false
        }
        try {
            val chapters = SourceRegistry.source(fullMangaId.substringBefore(":")).getChapters(fullMangaId)
            if (chapters.isNotEmpty()) {
                db.chapterDao().insertChapters(chapters)
                return@withContext true
            }
        } catch (e: Exception) {
            // network failure — leave existing data (if any) and let the UI offer a retry
        }
        false
    }

    /** Live page-image URLs for a chapter (via the chapter's raw source id stored in [ChapterEntity.fetchUrl]). */
    suspend fun getChapterPageUrls(chapterId: String): List<String> = withContext(Dispatchers.IO) {
        val ch = db.chapterDao().getChapterById(chapterId) ?: return@withContext emptyList()
        SourceRegistry.source(ch.mangaId.substringBefore(":")).getPageUrls(ch.fetchUrl)
    }

    suspend fun toggleLibraryStatus(mangaId: String, category: String = "Reading") = withContext(Dispatchers.IO) {
        val manga = db.mangaDao().getMangaById(mangaId) ?: return@withContext
        val newInLibrary = !manga.inLibrary
        db.mangaDao().updateLibraryStatus(mangaId, newInLibrary, category)
    }

    suspend fun updateMangaCategory(mangaId: String, category: String) = withContext(Dispatchers.IO) {
        db.mangaDao().updateLibraryStatus(mangaId, true, category)
    }

    suspend fun toggleChapterRead(chapterId: String) = withContext(Dispatchers.IO) {
        val ch = db.chapterDao().getChapterById(chapterId) ?: return@withContext
        val newRead = !ch.read
        db.chapterDao().updateChapterReadState(chapterId, newRead, if (newRead) ch.totalPages else 1)
    }

    suspend fun toggleChapterBookmark(chapterId: String) = withContext(Dispatchers.IO) {
        val ch = db.chapterDao().getChapterById(chapterId) ?: return@withContext
        db.chapterDao().toggleBookmark(chapterId, !ch.bookmarked)
    }

    suspend fun markPreviousChaptersRead(mangaId: String, chapterNumber: Float) = withContext(Dispatchers.IO) {
        db.chapterDao().markPreviousChaptersAsRead(mangaId, chapterNumber)
    }

    suspend fun saveReadingProgress(mangaId: String, chapterId: String, chapterName: String, page: Int) = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        db.mangaDao().updateReadProgress(mangaId, chapterId, chapterName, page, timestamp)
        db.chapterDao().updateChapterReadState(chapterId, page >= 1, page)
    }

    suspend fun addExtensionRepo(repoUrl: String, repoName: String) = withContext(Dispatchers.IO) {
        val cleanUrl = repoUrl.trim()
        val cleanName = if (repoName.isNotBlank()) repoName else "Custom Extension Repo (${cleanUrl.takeLast(20)})"
        val id = "repo_" + cleanUrl.hashCode()
        val repo = ExtensionRepoEntity(
            id = id,
            name = cleanName,
            url = cleanUrl,
            extensionCount = 0,
            isOfficial = false
        )
        db.extensionDao().insertRepo(repo)
    }

    suspend fun deleteExtensionRepo(id: String) = withContext(Dispatchers.IO) {
        db.extensionDao().deleteRepo(id)
    }

    suspend fun addCategory(name: String) = withContext(Dispatchers.IO) {
        val id = "cat_" + System.currentTimeMillis()
        val category = CategoryEntity(id = id, name = name, sortOrder = 10)
        db.categoryDao().insertCategory(category)
    }

    suspend fun deleteCategory(id: String) = withContext(Dispatchers.IO) {
        db.categoryDao().deleteCategory(id)
    }
}
