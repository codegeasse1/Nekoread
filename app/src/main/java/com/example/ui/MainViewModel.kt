package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.CategoryEntity
import com.example.data.local.ChapterEntity
import com.example.data.extension.ExtensionDexLoader
import com.example.data.local.ExtensionRepoEntity
import com.example.data.local.ExtensionSourceEntity
import com.example.data.local.ExtensionEntity
import com.example.data.local.MangaEntity
import com.example.data.repository.MangaRepository
import com.example.util.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ReaderMode {
    WEBTOON, WEBTOON_GAPS, LEFT_TO_RIGHT, RIGHT_TO_LEFT, VERTICAL
}

enum class ReaderBg {
    PURE_BLACK, DARK_GRAY, CREAM, WHITE
}

enum class ReaderFit {
    FIT, FIT_WIDTH, FIT_HEIGHT
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository: MangaRepository = MangaRepository(AppDatabase.getInstance(application), application)

    private val prefs = application.getSharedPreferences("nekoread_settings", Context.MODE_PRIVATE)

    init {
        viewModelScope.launch {
            // Tachiyomi extensions need the shared network stack before any source is constructed.
            eu.kanade.tachiyomi.network.NetworkHelper.init(getApplication())
            repository.initializeDefaultDataIfNeeded()
            // Collapse duplicate repo rows (same repo added via different URL forms) before
            // re-registering installed extensions so no stale source rows linger.
            repository.dedupeRepos()
            // Bring installed extensions' sources back online (their APKs stay in app storage).
            repository.loadInstalledExtensions()
            // Refresh every repo's catalog so new extension versions / new extensions show up
            // automatically on app open (this is the app's auto-update check for extensions).
            repository.refreshAllRepos()
        }
    }

    // Settings State
    private val _readerMode = MutableStateFlow(ReaderMode.WEBTOON)
    val readerMode: StateFlow<ReaderMode> = _readerMode.asStateFlow()

    private val _readerBg = MutableStateFlow(ReaderBg.PURE_BLACK)
    val readerBg: StateFlow<ReaderBg> = _readerBg.asStateFlow()

    private val _showPageNumber = MutableStateFlow(true)
    val showPageNumber: StateFlow<Boolean> = _showPageNumber.asStateFlow()

    private val _readerFit = MutableStateFlow(ReaderFit.FIT)
    val readerFit: StateFlow<ReaderFit> = _readerFit.asStateFlow()

    init {
        // Load persisted reader settings (stored in SharedPreferences, survives app restarts).
        _readerMode.value = ReaderMode.valueOf(prefs.getString("reader_mode", ReaderMode.WEBTOON.name)!!)
        _readerBg.value = ReaderBg.valueOf(prefs.getString("reader_bg", ReaderBg.PURE_BLACK.name)!!)
        _showPageNumber.value = prefs.getBoolean("show_page_number", true)
        _readerFit.value = ReaderFit.valueOf(prefs.getString("reader_fit", ReaderFit.FIT.name)!!)
    }

