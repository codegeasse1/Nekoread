package com.example.data.reader

import android.graphics.BitmapFactory
import com.example.data.source.MangaSource
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Downloads reader page images to on-device cache files so the webtoon reader's
 * [SubsamplingScaleImageView]-style view can decode only the visible region straight from disk
 * (the yomi/Tadami technique — never a giant full-height bitmap in memory).
 *
 * Downloads are single-flighted per image URL: the reader's preload loop and a page item can both
 * request the same page and only one network fetch happens; the other callers await the same
 * result. Existing cache files are reused without re-downloading.
 */
object WebtoonPageCache {

    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<File>>()
    private val dims = ConcurrentHashMap<String, Pair<Int, Int>>()

    // Downloads run in this shared scope so no single caller's cancellation (e.g. a page item
    // scrolling off-screen mid-download) can kill a fetch that other callers are awaiting.
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun keyFor(imageUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(imageUrl.toByteArray())
            .take(8)
            .joinToString("") { String.format("%02x", it) }
        return digest
    }

    fun targetFile(key: String, dir: File): File = File(dir, "$key.img")

    /**
     * Ensures the page's image bytes are on disk and returns the file. The download goes through
     * the source's own client + imageRequest headers (Referer/Origin etc.), exactly like Tadami's
     * HttpPageLoader, so hotlink-protected CDNs and signed URLs work.
     */
    suspend fun fileFor(
        desc: MangaSource.PageDescriptor,
        source: MangaSource,
        dir: File,
    ): File {
        val key = keyFor(desc.imageUrl)
        val target = targetFile(key, dir)
        if (target.exists() && target.length() > 0) return target

        val existing = inFlight[key]
        if (existing != null) return existing.await()

        val deferred = CompletableDeferred<File>()
        val winner = inFlight.putIfAbsent(key, deferred)
        if (winner != null) return winner.await()

        // We own the fetch. Launch it in the shared scope: the caller below only awaits the result,
        // so if the caller is cancelled the download still completes for the other waiters.
        downloadScope.launch {
            try {
                val downloaded = withContext(Dispatchers.IO) {
                    dir.mkdirs()
                    val tmp = File(dir, "$key.tmp")
                    tmp.delete()
                    source.downloadPageImage(desc, tmp)
                    tmp.renameTo(target)
                    target
                }
                deferred.complete(downloaded)
            } catch (e: Throwable) {
                // Never happens in practice (the scope's jobs are never cancelled), but don't
                // mislabel a cancellation as a network failure.
                deferred.completeExceptionally(
                    if (e is CancellationException) IOException("Download cancelled") else e,
                )
            } finally {
                inFlight.remove(key)
            }
        }
        return deferred.await()
    }

    /** Cheap intrinsic pixel size of a page's image file (bounds-only decode, cached). */
    suspend fun dimensions(desc: MangaSource.PageDescriptor, dir: File): Pair<Int, Int>? {
        val key = keyFor(desc.imageUrl)
        dims[key]?.let { return it }
        val f = targetFile(key, dir)
        if (!f.exists() || f.length() == 0L) return null
        return withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, opts)
            val d = if (opts.outWidth > 0 && opts.outHeight > 0) {
                Pair(opts.outWidth, opts.outHeight)
            } else {
                null
            }
            if (d != null) dims[key] = d
            d
        }
    }
}
