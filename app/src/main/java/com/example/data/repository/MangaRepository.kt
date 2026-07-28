package com.example.data.repository

import com.example.data.extension.ExtensionEngine
import com.example.data.local.AppDatabase
import com.example.data.local.CategoryEntity
import com.example.data.local.ChapterEntity
import com.example.data.local.ExtensionRepoEntity
import com.example.data.local.ExtensionSourceEntity
import com.example.data.local.MangaEntity
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

        // Preload sample catalog into database so search and library operate seamlessly
        val existingManga = db.mangaDao().getLibraryManga().first()
        if (existingManga.isEmpty()) {
            for (manga in ExtensionEngine.sampleCatalog) {
                db.mangaDao().insertManga(manga)
                val chapters = ExtensionEngine.getSampleChaptersForManga(manga.id)
                db.chapterDao().insertChapters(chapters)
            }
        }
    }

    fun getMangaFlow(id: String): Flow<MangaEntity?> = db.mangaDao().getMangaByIdFlow(id)

    suspend fun getMangaById(id: String): MangaEntity? = db.mangaDao().getMangaById(id)

    fun getChaptersFlow(mangaId: String): Flow<List<ChapterEntity>> = db.chapterDao().getChaptersForManga(mangaId)

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
            extensionCount = 24,
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