    // Library Filter & Search
    private val _librarySearchQuery = MutableStateFlow("")
    val librarySearchQuery: StateFlow<String> = _librarySearchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryManga: StateFlow<List<MangaEntity>> = combine(
        repository.libraryManga,
        _selectedCategory,
        _librarySearchQuery
    ) { mangaList, category, query ->
        mangaList.filter { manga ->
            val matchesCategory = (category == "All" || manga.category.equals(category, ignoreCase = true))
            val matchesQuery = query.isBlank() || manga.title.contains(query, ignoreCase = true) || manga.genres.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readingHistory: StateFlow<List<MangaEntity>> = repository.readingHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val extensionRepos: StateFlow<List<ExtensionRepoEntity>> = repository.extensionRepos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val extensionSources: StateFlow<List<ExtensionSourceEntity>> = repository.extensionSources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val extensions: StateFlow<List<ExtensionEntity>> = repository.extensions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _opMessage = MutableStateFlow<String?>(null)
    val opMessage: StateFlow<String?> = _opMessage.asStateFlow()

    private val _opBusy = MutableStateFlow<String?>(null)
    val opBusy: StateFlow<String?> = _opBusy.asStateFlow()

    // Live catalog browsing state (real sources)
    private val _catalogResults = MutableStateFlow<List<MangaEntity>>(emptyList())
    val catalogResults: StateFlow<List<MangaEntity>> = _catalogResults.asStateFlow()

    private val _catalogLoading = MutableStateFlow(false)
    val catalogLoading: StateFlow<Boolean> = _catalogLoading.asStateFlow()

    private val _catalogError = MutableStateFlow<String?>(null)
    val catalogError: StateFlow<String?> = _catalogError.asStateFlow()

    private val _catalogSourceName = MutableStateFlow("")
    val catalogSourceName: StateFlow<String> = _catalogSourceName.asStateFlow()

    // Global search across all installed sources
    private val _globalResults = MutableStateFlow<List<MangaEntity>>(emptyList())
    val globalResults: StateFlow<List<MangaEntity>> = _globalResults.asStateFlow()

    private val _globalLoading = MutableStateFlow(false)
    val globalLoading: StateFlow<Boolean> = _globalLoading.asStateFlow()

    private val _globalError = MutableStateFlow<String?>(null)
    val globalError: StateFlow<String?> = _globalError.asStateFlow()

    private val _globalSearchedSources = MutableStateFlow(0)
    val globalSearchedSources: StateFlow<Int> = _globalSearchedSources.asStateFlow()

    // Detail screen loading state
    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    private val _detailError = MutableStateFlow<String?>(null)
    val detailError: StateFlow<String?> = _detailError.asStateFlow()

    // Extension catalog pages are 1-BASED (keiyoushi sources pass `page` straight into the request
    // URL, e.g. 4KHD's WordPress REST API 400s on page=0; ComicLand computes offset=(page-1)*20).
    // So the first catalog request must be page 1 — starting at 0 is what broke 4KHD with
    // "HTTP error 400 — ...&page=0..." while the same URL with page=1 returns 200.
    private var lastCatalogKey: String? = null

    fun loadCatalog(sourceId: String = "", query: String = "", page: Int = 1) {
        if (sourceId.isBlank()) return
        val key = "$sourceId\u0000$query\u0000$page"
        // Re-running the exact same catalog fetch (e.g. returning to Browse after viewing a manga
        // detail screen) is a no-op — the results are already on screen, so don't flash a reload.
        if (key == lastCatalogKey && _catalogResults.value.isNotEmpty()) return
        viewModelScope.launch {
            _catalogLoading.value = true
            _catalogError.value = null
            try {
                _catalogSourceName.value = repository.sourceForManga("$sourceId:x").name
                _catalogResults.value = repository.searchCatalog(sourceId, query, page)
                lastCatalogKey = key
            } catch (e: Throwable) {
                _catalogResults.value = emptyList()
                _catalogError.value = e.describe()
            } finally {
                _catalogLoading.value = false
            }
        }
    }

    fun clearCatalog() {
        _catalogResults.value = emptyList()
        _catalogError.value = null
        lastCatalogKey = null
    }

    // A tag/genre chip tapped on a detail screen: stash (sourceId, tag) so BrowseScreen can open
    // that source's catalog pre-filled with the tag search when it next composes.
    private val _pendingCatalogSearch = MutableStateFlow<Pair<String, String>?>(null)
    val pendingCatalogSearch: StateFlow<Pair<String, String>?> = _pendingCatalogSearch.asStateFlow()

    fun openTagSearch(sourceId: String, tag: String) {
        _pendingCatalogSearch.value = sourceId to tag
    }

    fun consumePendingCatalogSearch(): Pair<String, String>? {
        val v = _pendingCatalogSearch.value
        _pendingCatalogSearch.value = null
        return v
    }

    fun globalSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) {
            _globalResults.value = emptyList()
            _globalSearchedSources.value = 0
            _globalError.value = null
            return
        }
        viewModelScope.launch {
            _globalLoading.value = true
            _globalError.value = null
            _globalSearchedSources.value = ExtensionDexLoader.loaded.size
            try {
                _globalResults.value = repository.searchAllInstalledSources(q)
            } catch (e: Throwable) {
                _globalResults.value = emptyList()
                _globalError.value = e.describe()
            } finally {
                _globalLoading.value = false
            }
        }
    }

    fun clearGlobalSearch() {
        _globalResults.value = emptyList()
        _globalSearchedSources.value = 0
        _globalError.value = null
    }

    fun loadMangaDetail(mangaId: String) {
        viewModelScope.launch {
            _detailLoading.value = true
            _detailError.value = null
            try {
                repository.ensureMangaInDb(mangaId)
                // Force a chapter-list refresh so cached chapters get corrected 1..N numbers
                // (they were once numbered inverted); loadChapters preserves read state.
                repository.loadChapters(mangaId, force = true)
            } catch (e: Throwable) {
                _detailError.value = e.describe()
            } finally {
                _detailLoading.value = false
            }
        }
    }

