package com.example.data.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.Fetcher
import coil.fetch.FetchResult
import coil.key.Keyer
import coil.request.Options
import coil.size.Dimension
import com.example.data.source.MangaSource
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

/**
 * One vertical slice of a tall webtoon strip, decoded from the source file on disk.
 *
 * Rendering a single bitmap taller than the GPU's max texture size (4096/8192px) forces a slow
 * software-draw path on the RenderThread — the cause of the stutter at every slice boundary on
 * 100-slice chapters. Each strip is therefore split into slices whose on-screen height stays
 * under [TILE_MAX_DISPLAY_PX]: every slice is a normal texture-sized bitmap, region-decoded
 * straight from the cached file (the giant bitmap is never materialised) and copied to a
 * HARDWARE bitmap on the decode thread, so drawing it costs nothing on the draw path.
 */
data class WebtoonTile(
    val imageUrl: String,
    val requestUrl: String,
    val source: MangaSource,
    val sliceIndex: Int,
    val sliceCount: Int,
)

/** Stable memory-cache key per tile, so scrolling back to a slice is a cache hit. */
class WebtoonTileKeyer : Keyer<WebtoonTile> {
    override fun key(data: WebtoonTile, options: Options): String =
        "${data.imageUrl}#${data.sliceIndex}/${data.sliceCount}"
}

/**
 * Display height (px) above which a strip is sliced, and the target max height of each slice.
 * Kept at a multiple of typical GPU max-texture bounds so every slice is a normal hardware draw.
 */
const val TILE_MAX_DISPLAY_PX = 2048

/** How many on-screen slices a strip with the given aspect ratio (w/h) needs at this width. */
fun webtoonSliceCount(displayWidthPx: Int, aspectRatio: Float): Int {
    if (displayWidthPx <= 0 || aspectRatio <= 0f) return 1
    val displayHeightPx = displayWidthPx.toDouble() / aspectRatio
    return ((displayHeightPx + TILE_MAX_DISPLAY_PX - 1) / TILE_MAX_DISPLAY_PX)
        .toInt().coerceAtLeast(1)
}

class WebtoonTileFetcherFactory(
    private val cacheDir: File,
) : Fetcher.Factory<WebtoonTile> {

    override fun create(
        data: WebtoonTile,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher? {
        return object : Fetcher {
            override suspend fun fetch(): FetchResult? {
                val file = WebtoonFileCache.resolve(data, cacheDir) ?: return null
                val bitmap = decodeSlice(file, data, options) ?: return null
                return DrawableResult(
                    drawable = BitmapDrawable(options.context.resources, bitmap),
                    isSampled = true,
                    dataSource = DataSource.DISK,
                )
            }
        }
    }
}

/** URL -> File cache for tile sources. Files are keyed by a hash of the image URL so the same
 *  image is downloaded once and every slice region-decodes from it. */
object WebtoonFileCache {
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<File?>>()

    /** The on-disk file for this tile's image, downloading it through the source's own client +
     *  headers on first request. Single-flight per URL so concurrent slices of the same page
     *  never start parallel downloads. */
    suspend fun resolve(tile: WebtoonTile, cacheDir: File): File? {
        val target = File(cacheDir, keyFor(tile.imageUrl))
        if (target.isFile && target.length() > 0L) return target
        val existing = inflight[tile.imageUrl]
        if (existing != null) return existing.await()
        val future = CompletableDeferred<File?>()
        val prev = inflight.putIfAbsent(tile.imageUrl, future)
        if (prev != null) return prev.await()
        try {
            val ok = runCatching {
                target.parentFile?.mkdirs()
                tile.source.downloadPageImage(
                    MangaSource.PageDescriptor(tile.requestUrl, tile.imageUrl),
                    target,
                )
                target.isFile && target.length() > 0L
            }.getOrDefault(false)
            future.complete(if (ok) target else null)
        } finally {
            inflight.remove(tile.imageUrl)
        }
        return future.await()
    }

    private fun keyFor(url: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(url.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/** Region-decodes [tile] out of [file] at the request's target width and returns a bitmap that is
 *  hardware-backed when the device supports it (so the GPU upload happens off the draw path). */
private fun decodeSlice(
    file: File,
    tile: WebtoonTile,
    options: Options,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val srcW = bounds.outWidth
    val srcH = bounds.outHeight
    if (srcW <= 0 || srcH <= 0) return null

    val count = tile.sliceCount.coerceAtLeast(1)
    val index = tile.sliceIndex.coerceIn(0, count - 1)
    var top = (srcH.toLong() * index / count).toInt()
    var bottom = (srcH.toLong() * (index + 1) / count).toInt().coerceAtMost(srcH)
    if (bottom <= top) return null
    // 1px source overlap between neighbours hides hairline seams at slice edges.
    if (index < count - 1) bottom = (bottom + 1).coerceAtMost(srcH)

    val targetW = (options.size.width as? Dimension.Pixels)?.px ?: srcW
    val sample = if (targetW <= 0 || srcW <= targetW) 1 else {
        var s = 1
        while (srcW / (s * 2) >= targetW) s *= 2
        s
    }

    val decoder = runCatching {
        BitmapRegionDecoder.newInstance(file.absolutePath)
    }.getOrNull() ?: return null
    try {
        val region = decoder.decodeRegion(
            Rect(0, top, srcW, bottom),
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        val hardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { region.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
        } else null
        if (hardware != null) region.recycle()
        return hardware ?: region
    } catch (_: Throwable) {
        return null
    } finally {
        decoder.recycle()
    }
}
