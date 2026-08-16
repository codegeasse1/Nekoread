package com.example.data.source

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.FetchResult
import coil.fetch.SourceResult
import coil.request.Options
import coil.key.Keyer
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Coil model for a reader page served by a Tachiyomi extension.
 *
 * The dedicated [Fetcher] loads the image through the extension's own client and its
 * [HttpSource.getImage] path â which builds the request via the source's `imageRequest(page)`
 * (carrying the source's Referer/Origin/custom headers) and runs it through the source's client
 * (including source-specific interceptors like Comix's Descrambler and 404-fallback). This is
 * exactly how Tadami/Mihon's reader loads online pages; loading the bare URL through a generic
 * client omits those headers, which is why hotlink-protected CDNs returned blank/black pages.
 */
data class ExtensionPageImage(
    val pageUrl: String,
    val imageUrl: String,
    val source: HttpSource,
)


/**
 * Coil model for a catalog/library cover served by a Tachiyomi extension.
 *
 * The dedicated [Fetcher] loads the cover through the extension's OWN client and headers
 * ([HttpSource.headers] = the source's User-Agent + Referer/Origin etc.) so hotlink-protected
 * CDNs accept it â the same reason reader pages go through [ExtensionPageImage]. Loading the bare
 * URL through the generic client omitted the source's Referer, which is why covers on sources like
 * 18 Porn Comic / TheBlank came back as blank gray tiles even after their API worked.
 */
data class ExtensionCoverImage(
    val imageUrl: String,
    val source: HttpSource,
)

/** Stable memory-cache key for covers (source + url), so a cover loads once and is reused. */
class ExtensionCoverImageKeyer : Keyer<ExtensionCoverImage> {
    override fun key(data: ExtensionCoverImage, options: Options): String =
        "cover:" + data.source.toString() + "|" + data.imageUrl
}

class ExtensionCoverImageFetcherFactory : Fetcher.Factory<ExtensionCoverImage> {

    // Covers must never hang for minutes behind a burst of reader page requests on the same host
    // (per-host request cap + long call timeouts on the shared client) â a stuck cover just shows
    // a blank tile until restart. Use a per-source clone with short timeouts so a slow/failing
    // cover fails fast, lets Coil show the placeholder, and retries cleanly on next composition.
    private val coverClients = ConcurrentHashMap<HttpSource, OkHttpClient>()

    private fun coverClient(source: HttpSource): OkHttpClient =
        coverClients.getOrPut(source) {
            source.client.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .build()
        }

    override fun create(
        data: ExtensionCoverImage,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher? {
        return object : Fetcher {
            override suspend fun fetch(): FetchResult? {
                val request = okhttp3.Request.Builder()
                    .url(data.imageUrl)
                    .headers(data.source.headers)
                    .build()
                val response = try {
                    coverClient(data.source).newCall(request).execute()
                } catch (e: Exception) {
                    throw IOException("${data.source.name} cover load failed (${data.imageUrl.take(80)}): ${e.message}", e)
                }
                if (!response.isSuccessful) {
                    response.close()
                    throw IOException("HTTP ${response.code} for ${data.imageUrl.take(80)}")
                }
                val body = response.body ?: throw IOException("Null cover body")
                return SourceResult(
                    source = ImageSource(
                        source = body.source(),
                        context = options.context,
                    ),
                    mimeType = body.contentType()?.toString() ?: "image/*",
                    dataSource = DataSource.NETWORK,
                )
            }
        }
    }
}

