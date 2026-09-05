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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ReaderMode {
    WEBTOON, WEBTOON_GAPS, LEFT_TO_RIGHT, RIGHT_TO_LEFT, VERTICAL
}

enum class ReaderBg {
    PURE_BLACK, DARK_GRAY, CREAM, WHITE
}

enum class ReaderFit {
    FIT, STRETCH, FIT_WIDTH, FIT_HEIGHT, ORIGINAL_SIZE, SMART_FIT
}

// Yomi-style reader orientation lock: AUTO follows the system, PORTRAIT/LANDSCAPE force the
// screen orientation while the reader is open (restored to AUTO when the reader closes).
enum class ReaderOrientation {
    AUTO, PORTRAIT, LANDSCAPE
}

// One source's slice of a global search. Sections are emitted as soon as that source answers, so
// the UI can stream results in source-by-source instead of waiting for all sources to finish.
data class GlobalSearchSection(
    val sourceId: String,
    val sourceName: String,
    val manga: List<MangaEntity>,
)

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

    private val _readerFit = MutableStateFlow(ReaderFit.FIT_WIDTH)
    val readerFit: StateFlow<ReaderFit> = _readerFit.asStateFlow()

    private val _readerOrientation = MutableStateFlow(ReaderOrientation.AUTO)
    val readerOrientation: StateFlow<ReaderOrientation> = _readerOrientation.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(true)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _webtoonFade = MutableStateFlow(false)
    val webtoonFade: StateFlow<Boolean> = _webtoonFade.asStateFlow()

    private val _autoScroll = MutableStateFlow(false)
    val autoScroll: StateFlow<Boolean> = _autoScroll.asStateFlow()

    private val _autoScrollSpeedDp = MutableStateFlow(80f)
    val autoScrollSpeedDp: StateFlow<Float> = _autoScrollSpeedDp.asStateFlow()

    private val _readerQuality = MutableStateFlow(75)
    val readerQuality: StateFlow<Int> = _readerQuality.asStateFlow()

    private val _cropBorders = MutableStateFlow(false)
    val cropBorders: StateFlow<Boolean> = _cropBorders.asStateFlow()

    private val _doubleTapZoom = MutableStateFlow(true)
    val doubleTapZoom: StateFlow<Boolean> = _doubleTapZoom.asStateFlow()

    private val _pinchToZoom = MutableStateFlow(true)
    val pinchToZoom: StateFlow<Boolean> = _pinchToZoom.asStateFlow()

    private val _tapToChangePages = MutableStateFlow(false)
    val tapToChangePages: StateFlow<Boolean> = _tapToChangePages.asStateFlow()

    // Webtoon-mode settings ported from chimahon.
    private val _webtoonCropBorders = MutableStateFlow(false)
    val webtoonCropBorders: StateFlow<Boolean> = _webtoonCropBorders.asStateFlow()

    private val _cropBordersPaged = MutableStateFlow(false)
    val cropBordersPaged: StateFlow<Boolean> = _cropBordersPaged.asStateFlow()

    private val _cropBordersContinuous = MutableStateFlow(false)
    val cropBordersContinuous: StateFlow<Boolean> = _cropBordersContinuous.asStateFlow()

    private val _webtoonSidePadding = MutableStateFlow(0)
    val webtoonSidePadding: StateFlow<Int> = _webtoonSidePadding.asStateFlow()

    private val _webtoonNavigationMode = MutableStateFlow(5)
    val webtoonNavigationMode: StateFlow<Int> = _webtoonNavigationMode.asStateFlow()

    private val _webtoonNavInverted = MutableStateFlow(TappingInvertMode.NONE)
    val webtoonNavInverted: StateFlow<TappingInvertMode> = _webtoonNavInverted.asStateFlow()

    private val _webtoonSmallerTapZone = MutableStateFlow(false)
    val webtoonSmallerTapZone: StateFlow<Boolean> = _webtoonSmallerTapZone.asStateFlow()

    private val _webtoonScaleType = MutableStateFlow(WebtoonScaleType.FIT)
    val webtoonScaleType: StateFlow<WebtoonScaleType> = _webtoonScaleType.asStateFlow()

    private val _longStripGapSmartScale = MutableStateFlow(false)
    val longStripGapSmartScale: StateFlow<Boolean> = _longStripGapSmartScale.asStateFlow()

    private val _webtoonDisableZoomOut = MutableStateFlow(false)
    val webtoonDisableZoomOut: StateFlow<Boolean> = _webtoonDisableZoomOut.asStateFlow()

    private val _webtoonPageTransitions = MutableStateFlow(true)
    val webtoonPageTransitions: StateFlow<Boolean> = _webtoonPageTransitions.asStateFlow()

    private val _webtoonSmoothAutoScroll = MutableStateFlow(true)
    val webtoonSmoothAutoScroll: StateFlow<Boolean> = _webtoonSmoothAutoScroll.asStateFlow()

    private val _alwaysDecodeLongStripWithSSIV = MutableStateFlow(false)
    val alwaysDecodeLongStripWithSSIV: StateFlow<Boolean> = _alwaysDecodeLongStripWithSSIV.asStateFlow()

    private val _continuousVerticalTappingByPage = MutableStateFlow(false)
    val continuousVerticalTappingByPage: StateFlow<Boolean> = _continuousVerticalTappingByPage.asStateFlow()

    private val _readerHideThreshold = MutableStateFlow(ReaderHideThreshold.LOW)
    val readerHideThreshold: StateFlow<ReaderHideThreshold> = _readerHideThreshold.asStateFlow()

    private val _doubleTapAnimDuration = MutableStateFlow(500)
    val doubleTapAnimDuration: StateFlow<Int> = _doubleTapAnimDuration.asStateFlow()

    private val _showReadingMode = MutableStateFlow(true)
    val showReadingMode: StateFlow<Boolean> = _showReadingMode.asStateFlow()

    // Color options ported from chimahon's color-filter tab.
    private val _customBrightness = MutableStateFlow(false)
    val customBrightness: StateFlow<Boolean> = _customBrightness.asStateFlow()

    private val _customBrightnessValue = MutableStateFlow(0)
    val customBrightnessValue: StateFlow<Int> = _customBrightnessValue.asStateFlow()

    private val _colorFilter = MutableStateFlow(false)
    val colorFilter: StateFlow<Boolean> = _colorFilter.asStateFlow()

    private val _colorFilterValue = MutableStateFlow(0)
    val colorFilterValue: StateFlow<Int> = _colorFilterValue.asStateFlow()

    private val _colorFilterMode = MutableStateFlow(0)
    val colorFilterMode: StateFlow<Int> = _colorFilterMode.asStateFlow()

    private val _grayscale = MutableStateFlow(false)
    val grayscale: StateFlow<Boolean> = _grayscale.asStateFlow()

    private val _invertedColors = MutableStateFlow(false)
    val invertedColors: StateFlow<Boolean> = _invertedColors.asStateFlow()

    // Per-series reader overrides: a manga can pin its own reading mode (the global mode still
    // applies everywhere else). Enabled state and mode are stored per manga id in prefs.
    private val _seriesOverrideEnabled = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val seriesOverrideEnabled: StateFlow<Map<String, Boolean>> = _seriesOverrideEnabled.asStateFlow()

    private val _seriesReaderMode = MutableStateFlow<Map<String, ReaderMode>>(emptyMap())
    val seriesReaderMode: StateFlow<Map<String, ReaderMode>> = _seriesReaderMode.asStateFlow()

    init {
        // Load persisted reader settings (stored in SharedPreferences, survives app restarts).
        _readerMode.value = ReaderMode.valueOf(prefs.getString("reader_mode", ReaderMode.WEBTOON.name)!!)
        _readerBg.value = ReaderBg.valueOf(prefs.getString("reader_bg", ReaderBg.PURE_BLACK.name)!!)
        _showPageNumber.value = prefs.getBoolean("show_page_number", true)
        _cropBorders.value = prefs.getBoolean("reader_crop_borders", false)
        _doubleTapZoom.value = prefs.getBoolean("reader_double_tap_zoom", true)
        _tapToChangePages.value = prefs.getBoolean("reader_tap_change_pages", false)
        _pinchToZoom.value = prefs.getBoolean("reader_pinch_to_zoom", true)
        _webtoonCropBorders.value = prefs.getBoolean("reader_webtoon_crop_borders", false)
        _cropBordersPaged.value = prefs.getBoolean("reader_crop_borders_paged", false)
        _cropBordersContinuous.value = prefs.getBoolean("reader_crop_borders_continuous", false)
        _webtoonSidePadding.value = prefs.getInt("reader_webtoon_side_padding", 0).coerceIn(0, 25)
        _webtoonNavigationMode.value = prefs.getInt("reader_navigation_mode_webtoon", 5).coerceIn(0, 5)
        // One-time migration: users who had "Tap to change pages" enabled before tap zones existed
        // get the L (default) tap-zone scheme in webtoon mode instead of the (now separate)
        // Disabled default, so their old behavior doesn't silently vanish.
        if (!prefs.getBoolean("webtoon_nav_migrated", false)) {
            prefs.edit().putBoolean("webtoon_nav_migrated", true).apply()
            if (!prefs.contains("reader_navigation_mode_webtoon") && prefs.getBoolean("reader_tap_change_pages", false)) {
                _webtoonNavigationMode.value = 0
                prefs.edit().putInt("reader_navigation_mode_webtoon", 0).apply()
            }
        }
        _webtoonNavInverted.value = runCatching {
            TappingInvertMode.valueOf(prefs.getString("reader_webtoon_nav_inverted", TappingInvertMode.NONE.name)!!)
        }.getOrDefault(TappingInvertMode.NONE)
        _webtoonSmallerTapZone.value = prefs.getBoolean("reader_webtoon_smaller_tap_zone", false)
        _webtoonScaleType.value = runCatching {
            WebtoonScaleType.valueOf(prefs.getString("reader_webtoon_scale_type", WebtoonScaleType.FIT.name)!!)
        }.getOrDefault(WebtoonScaleType.FIT)
        _longStripGapSmartScale.value = prefs.getBoolean("reader_long_strip_gap_smart_scale", false)
        _webtoonDisableZoomOut.value = prefs.getBoolean("reader_webtoon_disable_zoom_out", false)
        _webtoonPageTransitions.value = prefs.getBoolean("reader_webtoon_page_transitions", true)
        _webtoonSmoothAutoScroll.value = prefs.getBoolean("reader_webtoon_smooth_auto_scroll", true)
        _alwaysDecodeLongStripWithSSIV.value = prefs.getBoolean("reader_webtoon_always_ssiv", false)
        _continuousVerticalTappingByPage.value = prefs.getBoolean("reader_webtoon_tap_by_page", false)
        _readerHideThreshold.value = runCatching {
            ReaderHideThreshold.valueOf(prefs.getString("reader_hide_threshold", ReaderHideThreshold.LOW.name)!!)
        }.getOrDefault(ReaderHideThreshold.LOW)
        _doubleTapAnimDuration.value = prefs.getInt("reader_double_tap_anim_duration", 500).coerceIn(100, 1000)
        _showReadingMode.value = prefs.getBoolean("reader_show_reading_mode", true)
        _customBrightness.value = prefs.getBoolean("reader_custom_brightness", false)
        _customBrightnessValue.value = prefs.getInt("reader_custom_brightness_value", 0).coerceIn(-75, 100)
        _colorFilter.value = prefs.getBoolean("reader_color_filter", false)
        _colorFilterValue.value = prefs.getInt("reader_color_filter_value", 0)
        _colorFilterMode.value = prefs.getInt("reader_color_filter_mode", 0).coerceIn(0, 5)
        _grayscale.value = prefs.getBoolean("reader_grayscale", false)
        _invertedColors.value = prefs.getBoolean("reader_inverted_colors", false)
        _seriesOverrideEnabled.value = prefs.all.mapNotNull { (k, v) ->
            if (k.startsWith("series_override_") && v is Boolean) k.removePrefix("series_override_") to v else null
        }.toMap()
        _seriesReaderMode.value = prefs.all.mapNotNull { (k, v) ->
            if (k.startsWith("series_mode_") && v is String) {
                runCatching { k.removePrefix("series_mode_") to ReaderMode.valueOf(v) }.getOrNull()
            } else null
        }.toMap()
        // One-time migration: the OLD default fit was Fit Screen; the new default is Fit Width.
        // Devices that still hold the old default value get switched to Fit Width exactly once —
        // any choice made after that is always respected.
        if (!prefs.getBoolean("fit_width_migrated", false)) {
            prefs.edit().putBoolean("fit_width_migrated", true).apply()
            if (prefs.getString("reader_fit", null) == ReaderFit.FIT.name) {
                prefs.edit().putString("reader_fit", ReaderFit.FIT_WIDTH.name).apply()
            }
        }
        _readerFit.value = runCatching {
            ReaderFit.valueOf(prefs.getString("reader_fit", ReaderFit.FIT_WIDTH.name)!!)
        }.getOrDefault(ReaderFit.FIT_WIDTH)
        _readerOrientation.value = runCatching {
            ReaderOrientation.valueOf(prefs.getString("reader_orientation", ReaderOrientation.AUTO.name)!!)
        }.getOrDefault(ReaderOrientation.AUTO)
        _keepScreenOn.value = prefs.getBoolean("reader_keep_screen_on", true)
        _webtoonFade.value = prefs.getBoolean("reader_webtoon_fade", false)
        _autoScroll.value = prefs.getBoolean("reader_auto_scroll", false)
        _autoScrollSpeedDp.value = prefs.getFloat("reader_auto_scroll_speed", 80f)
        _readerQuality.value = when (prefs.getInt("reader_quality", 75)) {
            50 -> 50
            100 -> 100
            else -> 75
        }
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

    // Tadami-style catalog tab: "latest" (default) or "popular". Search/filter is expressed as a
    // non-blank query on top of whichever tab is active.
    private val _catalogMode = MutableStateFlow("latest")
    val catalogMode: StateFlow<String> = _catalogMode.asStateFlow()

    private val _catalogLoadingMore = MutableStateFlow(false)
    val catalogLoadingMore: StateFlow<Boolean> = _catalogLoadingMore.asStateFlow()

    private val _catalogHasMore = MutableStateFlow(true)
    val catalogHasMore: StateFlow<Boolean> = _catalogHasMore.asStateFlow()

    // Set only when a catalog fetch actually fails with a Cloudflare challenge (not a slow load).
    private val _catalogNeedsVerification = MutableStateFlow(false)
    val catalogNeedsVerification: StateFlow<Boolean> = _catalogNeedsVerification.asStateFlow()

    fun consumeCatalogVerification() {
        _catalogNeedsVerification.value = false
    }

    // Global search across all installed sources — results stream in per source, so the first
    // sources to answer show up before slower ones finish.
    private val _globalSections = MutableStateFlow<List<GlobalSearchSection>>(emptyList())
    val globalSections: StateFlow<List<GlobalSearchSection>> = _globalSections.asStateFlow()

    private val _globalTotalSources = MutableStateFlow(0)
    val globalTotalSources: StateFlow<Int> = _globalTotalSources.asStateFlow()

    private val _globalLoading = MutableStateFlow(false)
    val globalLoading: StateFlow<Boolean> = _globalLoading.asStateFlow()

    private val _globalError = MutableStateFlow<String?>(null)
    val globalError: StateFlow<String?> = _globalError.asStateFlow()

    private val _globalSearchedSources = MutableStateFlow(0)
    val globalSearchedSources: StateFlow<Int> = _globalSearchedSources.asStateFlow()

    private var globalSearchJob: Job? = null

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
    private var catalogSource = ""
    private var catalogQuery = ""
    private var catalogPage = 1

    fun loadCatalog(sourceId: String = "", query: String = "", page: Int = 1, mode: String? = null) {
        if (sourceId.isBlank()) return
        if (mode != null) _catalogMode.value = mode
        catalogSource = sourceId
        catalogQuery = query
        catalogPage = page
        _catalogHasMore.value = true
        _catalogLoadingMore.value = false
        val key = "$sourceId\u0000$query\u0000$page\u0000${_catalogMode.value}"
        // Re-running the exact same catalog fetch (e.g. returning to Browse after viewing a manga
        // detail screen) is a no-op — the results are already on screen, so don't flash a reload.
        if (key == lastCatalogKey && _catalogResults.value.isNotEmpty()) return
        // Show the loading state immediately (synchronously), so switching sources / tag jumps never
        // flash the stale grid or the "No manga found" empty state while the fetch coroutine starts.
        _catalogLoading.value = true
        _catalogError.value = null
        _catalogNeedsVerification.value = false
        viewModelScope.launch {
            try {
                _catalogSourceName.value = repository.sourceForManga("$sourceId:x").name
                _catalogResults.value = repository.searchCatalog(sourceId, query, page, _catalogMode.value)
                lastCatalogKey = key
            } catch (e: Throwable) {
                _catalogResults.value = emptyList()
                _catalogError.value = e.describe()
                if (looksLikeCloudflare(e)) _catalogNeedsVerification.value = true
            } finally {
                _catalogLoading.value = false
            }
        }
    }

    fun setCatalogMode(mode: String) {
        _catalogMode.value = mode
        lastCatalogKey = null
    }

    /** Append the next catalog page (real pagination: page 2, page 3, ...) to the results shown in
     *  the catalog grid. Stops automatically when the source returns no new manga (end of list) or
     *  only items already on screen, so it can never loop the same first-page titles forever. */
    fun loadMoreCatalog() {
        if (catalogSource.isBlank()) return
        if (_catalogLoading.value || _catalogLoadingMore.value || !_catalogHasMore.value) return
        val nextPage = catalogPage + 1
        viewModelScope.launch {
            _catalogLoadingMore.value = true
            try {
                val fetched = repository.searchCatalog(catalogSource, catalogQuery, nextPage, _catalogMode.value)
                val existingIds = _catalogResults.value.map { it.id }.toHashSet()
                val fresh = fetched.filter { it.id !in existingIds }
                if (fresh.isEmpty()) {
                    _catalogHasMore.value = false
                } else {
                    _catalogResults.value += fresh
                    catalogPage = nextPage
                }
            } catch (e: Throwable) {
                _catalogHasMore.value = false
            } finally {
                _catalogLoadingMore.value = false
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
        stopGlobalSearch()
        if (q.isBlank()) {
            _globalSections.value = emptyList()
            _globalSearchedSources.value = 0
            _globalTotalSources.value = 0
            _globalError.value = null
            _globalLoading.value = false
            return
        }
        val adapters = ExtensionDexLoader.loaded
        _globalSections.value = emptyList()
        _globalSearchedSources.value = 0
        _globalTotalSources.value = adapters.size
        _globalLoading.value = adapters.isNotEmpty()
        _globalError.value = null
        if (adapters.isEmpty()) return

        // Search every source in parallel, but publish each source's results the moment it answers
        // (instead of waiting for all of them). Sources are also counted as "searched" as they
        // finish, so the badge reads "X of Y sources". A failing source just contributes nothing;
        // cancelling stops the remaining sources but keeps what already streamed in.
        globalSearchJob = viewModelScope.launch {
            try {
                val children = adapters.map { adapter ->
                    launch(Dispatchers.IO) {
                        val manga = try {
                            adapter.search(q, 1).distinctBy { it.id }.take(20)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            emptyList()
                        }
                        if (coroutineContext.isActive && manga.isNotEmpty()) {
                            _globalSections.update { it + GlobalSearchSection(adapter.id, adapter.name, manga) }
                        }
                        if (coroutineContext.isActive) {
                            _globalSearchedSources.update { it + 1 }
                        }
                    }
                }
                children.forEach { it.join() }
            } catch (e: CancellationException) {
                // Stopped by the user — whatever already streamed in stays visible.
            } catch (e: Throwable) {
                _globalError.value = e.describe()
            } finally {
                _globalLoading.value = false
            }
        }
    }

    fun stopGlobalSearch() {
        globalSearchJob?.cancel()
        globalSearchJob = null
    }

    fun clearGlobalSearch() {
        stopGlobalSearch()
        _globalSections.value = emptyList()
        _globalSearchedSources.value = 0
        _globalTotalSources.value = 0
        _globalError.value = null
        _globalLoading.value = false
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

    fun setCropBorders(enabled: Boolean) {
        _cropBorders.value = enabled
        prefs.edit().putBoolean("reader_crop_borders", enabled).apply()
    }

    fun setDoubleTapZoom(enabled: Boolean) {
        _doubleTapZoom.value = enabled
        prefs.edit().putBoolean("reader_double_tap_zoom", enabled).apply()
    }

    fun setPinchToZoom(enabled: Boolean) {
        _pinchToZoom.value = enabled
        prefs.edit().putBoolean("reader_pinch_to_zoom", enabled).apply()
    }

    fun setTapToChangePages(enabled: Boolean) {
        _tapToChangePages.value = enabled
        prefs.edit().putBoolean("reader_tap_change_pages", enabled).apply()
    }

    fun setWebtoonCropBorders(enabled: Boolean) {
        _webtoonCropBorders.value = enabled
        prefs.edit().putBoolean("reader_webtoon_crop_borders", enabled).apply()
    }

    fun setCropBordersPaged(enabled: Boolean) {
        _cropBordersPaged.value = enabled
        prefs.edit().putBoolean("reader_crop_borders_paged", enabled).apply()
    }

    fun setCropBordersContinuous(enabled: Boolean) {
        _cropBordersContinuous.value = enabled
        prefs.edit().putBoolean("reader_crop_borders_continuous", enabled).apply()
    }

    fun setWebtoonSidePadding(padding: Int) {
        val p = padding.coerceIn(0, 25)
        _webtoonSidePadding.value = p
        prefs.edit().putInt("reader_webtoon_side_padding", p).apply()
    }

    fun setWebtoonNavigationMode(mode: Int) {
        val m = mode.coerceIn(0, 5)
        _webtoonNavigationMode.value = m
        prefs.edit().putInt("reader_navigation_mode_webtoon", m).apply()
    }

    fun setWebtoonNavInverted(mode: TappingInvertMode) {
        _webtoonNavInverted.value = mode
        prefs.edit().putString("reader_webtoon_nav_inverted", mode.name).apply()
    }

    fun setWebtoonSmallerTapZone(enabled: Boolean) {
        _webtoonSmallerTapZone.value = enabled
        prefs.edit().putBoolean("reader_webtoon_smaller_tap_zone", enabled).apply()
    }

    fun setWebtoonScaleType(type: WebtoonScaleType) {
        _webtoonScaleType.value = type
        prefs.edit().putString("reader_webtoon_scale_type", type.name).apply()
    }

    fun setLongStripGapSmartScale(enabled: Boolean) {
        _longStripGapSmartScale.value = enabled
        prefs.edit().putBoolean("reader_long_strip_gap_smart_scale", enabled).apply()
    }

    fun setWebtoonDisableZoomOut(enabled: Boolean) {
        _webtoonDisableZoomOut.value = enabled
        prefs.edit().putBoolean("reader_webtoon_disable_zoom_out", enabled).apply()
    }

    fun setWebtoonPageTransitions(enabled: Boolean) {
        _webtoonPageTransitions.value = enabled
        prefs.edit().putBoolean("reader_webtoon_page_transitions", enabled).apply()
    }

    fun setWebtoonSmoothAutoScroll(enabled: Boolean) {
        _webtoonSmoothAutoScroll.value = enabled
        prefs.edit().putBoolean("reader_webtoon_smooth_auto_scroll", enabled).apply()
    }

    fun setAlwaysDecodeLongStripWithSSIV(enabled: Boolean) {
        _alwaysDecodeLongStripWithSSIV.value = enabled
        prefs.edit().putBoolean("reader_webtoon_always_ssiv", enabled).apply()
    }

    fun setContinuousVerticalTappingByPage(enabled: Boolean) {
        _continuousVerticalTappingByPage.value = enabled
        prefs.edit().putBoolean("reader_webtoon_tap_by_page", enabled).apply()
    }

    fun setReaderHideThreshold(threshold: ReaderHideThreshold) {
        _readerHideThreshold.value = threshold
        prefs.edit().putString("reader_hide_threshold", threshold.name).apply()
    }

    fun setDoubleTapAnimDuration(duration: Int) {
        val d = duration.coerceIn(100, 1000)
        _doubleTapAnimDuration.value = d
        prefs.edit().putInt("reader_double_tap_anim_duration", d).apply()
    }

    fun setShowReadingMode(show: Boolean) {
        _showReadingMode.value = show
        prefs.edit().putBoolean("reader_show_reading_mode", show).apply()
    }

    fun setCustomBrightness(enabled: Boolean) {
        _customBrightness.value = enabled
        prefs.edit().putBoolean("reader_custom_brightness", enabled).apply()
    }

    fun setCustomBrightnessValue(value: Int) {
        val v = value.coerceIn(-75, 100)
        _customBrightnessValue.value = v
        prefs.edit().putInt("reader_custom_brightness_value", v).apply()
    }

    fun setColorFilter(enabled: Boolean) {
        _colorFilter.value = enabled
        prefs.edit().putBoolean("reader_color_filter", enabled).apply()
    }

    fun setColorFilterValue(value: Int) {
        _colorFilterValue.value = value
        prefs.edit().putInt("reader_color_filter_value", value).apply()
    }

    fun setColorFilterMode(mode: Int) {
        val m = mode.coerceIn(0, 5)
        _colorFilterMode.value = m
        prefs.edit().putInt("reader_color_filter_mode", m).apply()
    }

    fun setGrayscale(enabled: Boolean) {
        _grayscale.value = enabled
        prefs.edit().putBoolean("reader_grayscale", enabled).apply()
    }

    fun setInvertedColors(enabled: Boolean) {
        _invertedColors.value = enabled
        prefs.edit().putBoolean("reader_inverted_colors", enabled).apply()
    }

    fun setSeriesOverrideEnabled(mangaId: String, enabled: Boolean) {
        _seriesOverrideEnabled.update { it + (mangaId to enabled) }
        prefs.edit().putBoolean("series_override_$mangaId", enabled).apply()
    }

    fun setSeriesReaderMode(mangaId: String, mode: ReaderMode) {
        _seriesReaderMode.update { it + (mangaId to mode) }
        prefs.edit().putString("series_mode_$mangaId", mode.name).apply()
    }

    /** The mode that should actually be used to render [mangaId]: its per-series override when
     *  enabled, otherwise the global reader mode. */
    fun resolvedReaderMode(mangaId: String): ReaderMode =
        if (_seriesOverrideEnabled.value[mangaId] == true) {
            _seriesReaderMode.value[mangaId] ?: _readerMode.value
        } else {
            _readerMode.value
        }

    fun setReaderBg(bg: ReaderBg) {
        _readerBg.value = bg
        prefs.edit().putString("reader_bg", bg.name).apply()
    }

    fun setReaderQuality(quality: Int) {
        val q = when (quality) {
            50 -> 50
            100 -> 100
            else -> 75
        }
        _readerQuality.value = q
        prefs.edit().putInt("reader_quality", q).apply()
    }

    fun setReaderFit(fit: ReaderFit) {
        _readerFit.value = fit
        prefs.edit().putString("reader_fit", fit.name).apply()
    }

    fun setShowPageNumber(show: Boolean) {
        _showPageNumber.value = show
        prefs.edit().putBoolean("show_page_number", show).apply()
    }

    fun setReaderOrientation(orientation: ReaderOrientation) {
        _readerOrientation.value = orientation
        prefs.edit().putString("reader_orientation", orientation.name).apply()
    }

    fun setKeepScreenOn(on: Boolean) {
        _keepScreenOn.value = on
        prefs.edit().putBoolean("reader_keep_screen_on", on).apply()
    }

    fun setWebtoonFade(fade: Boolean) {
        _webtoonFade.value = fade
        prefs.edit().putBoolean("reader_webtoon_fade", fade).apply()
    }

    fun setAutoScroll(auto: Boolean) {
        _autoScroll.value = auto
        prefs.edit().putBoolean("reader_auto_scroll", auto).apply()
    }

    fun setAutoScrollSpeedDp(speed: Float) {
        _autoScrollSpeedDp.value = speed
        prefs.edit().putFloat("reader_auto_scroll_speed", speed).apply()
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

    private var lastReposAutoRefreshAt = 0L

    /** Auto-refresh every added repo's catalog when the user opens the Repos/Extensions tab, so new
     *  extensions / extension updates show up without relaunching. Rate-limited to once per 2 minutes
     *  (the launch-time refresh in init already covers cold starts); failures are silent — the
     *  previous catalog stays in place. */
    fun refreshExtensionReposIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastReposAutoRefreshAt < 120_000L) return
        lastReposAutoRefreshAt = now
        viewModelScope.launch {
            repository.refreshAllRepos()
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

internal fun looksLikeCloudflare(e: Throwable): Boolean {
    var t: Throwable? = e
    while (t != null) {
        val msg = (t.message ?: "") + t.javaClass.simpleName
        if (msg.contains("cloudflare", ignoreCase = true) ||
            msg.contains("cf_clearance", ignoreCase = true) ||
            msg.contains("just a moment", ignoreCase = true)
        ) return true
        t = t.cause
    }
    return false
}
