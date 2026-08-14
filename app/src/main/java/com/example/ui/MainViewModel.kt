package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.CategoryEntity
import com.example.data.local.ChapterEntity
import com.example.data.local.ExtensionRepoEntity
import com.example.data.local.ExtensionSourceEntity
import com.example.data.local.ExtensionEntity
import com.example.data.local.MangaEntity
import com.example.data.repository.MangaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ReaderMode {
    WEBTOON, LEFT_TO_RIGHT, RIGHT_TO_LEFT
}

enum class ReaderBg {
    PURE_BLACK, DARK_GRAY, CREAM, WHITE
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository: MangaRepository = MangaRepository(AppDatabase.getInstance(application), application)

    init {
        viewModelScope.launch {
            repository.initializeDefaultDataIfNeeded()
            // Refresh repo catalogs that have never been fetched (first launch / new repo).
            repository.refreshStaleRepos()
        }
    }

    // Settings State
    private val _readerMode = MutableStateFlow(ReaderMode.WEBTOON)
    val readerMode: StateFlow<ReaderMode> = _readerMode.asStateFlow()

    private val _readerBg = MutableStateFlow(ReaderBg.PURE_BLACK)
    val readerBg: StateFlow<ReaderBg> = _readerBg.asStateFlow()

    private val _showPageNumber = MutableStateFlow(true)
    val showPageNumber: StateFlow<Boolean> = _showPageNumber.asStateFlow()

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

    private val _catalogSourceName = MutableStateFlow("MangaDex")
    val catalogSourceName: StateFlow<String> = _catalogSourceName.asStateFlow()

    // Detail screen loading state
    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    private val _detailError = MutableStateFlow<String?>(null)
    val detailError: StateFlow<String?> = _detailError.asStateFlow()

    fun loadCatalog(sourceId: String = "mangadex", query: String = "", page: Int = 0) {
        viewModelScope.launch {
            _catalogSourceName.value = repository.sourceForManga("$sourceId:x").name
            _catalogLoading.value = true
            _catalogError.value = null
            try {
                _catalogResults.value = repository.searchCatalog(sourceId, query, page)
            } catch (e: Exception) {
                _catalogResults.value = emptyList()
                _catalogError.value = e.message ?: "Failed to load catalog"
            } finally {
                _catalogLoading.value = false
            }
        }
    }

    fun clearCatalog() {
        _catalogResults.value = emptyList()
        _catalogError.value = null
    }

    fun loadMangaDetail(mangaId: String) {
        viewModelScope.launch {
            _detailLoading.value = true
            _detailError.value = null
            try {
                repository.ensureMangaInDb(mangaId)
                repository.loadChapters(mangaId)
            } catch (e: Exception) {
                _detailError.value = e.message ?: "Failed to load manga"
            } finally {
                _detailLoading.value = false
            }
        }
    }

    fun setReaderMode(mode: ReaderMode) {
        _readerMode.value = mode
    }

    fun setReaderBg(bg: ReaderBg) {
        _readerBg.value = bg
    }

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

    fun installExtension(packageName: String) {
        viewModelScope.launch {
            _opBusy.value = "install_$packageName"
            val error = repository.installExtension(packageName)
            _opBusy.value = null
            if (error != null) _opMessage.value = error
        }
    }

    fun uninstallExtension(packageName: String) {
        viewModelScope.launch {
            _opBusy.value = "uninstall_$packageName"
            val error = repository.uninstallExtension(packageName)
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
