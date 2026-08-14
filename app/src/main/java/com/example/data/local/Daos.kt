package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    @Query("SELECT * FROM manga WHERE inLibrary = 1 ORDER BY lastReadTimestamp DESC, title ASC")
    fun getLibraryManga(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE inLibrary = 1 AND category = :category ORDER BY title ASC")
    fun getLibraryMangaByCategory(category: String): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE id = :id")
    fun getMangaByIdFlow(id: String): Flow<MangaEntity?>

    @Query("SELECT * FROM manga WHERE id = :id")
    suspend fun getMangaById(id: String): MangaEntity?

    @Query("SELECT * FROM manga WHERE lastReadTimestamp > 0 ORDER BY lastReadTimestamp DESC LIMIT 30")
    fun getReadingHistory(): Flow<List<MangaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManga(manga: MangaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMangaList(mangaList: List<MangaEntity>)

    @Update
    suspend fun updateManga(manga: MangaEntity)

    @Query("UPDATE manga SET inLibrary = :inLibrary, category = :category WHERE id = :id")
    suspend fun updateLibraryStatus(id: String, inLibrary: Boolean, category: String)

    @Query("UPDATE manga SET lastReadChapterId = :chapterId, lastReadChapterName = :chapterName, lastReadPage = :page, lastReadTimestamp = :timestamp WHERE id = :mangaId")
    suspend fun updateReadProgress(mangaId: String, chapterId: String, chapterName: String, page: Int, timestamp: Long)

    @Query("DELETE FROM manga WHERE id = :id")
    suspend fun deleteManga(id: String)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY chapterNumber DESC")
    fun getChaptersForManga(mangaId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY chapterNumber DESC")
    suspend fun getChaptersListForManga(mangaId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapterById(id: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE id = :id")
    fun getChapterByIdFlow(id: String): Flow<ChapterEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity)

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Query("UPDATE chapters SET read = :read, lastPageRead = :lastPageRead WHERE id = :chapterId")
    suspend fun updateChapterReadState(chapterId: String, read: Boolean, lastPageRead: Int)

    @Query("UPDATE chapters SET bookmarked = :bookmarked WHERE id = :chapterId")
    suspend fun toggleBookmark(chapterId: String, bookmarked: Boolean)

    @Query("UPDATE chapters SET read = 1 WHERE mangaId = :mangaId AND chapterNumber <= :chapterNumber")
    suspend fun markPreviousChaptersAsRead(mangaId: String, chapterNumber: Float)

    @Query("SELECT * FROM chapters WHERE read = 1 ORDER BY dateUpload DESC LIMIT 20")
    fun getRecentlyReadChapters(): Flow<List<ChapterEntity>>
}

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM extension_repos ORDER BY isOfficial DESC, name ASC")
    fun getAllRepos(): Flow<List<ExtensionRepoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepo(repo: ExtensionRepoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepos(repos: List<ExtensionRepoEntity>)

    @Query("DELETE FROM extension_repos WHERE id = :id")
    suspend fun deleteRepo(id: String)

    @Query("SELECT * FROM extension_sources ORDER BY isInstalled DESC, name ASC")
    fun getAllSources(): Flow<List<ExtensionSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<ExtensionSourceEntity>)

    @Query("UPDATE extension_sources SET isInstalled = :installed WHERE id = :id")
    suspend fun updateSourceInstalledStatus(id: String, installed: Boolean)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: String)
}
