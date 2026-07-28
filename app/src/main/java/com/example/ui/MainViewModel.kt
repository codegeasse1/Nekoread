package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.CategoryEntity
import com.example.data.local.ChapterEntity
import com.example.data.local.ExtensionRepoEntity
import com.example.data.local.ExtensionSourceEntity
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

    val repository: MangaRepository = MangaRepository(AppDatabase.getInstance(application))

    init {
        viewModelScope.launch {
            repository.initializeDefaultDataIfNeeded()
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
            repository.addExtensionRepo(url, name)
        }
    }

    fun deleteExtensionRepo(id: String) {
        viewModelScope.launch {
            repository.deleteExtensionRepo(id)
        }
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
