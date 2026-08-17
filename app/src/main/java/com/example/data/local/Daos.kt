package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    @Query("SELECT * FROM manga WHERE inLibrary = 1 OR lastReadTimestamp > 0 ORDER BY lastReadTimestamp DESC, title ASC")
    fun getLibraryManga(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga ORDER BY title ASC")
    suspend fun getAllManga(): List<MangaEntity>

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

    @Query("UPDATE manga SET lastReadTimestamp = 0, lastReadChapterId = NULL, lastReadChapterName = NULL, lastReadPage = 1")
    suspend fun clearReadingHistory()

    @Query("UPDATE manga SET lastReadTimestamp = 0, lastReadChapterId = NULL, lastReadChapterName = NULL, lastReadPage = 1 WHERE id = :id")
    suspend fun clearReadingHistoryFor(id: String)

    @Query("UPDATE manga SET inLibrary = 0, category = 'Reading' WHERE inLibrary = 1")
    suspend fun clearLibrary()

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

    @Query("SELECT * FROM chapters ORDER BY dateUpload DESC")
    suspend fun getAllChapters(): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE read = 1 ORDER BY dateUpload DESC LIMIT 20")
    fun getRecentlyReadChapters(): Flow<List<ChapterEntity>>
}

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM extension_repos ORDER BY name ASC")
    fun getAllRepos(): Flow<List<ExtensionRepoEntity>>

    @Query("SELECT * FROM extension_repos ORDER BY name ASC")
    suspend fun getAllReposOnce(): List<ExtensionRepoEntity>

    @Query("SELECT * FROM extension_repos WHERE id = :id")
    suspend fun getRepoById(id: String): ExtensionRepoEntity?

    @Query("SELECT * FROM extension_repos WHERE url = :url")
    suspend fun getRepoByUrl(url: String): ExtensionRepoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepo(repo: ExtensionRepoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepos(repos: List<ExtensionRepoEntity>)

    @Query("UPDATE extension_repos SET name = :name, extensionCount = :count, lastUpdated = :lastUpdated WHERE id = :id")
    suspend fun updateRepoInfo(id: String, name: String, count: Int, lastUpdated: Long)

    @Query("DELETE FROM extension_repos WHERE id = :id")
    suspend fun deleteRepo(id: String)

    @Query("SELECT * FROM extensions ORDER BY name ASC")
    fun getAllExtensions(): Flow<List<ExtensionEntity>>

    @Query("SELECT * FROM extensions ORDER BY name ASC")
    suspend fun getAllExtensionsOnce(): List<ExtensionEntity>

    @Query("SELECT * FROM extensions WHERE repoId = :repoId ORDER BY name ASC")
    suspend fun getExtensionsByRepo(repoId: String): List<ExtensionEntity>

    @Query("SELECT * FROM extensions WHERE packageName = :packageName AND repoId = :repoId")
    suspend fun getExtension(packageName: String, repoId: String): ExtensionEntity?

    @Query("SELECT * FROM extensions WHERE isInstalled = 1 ORDER BY name ASC")
    fun getInstalledExtensions(): Flow<List<ExtensionEntity>>

    @Query("SELECT * FROM extensions WHERE isInstalled = 1 ORDER BY name ASC")
    suspend fun getInstalledExtensionsOnce(): List<ExtensionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtensions(extensions: List<ExtensionEntity>)

    @Query("DELETE FROM extensions WHERE repoId = :repoId")
    suspend fun deleteExtensionsByRepo(repoId: String)

    @Query("DELETE FROM extensions WHERE packageName = :packageName AND repoId = :repoId")
    suspend fun deleteExtension(packageName: String, repoId: String)

    @Query("UPDATE extensions SET isInstalled = :installed, installedVersionName = :installedVersion, installedVersionCode = :installedVersionCode, installError = :error WHERE packageName = :packageName AND repoId = :repoId")
    suspend fun updateExtensionInstallState(packageName: String, repoId: String, installed: Boolean, installedVersion: String?, installedVersionCode: String?, error: String?)

    /** Only one APK per package can be installed — installing from another repo unmarks the others. */
    @Query("UPDATE extensions SET isInstalled = 0 WHERE packageName = :packageName")
    suspend fun clearInstalledState(packageName: String)

    @Query("SELECT * FROM extension_sources ORDER BY name ASC")
    fun getAllSources(): Flow<List<ExtensionSourceEntity>>

    @Query("SELECT * FROM extension_sources ORDER BY name ASC")
    suspend fun getAllSourcesOnce(): List<ExtensionSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<ExtensionSourceEntity>)

    @Query("DELETE FROM extension_sources WHERE extensionPkg = :extensionPkg")
    suspend fun deleteSourcesByExtension(extensionPkg: String)

    @Query("DELETE FROM extension_sources")
    suspend fun clearExtensionSources()

    @Query("DELETE FROM extension_sources WHERE repoId = :repoId")
    suspend fun deleteSourcesByRepo(repoId: String)

    @Query("UPDATE extension_sources SET isInstalled = :installed WHERE extensionPkg = :extensionPkg")
    suspend fun updateSourcesInstalled(extensionPkg: String, installed: Boolean)
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
