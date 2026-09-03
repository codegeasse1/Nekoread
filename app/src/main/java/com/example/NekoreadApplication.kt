package com.example

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import io.aatricks.easyreader.data.local.PreferencesManager
import io.aatricks.easyreader.data.repository.ContentRepository
import io.aatricks.easyreader.data.repository.ImageDimensionCacheRepository
import io.aatricks.easyreader.data.repository.LibraryRepository
import io.aatricks.easyreader.data.repository.ReadingHistorySeeder
import io.aatricks.easyreader.data.repository.content.EpubImageFetcher
import io.aatricks.easyreader.data.repository.content.HttpMediaCacheFetcher
import io.aatricks.easyreader.data.repository.content.ReaderImageTileFetcher
import io.aatricks.easyreader.work.ChapterDownloadQueue
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Nekoread's Hilt application. It also boots Emaki's (EasyReader's) reader engine:
 *  - provides the coil3 image loader with Emaki's own fetchers (tiled reader slices, media cache,
 *    epub images) so Emaki's reader renders exactly as it does in the Emaki app, and
 *  - provides the WorkManager [Configuration] via Emaki's HiltWorkerFactory.
 */
@HiltAndroidApp
class NekoreadApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var contentRepository: ContentRepository
    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject lateinit var chapterDownloadQueue: ChapterDownloadQueue
    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var imageDimensionCache: ImageDimensionCacheRepository
    @Inject lateinit var readingHistorySeeder: ReadingHistorySeeder

    // WorkManager pulls this lazily before its first enqueue, which happens after Hilt
    // injection has populated `workerFactory`. Using on-demand initialization (no manual
    // `WorkManager.initialize`) means the test variant can override via WorkManagerTestInitHelper.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    private val warmupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        prewarmLastReadChapter()
        pruneImageDimensionCache()
        pruneChapterDownloadQueue()
        seedReadingHistory()
    }

    private fun seedReadingHistory() {
        warmupScope.launch {
            runCatching { readingHistorySeeder.seedIfNeeded() }
                .onFailure { Log.w(TAG, "reading history seed failed message=${it.message}") }
        }
    }

    private fun pruneChapterDownloadQueue() {
        warmupScope.launch {
            runCatching { chapterDownloadQueue.prune() }
                .onFailure { Log.w(TAG, "chapter download queue prune failed message=${it.message}") }
        }
    }

    private fun pruneImageDimensionCache() {
        warmupScope.launch {
            runCatching { imageDimensionCache.prune() }
                .onFailure { Log.w(TAG, "image dim cache prune failed message=${it.message}") }
        }
    }

    // Kick off chapter parse on a background coroutine so it overlaps Hilt graph build,
    // Compose first frame, and ViewModel init. Populates ParsedContentCache + in-memory memo,
    // so by the time ReaderViewModel.loadContent runs the fast path is already primed.
    // Fire-and-forget; failures are swallowed because pre-warm is best-effort.
    private fun prewarmLastReadChapter() {
        val url = preferencesManager.lastReadUrl?.takeIf { it.isNotBlank() } ?: return
        warmupScope.launch {
            runCatching { contentRepository.loadContent(url) }
                .onFailure { Log.w(TAG, "prewarm failed url=$url message=${it.message}") }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache { buildMemoryCache(context) }
            .components {
                add(ReaderImageTileFetcher.Factory(contentRepository))
                add(EpubImageFetcher.Factory(contentRepository))
                add(HttpMediaCacheFetcher.Factory(contentRepository))
                // Fallback only: HttpMediaCacheFetcher owns the disk-cached HTTP path and
                // matches every http(s) URL. OkHttp's fetcher runs only if that one returns
                // null (offline + cache miss, or a Factory.create bug). Keep it so a
                // regression in our fetcher doesn't render images unfetchable.
                add(OkHttpNetworkFetcherFactory(okHttpClient))
            }
            .crossfade(false)
            .build()
    }

    private fun buildMemoryCache(context: PlatformContext): MemoryCache {
        // Manhwa strips are region-decoded into HARDWARE-config slices (~screenWidth x 2048 x 4B
        // ≈ 9MB each). At 0.25 the strong LRU (~32-64MB) holds only ~4-7 slices — well under one
        // chapter, so a fast up/down drag or scrolling back re-decodes evicted slices every time.
        // HARDWARE pixels live in graphics memory (AHardwareBuffer), not the Java heap, so a larger
        // budget does not raise heap-OOM risk; 0.5 comfortably holds a full oscillation window.
        return MemoryCache.Builder()
            .maxSizePercent(context, MEMORY_CACHE_HEAP_FRACTION)
            .build()
    }

    companion object {
        private const val TAG = "NekoreadApplication"

        // Fraction of the app's available memory the Coil image cache may use. Manhwa strips are
        // decoded into HARDWARE-config slices (~9MB each, in graphics memory not the Java heap), so
        // a larger budget lets a full up/down oscillation window and >1 chapter's slices stay
        // resident instead of being evicted and re-decoded on scroll-back. Dial back toward 0.25 if
        // low-memory devices hit onTrimMemory / AHardwareBuffer-fd pressure.
        private const val MEMORY_CACHE_HEAP_FRACTION = 0.5
    }
}
