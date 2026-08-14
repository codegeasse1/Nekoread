package com.example.data.repository

import android.app.Application
import android.content.pm.PackageManager
import com.example.data.extension.ExtensionDexLoader
import com.example.data.extension.ExtensionEngine
import com.example.data.extension.ExtensionNetwork
import com.example.data.extension.ExtensionNetworkException
import com.example.data.extension.ParsedExtension
import com.example.data.local.AppDatabase
import com.example.data.local.ChapterEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.ExtensionEntity
import com.example.data.local.ExtensionRepoEntity
import com.example.data.local.ExtensionSourceEntity
import com.example.data.local.MangaEntity
import com.example.data.source.MangaSource
import com.example.data.source.SourceRegistry
import com.example.data.source.TachiyomiHttpSourceAdapter
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class MangaRepository(private val db: AppDatabase, private val app: Application) {

    val libraryManga: Flow<List<MangaEntity>> = db.mangaDao().getLibraryManga()
    val readingHistory: Flow<List<MangaEntity>> = db.mangaDao().getReadingHistory()
    val categories: Flow<List<CategoryEntity>> = db.categoryDao().getAllCategories()
    val extensionRepos: Flow<List<ExtensionRepoEntity>> = db.extensionDao().getAllRepos()
    val extensionSources: Flow<List<ExtensionSourceEntity>> = db.extensionDao().getAllSources()
    val extensions: Flow<List<ExtensionEntity>> = db.extensionDao().getAllExtensions()

    private fun extensionsDir(): File = File(app.filesDir, "extensions")

    private fun apkFile(packageName: String): File = File(extensionsDir(), "$packageName.apk")

    fun sourceForManga(mangaId: String): MangaSource = SourceRegistry.source(mangaId.substringBefore(":"))

    suspend fun initializeDefaultDataIfNeeded() = withContext(Dispatchers.IO) {
        val catList = db.categoryDao().getAllCategories().first()
        if (catList.isEmpty()) {
            db.categoryDao().insertCategories(ExtensionEngine.defaultCategories)
        }

        // Sources come from installed extensions only (like Tadami) — nothing is seeded here.

        // Seed the well-known repos only on first launch. Counts are fetched live afterwards.
        val repoList = db.extensionDao().getAllRepos().first()
        if (repoList.isEmpty()) {
            db.extensionDao().insertRepos(ExtensionEngine.defaultRepos)
        }
    }

    /** Refresh every repo that has never been fetched yet (called in the background on launch). */
    suspend fun refreshStaleRepos() = withContext(Dispatchers.IO) {
        val repos = db.extensionDao().getAllRepos().first()
        for (repo in repos) {
            if (repo.extensionCount == 0) {
                try {
                    refreshRepoInternal(repo)
                } catch (e: Exception) {
                    // offline / unreachable — the Repos tab shows the failure via its refresh button
                }
            }
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

    // ------------------------------------------------------------------------------------------
    // Extension repos (real data, fetched from each repo's index.json)
    // ------------------------------------------------------------------------------------------

    private fun repoIdFor(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .take(8)
            .joinToString("") { String.format("%02x", it) }
        return "repo_$digest"
    }

    private fun ParsedExtension.toEntity(repoId: String): ExtensionEntity = ExtensionEntity(
        packageName = packageName,
        repoId = repoId,
        name = name,
        versionName = versionName,
        versionCode = versionCode,
        libVersion = libVersion,
        contentWarning = contentWarning,
        apkUrl = apkUrl,
        iconUrl = iconUrl,
        nsfw = nsfw,
        isInstalled = false,
        installedVersionName = null,
        installError = null,
        sourcesJson = ExtensionNetwork.sourcesToJson(sources)
    )

    private suspend fun refreshRepoInternal(repo: ExtensionRepoEntity) {
        val parsed = ExtensionNetwork.fetchRepoIndex(repo.url)
        val installedPkgs = db.extensionDao().getExtensionsByRepo(repo.id)
            .filter { it.isInstalled }
            .map { it.packageName }
            .toSet()

        db.extensionDao().deleteExtensionsByRepo(repo.id)
        db.extensionDao().insertExtensions(
            parsed.extensions.map { ext ->
                ext.toEntity(repo.id).copy(isInstalled = ext.packageName in installedPkgs)
            }
        )
        db.extensionDao().updateRepoInfo(repo.id, repo.name, parsed.extensions.size, System.currentTimeMillis())
    }

    /**
     * Add (or update) an extension repo by really fetching its index. The URL may point directly
     * at an index file (index.json / repo.json / index.min.json / ...) or at a repo base URL, in
     * which case the common index file names are tried in order. Returns an error message on
     * failure, or null on success.
     */
    suspend fun addExtensionRepo(repoUrl: String, repoName: String): String? = withContext(Dispatchers.IO) {
        val cleanUrl = repoUrl.trim().removeSuffix("/")
        if (cleanUrl.isBlank()) return@withContext "URL is empty"
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            return@withContext "URL must start with http:// or https://"
        }

        val candidates = if (ExtensionNetwork.isIndexUrl(cleanUrl)) {
            listOf(cleanUrl)
        } else {
            ExtensionNetwork.INDEX_FILE_NAMES.map { "$cleanUrl/$it" }
        }

        var lastError: String? = null
        for (indexUrl in candidates) {
            try {
                val parsed = ExtensionNetwork.fetchRepoIndex(indexUrl)
                val id = repoIdFor(indexUrl)
                val existing = db.extensionDao().getRepoById(id)
                val repo = ExtensionRepoEntity(
                    id = id,
                    name = repoName.ifBlank { parsed.name },
                    url = indexUrl,
                    extensionCount = parsed.extensions.size,
                    lastUpdated = System.currentTimeMillis(),
                    addedDate = existing?.addedDate ?: System.currentTimeMillis()
                )
                db.extensionDao().insertRepo(repo)
                val installedPkgs = db.extensionDao().getExtensionsByRepo(id)
                    .filter { it.isInstalled }
                    .map { it.packageName }
                    .toSet()
                db.extensionDao().deleteExtensionsByRepo(id)
                db.extensionDao().insertExtensions(
                    parsed.extensions.map { ext ->
                        ext.toEntity(id).copy(isInstalled = ext.packageName in installedPkgs)
                    }
                )
                return@withContext null
            } catch (e: ExtensionNetworkException) {
                lastError = e.message
            } catch (e: Exception) {
                lastError = "Failed to add repo: ${e.message ?: "unknown error"}"
            }
        }
        lastError ?: "Failed to add repo"
    }

    /** Re-fetch a repo's index.json and update its catalog. Returns an error message or null. */
    suspend fun refreshExtensionRepo(id: String): String? = withContext(Dispatchers.IO) {
        val repo = db.extensionDao().getRepoById(id) ?: return@withContext "Repo not found"
        try {
            refreshRepoInternal(repo)
            null
        } catch (e: ExtensionNetworkException) {
            e.message ?: "Failed to refresh repo"
        } catch (e: Exception) {
            "Failed to refresh repo: ${e.message ?: "unknown error"}"
        }
    }

    /** Remove a repo, its catalog, its sources and any installed extension APKs it provided. */
    suspend fun deleteExtensionRepo(id: String) = withContext(Dispatchers.IO) {
        val exts = db.extensionDao().getExtensionsByRepo(id)
        for (ext in exts) {
            apkFile(ext.packageName).delete()
        }
        db.extensionDao().deleteExtensionsByRepo(id)
        db.extensionDao().deleteSourcesByRepo(id)
        db.extensionDao().deleteRepo(id)
    }

    // ------------------------------------------------------------------------------------------
    // Extension install / uninstall (real APK download + dex load, like Tadami/Mihon)
    // ------------------------------------------------------------------------------------------

    /**
     * Download the extension's APK into app-private storage and load its dex against the in-app
     * Tachiyomi source-api runtime. This is exactly what Tadami/Mihon do: a valid extension APK is
     * one whose classes can actually be instantiated and talk to the sources it declares. The
     * downloaded file is deleted and an error is reported if loading fails for any reason.
     * Returns an error message on failure, or null on success.
     */
    suspend fun installExtension(packageName: String): String? = withContext(Dispatchers.IO) {
        val ext = db.extensionDao().getExtension(packageName) ?: return@withContext "Extension not found"
        if (ext.apkUrl.isBlank()) return@withContext "This extension has no APK URL"

        val dest = apkFile(packageName)
        try {
            ExtensionNetwork.downloadApk(ext.apkUrl, dest)
        } catch (e: ExtensionNetworkException) {
            db.extensionDao().updateExtensionInstallState(packageName, false, null, e.message)
            return@withContext e.message
        } catch (e: Exception) {
            db.extensionDao().updateExtensionInstallState(packageName, false, null, "Download failed")
            return@withContext "Download failed: ${e.message ?: "unknown error"}"
        }

        // Sanity check against the real manifest when it's parseable. A package-name mismatch is
        // fatal; an unparseable manifest is not — the definitive test below is whether the dex
        // actually loads against the in-app runtime.
        val pm = app.packageManager
        val info = try {
            pm.getPackageArchiveInfo(dest.absolutePath, PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            null
        }
        if (info != null && info.packageName != packageName) {
            dest.delete()
            db.extensionDao().updateExtensionInstallState(packageName, false, null, "Package mismatch")
            return@withContext "APK package (${info.packageName}) does not match index package ($packageName)"
        }

        try {
            val sources = ExtensionDexLoader.loadApk(dest, dexCacheDir(), packageName)
            val registered = registerExtensionSources(ext, sources)
            if (!registered) {
                db.extensionDao().updateExtensionInstallState(packageName, false, null, "No sources in extension")
                return@withContext "Extension APK contained no browsable sources"
            }
        } catch (e: Exception) {
            // Keep the downloaded APK so a reinstall doesn't need to re-download it.
            val msg = e.message ?: "unknown error"
            db.extensionDao().updateExtensionInstallState(packageName, false, null, msg)
            return@withContext "Couldn't load extension: $msg"
        }

        db.extensionDao().updateExtensionInstallState(packageName, true, ext.versionName, null)
        null
    }

    /**
     * Load every installed extension's dex back into memory on app start (their sources are
     * stateless HTTP clients, so re-instantiating is all that's needed). Called once at startup.
     * Source rows are rebuilt from what actually loads — anything stale (from an old install or a
     * failed load) is dropped, so no fake source can ever appear in the Sources tab.
     */
    suspend fun loadInstalledExtensions() = withContext(Dispatchers.IO) {
        db.extensionDao().clearExtensionSources()
        val installed = db.extensionDao().getInstalledExtensionsOnce()
        for (ext in installed) {
            val dest = apkFile(ext.packageName)
            if (!dest.exists()) {
                db.extensionDao().updateExtensionInstallState(ext.packageName, false, null, "APK file missing")
                continue
            }
            try {
                val sources = ExtensionDexLoader.loadApk(dest, dexCacheDir(), ext.packageName)
                val registered = registerExtensionSources(ext, sources)
                if (!registered) {
                    db.extensionDao().updateExtensionInstallState(ext.packageName, false, null, "No browsable sources in extension")
                }
            } catch (e: Exception) {
                db.extensionDao().updateExtensionInstallState(ext.packageName, false, null, e.message)
            }
        }
    }

    private fun dexCacheDir(): File = File(app.cacheDir, "ext_dex")

    /** Persist source rows for a freshly-loaded extension and register its adapters in the registry. */
    private suspend fun registerExtensionSources(ext: ExtensionEntity, sources: List<Source>): Boolean {
        val rows = mutableListOf<ExtensionSourceEntity>()
        for (s in sources) {
            if (s !is HttpSource) continue // non-HTTP sources aren't browsable in-app yet
            val adapter = TachiyomiHttpSourceAdapter(s, ext.packageName)
            ExtensionDexLoader.register(adapter)
            rows.add(
                ExtensionSourceEntity(
                    id = adapter.id,
                    extensionPkg = ext.packageName,
                    repoId = ext.repoId,
                    name = adapter.name,
                    version = ext.versionName,
                    lang = adapter.lang,
                    iconUrl = ext.iconUrl,
                    isInstalled = true,
                    isNsfw = ext.nsfw,
                    baseUrl = adapter.baseUrl,
                    sourceType = "MANGA",
                    sourceName = adapter.id
                )
            )
        }
        if (rows.isNotEmpty()) {
            db.extensionDao().insertSources(rows)
        }
        return rows.isNotEmpty()
    }

    /** Remove an installed extension: delete its APK and deactivate its sources. */
    suspend fun uninstallExtension(packageName: String): String? = withContext(Dispatchers.IO) {
        if (db.extensionDao().getExtension(packageName) == null) {
            return@withContext "Extension not found"
        }
        apkFile(packageName).delete()
        ExtensionDexLoader.unregisterExtension(packageName)
        db.extensionDao().deleteSourcesByExtension(packageName)
        db.extensionDao().updateExtensionInstallState(packageName, false, null, null)
        null
    }

    // ------------------------------------------------------------------------------------------
    // Categories
    // ------------------------------------------------------------------------------------------

    suspend fun addCategory(name: String) = withContext(Dispatchers.IO) {
        val id = "cat_" + System.currentTimeMillis()
        val category = CategoryEntity(id = id, name = name, sortOrder = 10)
        db.categoryDao().insertCategory(category)
    }

    suspend fun deleteCategory(id: String) = withContext(Dispatchers.IO) {
        db.categoryDao().deleteCategory(id)
    }
}
