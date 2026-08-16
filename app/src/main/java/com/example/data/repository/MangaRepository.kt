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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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

    /** Live catalog search against a real source. Results are upserted so the detail screen works offline.
     *  A query prefixed with "tag:" (e.g. "tag:Action") searches by tag/genre instead of title. */
    suspend fun searchCatalog(sourceId: String, query: String, page: Int): List<MangaEntity> = withContext(Dispatchers.IO) {
        val src = SourceRegistry.source(sourceId)
        val results = if (query.startsWith("tag:")) {
            src.searchByTag(query.removePrefix("tag:").trim(), page)
        } else if (query.isBlank()) {
            src.latest(page)
        } else {
            src.search(query, page)
        }
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

    /** Make sure a manga fetched from a catalog exists in the DB before opening its detail screen,
     *  and always refresh its details from the source so the description/genres/status stay current
     *  (catalog search rows can arrive with an empty description). Library state and reading
     *  progress are preserved. A failed refresh never blocks: cached data (if any) is kept. */
    suspend fun ensureMangaInDb(fullMangaId: String) = withContext(Dispatchers.IO) {
        val existing = db.mangaDao().getMangaById(fullMangaId)
        val fresh = try {
            SourceRegistry.source(fullMangaId.substringBefore(":")).getDetails(fullMangaId)
        } catch (e: Throwable) {
            if (existing == null) throw e
            null
        }
        if (existing == null) {
            fresh?.let { db.mangaDao().insertManga(it) }
        } else if (fresh != null) {
            db.mangaDao().updateManga(
                fresh.copy(
                    inLibrary = existing.inLibrary,
                    category = existing.category,
                    lastReadChapterId = existing.lastReadChapterId,
                    lastReadChapterName = existing.lastReadChapterName,
                    lastReadPage = existing.lastReadPage,
                    lastReadTimestamp = existing.lastReadTimestamp,
                    unreadCount = existing.unreadCount,
                    bookmarkCount = existing.bookmarkCount,
                    rating = existing.rating
                )
            )
        }
    }

    /**
     * Global search: run [query] against every currently-loaded extension source in parallel and
     * merge the results. A failing source is skipped (its results are simply absent) so one broken
     * extension never blocks the whole search. Results are capped per source to keep the list sane.
     */
    suspend fun searchAllInstalledSources(query: String, perSource: Int = 20): List<MangaEntity> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val adapters = ExtensionDexLoader.loaded
            coroutineScope {
                adapters.map { adapter ->
                    async(Dispatchers.IO) {
                        try {
                            adapter.search(query.trim(), 1).take(perSource)
                        } catch (e: Throwable) {
                            emptyList()
                        }
                    }
                }.awaitAll().flatten().distinctBy { it.id }
            }
        }

    /**
     * Fetch and persist the real chapter list for a manga. Returns true if new chapters were
     * stored. Always refreshes from the source so cached entries pick up renumbered/updated
     * chapters (chapter numbers for -1 sources were once inverted), while read / bookmark /
     * progress state is carried over from the existing rows.
     */
    suspend fun loadChapters(fullMangaId: String, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val existing = db.chapterDao().getChaptersListForManga(fullMangaId)
        if (!force && existing.isNotEmpty()) {
            return@withContext false
        }
        try {
            val chapters = SourceRegistry.source(fullMangaId.substringBefore(":")).getChapters(fullMangaId)
            if (chapters.isNotEmpty()) {
                val existingById = existing.associateBy { it.id }
                db.chapterDao().insertChapters(
                    chapters.map { ch ->
                        val old = existingById[ch.id]
                        if (old != null) {
                            ch.copy(
                                read = old.read,
                                bookmarked = old.bookmarked,
                                lastPageRead = old.lastPageRead,
                                totalPages = old.totalPages
                            )
                        } else ch
                    }
                )
                return@withContext true
            }
        } catch (e: Throwable) {
            // network failure or extension incompatibility — leave existing data (if any) and let the UI offer a retry
        }
        false
    }

    /** Live page-image URLs for a chapter (via the chapter's raw source id stored in [ChapterEntity.fetchUrl]). */
    suspend fun getChapterPageUrls(chapterId: String): List<String> = withContext(Dispatchers.IO) {
        val ch = db.chapterDao().getChapterById(chapterId) ?: return@withContext emptyList()
        SourceRegistry.source(ch.mangaId.substringBefore(":")).getPageUrls(ch.fetchUrl)
    }

    /** Live page-image Coil models for a chapter. Extension sources return source-aware models so
     *  pages load through the extension's own client + headers (see MangaSource.getPageImageModels). */
    suspend fun getChapterPageImageModels(chapterId: String): List<Any> = withContext(Dispatchers.IO) {
        val ch = db.chapterDao().getChapterById(chapterId) ?: return@withContext emptyList()
        SourceRegistry.source(ch.mangaId.substringBefore(":")).getPageImageModels(ch.fetchUrl)
    }

    /** Live page descriptors (request URL + image URL) for a chapter — the Tadami-style reader
     *  input. Each page is downloaded individually via [getPageImageFile] and rendered with a
     *  tiled view, so no giant full-page bitmap is ever decoded. */
    suspend fun getChapterPageDescriptors(chapterId: String): List<MangaSource.PageDescriptor> =
        withContext(Dispatchers.IO) {
            val ch = db.chapterDao().getChapterById(chapterId) ?: return@withContext emptyList()
            SourceRegistry.source(ch.mangaId.substringBefore(":")).getPageDescriptors(ch.fetchUrl)
        }

    /**
     * Download one reader page's image bytes into a stable disk cache (Tadami HttpPageLoader
     * model): returns the cached file on subsequent calls. Pages are fetched through the source's
     * own client so hotlink protection (Referer/Origin) is honoured, exactly like extensions do.
     */
    suspend fun getPageImageFile(chapterId: String, pageUrl: String, imageUrl: String): File =
        withContext(Dispatchers.IO) {
            val ch = db.chapterDao().getChapterById(chapterId) ?: throw IllegalStateException("Chapter not found")
            val source = SourceRegistry.source(ch.mangaId.substringBefore(":"))
            val dir = File(app.cacheDir, "reader_pages")
            dir.mkdirs()
            val key = imageUrl.hashCode().toUInt().toString(16)
            val target = File(dir, "$key.img")
            if (target.exists() && target.length() > 0) return@withContext target

            val tmp = File(dir, "$key.img.tmp")
            tmp.delete()
            try {
                source.downloadPageImage(MangaSource.PageDescriptor(pageUrl, imageUrl), tmp)
                if (!tmp.exists() || tmp.length() == 0L) throw java.io.IOException("Empty download for $imageUrl")
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                target
            } catch (e: Throwable) {
                tmp.delete()
                throw e
            }
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

    /**
     * Normalize a repo URL into a canonical identity so the same repo added in different URL forms
     * (github.com vs raw.githubusercontent.com, with or without an index file name, .pb vs .json,
     * trailing slashes) is recognized as one repo. GitHub-hosted repos collapse to their user/repo
     * base; any other host keeps scheme://host/path with known index suffixes stripped.
     */
    fun canonicalRepoUrl(url: String): String {
        val trimmed = url.trim()
        val uri = try {
            java.net.URI(trimmed)
        } catch (e: Exception) {
            return trimmed.trimEnd('/')
        }
        val scheme = uri.scheme ?: return trimmed.trimEnd('/')
        val host = (uri.host ?: return trimmed.trimEnd('/')).lowercase()
        var path = uri.path ?: ""
        for (name in ExtensionNetwork.ALL_INDEX_FILE_NAMES) {
            if (path.endsWith("/$name", ignoreCase = true)) {
                path = path.dropLast(name.length + 1)
                break
            }
        }
        path = path.trimEnd('/')
        val canonicalHost = if (host == "github.com") "raw.githubusercontent.com" else host
        if (canonicalHost == "raw.githubusercontent.com") {
            val seg = path.split("/").filter { it.isNotBlank() }
            path = when {
                seg.size >= 2 -> "/" + seg.take(2).joinToString("/")
                seg.size == 1 -> "/" + seg[0]
                else -> ""
            }
        }
        return "$scheme://$canonicalHost$path"
    }

    /**
     * Merge duplicate repos (the same canonical repo added via different URL forms) into a single
     * row. The earliest-added row wins; installed-extension markers are carried over to it and the
     * extras' repo/extension/source rows are deleted. Runs on startup so duplicates created before
     * URL canonicalization get cleaned up once.
     */
    suspend fun dedupeRepos() = withContext(Dispatchers.IO) {
        val repos = db.extensionDao().getAllReposOnce()
        val byCanonical = repos.groupBy { canonicalRepoUrl(it.url) }
        for ((_, group) in byCanonical) {
            if (group.size <= 1) continue
            val keep = group.minByOrNull { it.addedDate } ?: continue
            for (dup in group) {
                if (dup.id == keep.id) continue
                // Keep any installed-extension markers on the surviving row.
                for (ext in db.extensionDao().getExtensionsByRepo(dup.id).filter { it.isInstalled }) {
                    db.extensionDao().clearInstalledState(ext.packageName)
                    db.extensionDao().updateExtensionInstallState(ext.packageName, keep.id, true, ext.installedVersionName, null)
                }
                db.extensionDao().deleteExtensionsByRepo(dup.id)
                db.extensionDao().deleteSourcesByRepo(dup.id)
                db.extensionDao().deleteRepo(dup.id)
            }
        }
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

        // The same repo added twice (in different URL forms — github.com vs raw.githubusercontent.com,
        // index.json vs index.min.json, .pb vs .json) must not create a duplicate row.
        val canonical = canonicalRepoUrl(cleanUrl)
        if (db.extensionDao().getAllReposOnce().any { canonicalRepoUrl(it.url) == canonical }) {
            return@withContext "Repository already added"
        }

        // A direct .pb index URL is transparently swapped for its .json sibling; a base URL gets
        // the common index file names appended in order.
        val candidates = ExtensionNetwork.indexCandidatesFor(cleanUrl)

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
    suspend fun installExtension(packageName: String, repoId: String): String? = withContext(Dispatchers.IO) {
        val ext = db.extensionDao().getExtension(packageName, repoId) ?: return@withContext "Extension not found"
        if (ext.apkUrl.isBlank()) return@withContext "This extension has no APK URL"

        val dest = apkFile(packageName)
        // A previously-installed APK is stored read-only (so Android 14+ will load it); make it
        // writable again so the download can overwrite it on reinstall.
        if (dest.exists()) dest.setWritable(true)
        try {
            ExtensionNetwork.downloadApk(ext.apkUrl, dest)
        } catch (e: ExtensionNetworkException) {
            db.extensionDao().updateExtensionInstallState(packageName, repoId, false, null, e.message)
            return@withContext e.message
        } catch (e: Exception) {
            db.extensionDao().updateExtensionInstallState(packageName, repoId, false, null, "Download failed")
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
            db.extensionDao().updateExtensionInstallState(packageName, repoId, false, null, "Package mismatch")
            return@withContext "APK package (${info.packageName}) does not match index package ($packageName)"
        }

        // Android 14+ refuses to load a dex/APK file that is writable
        // ("Writable dex file '...' is not allowed."). The standard fix is to mark the file
        // read-only before handing it to the class loader.
        dest.setReadOnly()

        try {
            val sources = ExtensionDexLoader.loadApk(dest, dexCacheDir(), packageName, app)
            val registered = registerExtensionSources(ext, sources)
            if (!registered) {
                db.extensionDao().updateExtensionInstallState(packageName, repoId, false, null, "No sources in extension")
                return@withContext "Extension APK contained no browsable sources"
            }
        } catch (e: Throwable) {
            // Keep the downloaded APK so a reinstall doesn't need to re-download it.
            val msg = e.message ?: "unknown error"
            db.extensionDao().updateExtensionInstallState(packageName, repoId, false, null, msg)
            return@withContext "Couldn't load extension: $msg"
        }

        // Only this repo's build is the installed one; other repos' rows for the same package
        // are just "available in this other repo".
        db.extensionDao().clearInstalledState(packageName)
        db.extensionDao().updateExtensionInstallState(packageName, repoId, true, ext.versionName, null)
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
                db.extensionDao().updateExtensionInstallState(ext.packageName, ext.repoId, false, null, "APK file missing")
                continue
            }
            try {
                if (dest.exists()) dest.setReadOnly() // Android 14+ requires read-only dex files
                val sources = ExtensionDexLoader.loadApk(dest, dexCacheDir(), ext.packageName, app)
                val registered = registerExtensionSources(ext, sources)
                if (!registered) {
                    db.extensionDao().updateExtensionInstallState(ext.packageName, ext.repoId, false, null, "No browsable sources in extension")
                }
            } catch (e: Throwable) {
                db.extensionDao().updateExtensionInstallState(ext.packageName, ext.repoId, false, null, e.message)
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
    suspend fun uninstallExtension(packageName: String, repoId: String): String? = withContext(Dispatchers.IO) {
        if (db.extensionDao().getExtension(packageName, repoId) == null) {
            return@withContext "Extension not found"
        }
        apkFile(packageName).delete()
        ExtensionDexLoader.unregisterExtension(packageName)
        db.extensionDao().deleteSourcesByExtension(packageName)
        db.extensionDao().clearInstalledState(packageName)
        db.extensionDao().updateExtensionInstallState(packageName, repoId, false, null, null)
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

    // ------------------------------------------------------------------------------------------
    // Backup & restore (real JSON export/import of all user data — like Tadami's Data section)
    // ------------------------------------------------------------------------------------------

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "Nekoread")
        root.put("version", 1)

        root.put(
            "categories",
            JSONArray().also { arr ->
                for (c in db.categoryDao().getAllCategories().first()) {
                    arr.put(
                        JSONObject()
                            .put("id", c.id)
                            .put("name", c.name)
                            .put("sortOrder", c.sortOrder)
                    )
                }
            }
        )

        root.put(
            "repos",
            JSONArray().also { arr ->
                for (r in db.extensionDao().getAllReposOnce()) {
                    arr.put(
                        JSONObject()
                            .put("id", r.id)
                            .put("name", r.name)
                            .put("url", r.url)
                            .put("extensionCount", r.extensionCount)
                            .put("lastUpdated", r.lastUpdated)
                            .put("addedDate", r.addedDate)
                    )
                }
            }
        )

        root.put(
            "extensions",
            JSONArray().also { arr ->
                for (e in db.extensionDao().getAllExtensionsOnce()) {
                    arr.put(
                        JSONObject()
                            .put("packageName", e.packageName)
                            .put("repoId", e.repoId)
                            .put("name", e.name)
                            .put("versionName", e.versionName)
                            .put("versionCode", e.versionCode)
                            .put("libVersion", e.libVersion)
                            .put("contentWarning", e.contentWarning)
                            .put("apkUrl", e.apkUrl)
                            .put("iconUrl", e.iconUrl)
                            .put("nsfw", e.nsfw)
                            .put("isInstalled", e.isInstalled)
                            .put("installedVersionName", e.installedVersionName)
                            .put("installError", e.installError)
                            .put("sourcesJson", e.sourcesJson)
                    )
                }
            }
        )

        root.put(
            "sources",
            JSONArray().also { arr ->
                for (s in db.extensionDao().getAllSourcesOnce()) {
                    arr.put(
                        JSONObject()
                            .put("id", s.id)
                            .put("extensionPkg", s.extensionPkg)
                            .put("repoId", s.repoId)
                            .put("name", s.name)
                            .put("version", s.version)
                            .put("lang", s.lang)
                            .put("iconUrl", s.iconUrl)
                            .put("isInstalled", s.isInstalled)
                            .put("isNsfw", s.isNsfw)
                            .put("baseUrl", s.baseUrl)
                            .put("sourceType", s.sourceType)
                            .put("sourceName", s.sourceName)
                    )
                }
            }
        )

        root.put(
            "manga",
            JSONArray().also { arr ->
                for (m in db.mangaDao().getAllManga()) {
                    arr.put(
                        JSONObject()
                            .put("id", m.id)
                            .put("title", m.title)
                            .put("coverUrl", m.coverUrl)
                            .put("author", m.author)
                            .put("artist", m.artist)
                            .put("description", m.description)
                            .put("sourceId", m.sourceId)
                            .put("sourceName", m.sourceName)
                            .put("status", m.status)
                            .put("type", m.type)
                            .put("inLibrary", m.inLibrary)
                            .put("category", m.category)
                            .put("lastReadChapterId", m.lastReadChapterId)
                            .put("lastReadChapterName", m.lastReadChapterName)
                            .put("lastReadPage", m.lastReadPage)
                            .put("lastReadTimestamp", m.lastReadTimestamp)
                            .put("unreadCount", m.unreadCount)
                            .put("bookmarkCount", m.bookmarkCount)
                            .put("rating", m.rating)
                            .put("genres", m.genres)
                    )
                }
            }
        )

        root.put(
            "chapters",
            JSONArray().also { arr ->
                for (c in db.chapterDao().getAllChapters()) {
                    arr.put(
                        JSONObject()
                            .put("id", c.id)
                            .put("mangaId", c.mangaId)
                            .put("chapterNumber", c.chapterNumber)
                            .put("name", c.name)
                            .put("scanlator", c.scanlator)
                            .put("releaseDate", c.releaseDate)
                            .put("read", c.read)
                            .put("bookmarked", c.bookmarked)
                            .put("lastPageRead", c.lastPageRead)
                            .put("totalPages", c.totalPages)
                            .put("fetchUrl", c.fetchUrl)
                            .put("dateUpload", c.dateUpload)
                    )
                }
            }
        )

        root.toString(2)
    }

    /** Restore a backup JSON produced by [exportBackupJson]. Returns an error string, or null. */
    suspend fun importBackupJson(json: String): String? = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(json)
            if (root.optString("app") != "Nekoread") return@withContext "Not a Nekoread backup file"

            val categories = mutableListOf<CategoryEntity>()
            val repos = mutableListOf<ExtensionRepoEntity>()
            val extensions = mutableListOf<ExtensionEntity>()
            val sources = mutableListOf<ExtensionSourceEntity>()
            val manga = mutableListOf<MangaEntity>()
            val chapters = mutableListOf<ChapterEntity>()

            for (i in 0 until (root.optJSONArray("categories")?.length() ?: 0)) {
                val o = root.optJSONArray("categories")!!.getJSONObject(i)
                categories.add(CategoryEntity(o.getString("id"), o.getString("name"), o.getInt("sortOrder")))
            }
            for (i in 0 until (root.optJSONArray("repos")?.length() ?: 0)) {
                val o = root.optJSONArray("repos")!!.getJSONObject(i)
                repos.add(
                    ExtensionRepoEntity(
                        id = o.getString("id"), name = o.getString("name"), url = o.getString("url"),
                        extensionCount = o.optInt("extensionCount"), lastUpdated = o.optLong("lastUpdated"),
                        addedDate = o.optLong("addedDate", System.currentTimeMillis())
                    )
                )
            }
            for (i in 0 until (root.optJSONArray("extensions")?.length() ?: 0)) {
                val o = root.optJSONArray("extensions")!!.getJSONObject(i)
                extensions.add(
                    ExtensionEntity(
                        packageName = o.getString("packageName"), repoId = o.getString("repoId"),
                        name = o.getString("name"), versionName = o.getString("versionName"),
                        versionCode = o.getString("versionCode"), libVersion = o.optString("libVersion"),
                        contentWarning = o.optString("contentWarning"), apkUrl = o.getString("apkUrl"),
                        iconUrl = o.optString("iconUrl"), nsfw = o.optBoolean("nsfw"),
                        isInstalled = o.optBoolean("isInstalled"), installedVersionName = o.optString("installedVersionName"),
                        installError = o.optString("installError"), sourcesJson = o.optString("sourcesJson")
                    )
                )
            }
            for (i in 0 until (root.optJSONArray("sources")?.length() ?: 0)) {
                val o = root.optJSONArray("sources")!!.getJSONObject(i)
                sources.add(
                    ExtensionSourceEntity(
                        id = o.getString("id"), extensionPkg = o.optString("extensionPkg"),
                        repoId = o.optString("repoId"), name = o.getString("name"), version = o.getString("version"),
                        lang = o.optString("lang"), iconUrl = o.optString("iconUrl"),
                        isInstalled = o.optBoolean("isInstalled"), isNsfw = o.optBoolean("isNsfw"),
                        baseUrl = o.optString("baseUrl"), sourceType = o.optString("sourceType"),
                        sourceName = o.optString("sourceName")
                    )
                )
            }
            for (i in 0 until (root.optJSONArray("manga")?.length() ?: 0)) {
                val o = root.optJSONArray("manga")!!.getJSONObject(i)
                manga.add(
                    MangaEntity(
                        id = o.getString("id"), title = o.getString("title"), coverUrl = o.optString("coverUrl"),
                        author = o.optString("author"), artist = o.optString("artist"), description = o.optString("description"),
                        sourceId = o.getString("sourceId"), sourceName = o.optString("sourceName"),
                        status = o.optString("status"), type = o.optString("type"),
                        inLibrary = o.optBoolean("inLibrary"), category = o.optString("category"),
                        lastReadChapterId = o.optString("lastReadChapterId"), lastReadChapterName = o.optString("lastReadChapterName"),
                        lastReadPage = o.optInt("lastReadPage", 1), lastReadTimestamp = o.optLong("lastReadTimestamp"),
                        unreadCount = o.optInt("unreadCount"), bookmarkCount = o.optInt("bookmarkCount"),
                        rating = o.optDouble("rating", 4.8).toFloat(), genres = o.optString("genres")
                    )
                )
            }
            for (i in 0 until (root.optJSONArray("chapters")?.length() ?: 0)) {
                val o = root.optJSONArray("chapters")!!.getJSONObject(i)
                chapters.add(
                    ChapterEntity(
                        id = o.getString("id"), mangaId = o.getString("mangaId"),
                        chapterNumber = o.optDouble("chapterNumber", -1.0).toFloat(), name = o.getString("name"),
                        scanlator = o.optString("scanlator"), releaseDate = o.optString("releaseDate"),
                        read = o.optBoolean("read"), bookmarked = o.optBoolean("bookmarked"),
                        lastPageRead = o.optInt("lastPageRead", 1), totalPages = o.optInt("totalPages", 20),
                        fetchUrl = o.optString("fetchUrl"), dateUpload = o.optLong("dateUpload", System.currentTimeMillis())
                    )
                )
            }

            if (categories.isNotEmpty()) db.categoryDao().insertCategories(categories)
            if (repos.isNotEmpty()) db.extensionDao().insertRepos(repos)
            if (extensions.isNotEmpty()) db.extensionDao().insertExtensions(extensions)
            if (sources.isNotEmpty()) db.extensionDao().insertSources(sources)
            if (manga.isNotEmpty()) db.mangaDao().insertMangaList(manga)
            if (chapters.isNotEmpty()) db.chapterDao().insertChapters(chapters)

            null
        } catch (e: Exception) {
            "Import failed: ${e.message ?: "invalid file"}"
        }
    }
}