    fun setReaderMode(mode: ReaderMode) {
        _readerMode.value = mode
        prefs.edit().putString("reader_mode", mode.name).apply()
    }

    fun setReaderBg(bg: ReaderBg) {
        _readerBg.value = bg
        prefs.edit().putString("reader_bg", bg.name).apply()
    }

    fun setReaderFit(fit: ReaderFit) {
        _readerFit.value = fit
        prefs.edit().putString("reader_fit", fit.name).apply()
    }

    fun setShowPageNumber(show: Boolean) {
        _showPageNumber.value = show
        prefs.edit().putBoolean("show_page_number", show).apply()
    }

    suspend fun exportBackup(): String = repository.exportBackupJson()

    suspend fun importBackup(json: String): String? = repository.importBackupJson(json)

    fun setLibrarySearchQuery(query: String) {
        _librarySearchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun toggleLibraryStatus(mangaId: String, category: String = "Reading") {
        viewModelScope.launch {
            repository.toggleLibraryStatus(mangaId, category)
        }
    }

    fun updateMangaCategory(mangaId: String, category: String) {
        viewModelScope.launch {
            repository.updateMangaCategory(mangaId, category)
        }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun removeHistory(mangaId: String) {
        viewModelScope.launch { repository.removeHistory(mangaId) }
    }

    fun clearLibrary() {
        viewModelScope.launch { repository.clearLibrary() }
    }

    fun removeFromLibrary(mangaIds: List<String>) {
        if (mangaIds.isEmpty()) return
        viewModelScope.launch { repository.removeFromLibrary(mangaIds) }
    }

    fun addExtensionRepo(url: String, name: String) {
        viewModelScope.launch {
            _opBusy.value = "repo_add"
            val error = repository.addExtensionRepo(url, name)
            _opBusy.value = null
            _opMessage.value = error ?: "Repository added"
        }
    }

    fun refreshExtensionRepo(id: String) {
        viewModelScope.launch {
            _opBusy.value = "repo_refresh_$id"
            val error = repository.refreshExtensionRepo(id)
            _opBusy.value = null
            _opMessage.value = error ?: "Repository refreshed"
        }
    }

    fun deleteExtensionRepo(id: String) {
        viewModelScope.launch {
            _opBusy.value = "repo_delete_$id"
            repository.deleteExtensionRepo(id)
            _opBusy.value = null
            _opMessage.value = "Repository removed"
        }
    }

    fun installExtension(packageName: String, repoId: String) {
        viewModelScope.launch {
            _opBusy.value = "install_${packageName}_$repoId"
            val error = repository.installExtension(packageName, repoId)
            _opBusy.value = null
            if (error != null) _opMessage.value = error
        }
    }

    fun updateAllExtensions(exts: List<ExtensionEntity>) {
        if (exts.isEmpty()) return
        viewModelScope.launch {
            _opBusy.value = "update_all"
            var firstError: String? = null
            for (ext in exts) {
                val error = repository.installExtension(ext.packageName, ext.repoId)
                if (error != null && firstError == null) firstError = error
            }
            _opBusy.value = null
            _opMessage.value = firstError ?: "All extensions updated"
        }
    }

    fun uninstallExtension(packageName: String, repoId: String) {
        viewModelScope.launch {
            _opBusy.value = "uninstall_${packageName}_$repoId"
            val error = repository.uninstallExtension(packageName, repoId)
            _opBusy.value = null
            _opMessage.value = error ?: "Extension uninstalled"
        }
    }

    fun clearOpMessage() {
        _opMessage.value = null
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            repository.addCategory(name)
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            repository.deleteCategory(id)
        }
    }

    fun toggleChapterRead(chapterId: String) {
        viewModelScope.launch {
            repository.toggleChapterRead(chapterId)
        }
    }

    fun toggleChapterBookmark(chapterId: String) {
        viewModelScope.launch {
            repository.toggleChapterBookmark(chapterId)
        }
    }

    fun markPreviousChaptersRead(mangaId: String, chapterNumber: Float) {
        viewModelScope.launch {
            repository.markPreviousChaptersRead(mangaId, chapterNumber)
        }
    }

    fun saveProgress(mangaId: String, chapterId: String, chapterName: String, page: Int) {
        viewModelScope.launch {
            repository.saveReadingProgress(mangaId, chapterId, chapterName, page)
        }
    }
}
