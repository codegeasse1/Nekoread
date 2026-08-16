package com.example.data.source

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.FetchResult
import coil.fetch.SourceResult
import coil.request.Options
import coil.key.Keyer
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Coil model for a reader page served by a Tachiyomi extension.
 *
 * The dedicated [Fetcher] loads the image through the extension's own client and its
 * [HttpSource.getImage] path Ã¢ÂÂ which builds the request via the source's `imageRequest(page)`
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


/** Memory-cache pages by their unique image URL so scrolling the reader doesn't re-fetch them. */
class ExtensionPageImageKeyer : Keyer<ExtensionPageImage> {
    override fun key(data: ExtensionPageImage, options: Options): String = data.imageUrl
}

/**
 * Network fetcher for reader page models: loads the image through the extension's OWN client and
 * its [HttpSource.getImage] path (carrying the source's Referer/Origin/custom headers), so
 * hotlink-protected CDNs accept it. This is the same client/headers the extension itself uses.
 * No on-device page cache: pages always fetch live from the source.
 */
class ExtensionPageImageFetcherFactory : Fetcher.Factory<ExtensionPageImage> {
    override fun create(
        data: ExtensionPageImage,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher? {
        return object : Fetcher {
            override suspend fun fetch(): FetchResult? {
                val page = Page(0, url = data.pageUrl, imageUrl = data.imageUrl)
                val response = try {
                    data.source.getImage(page)
                } catch (e: Exception) {
                    throw IOException("${data.source.name} page load failed (${data.imageUrl.take(80)}): ${e.message}", e)
                }
                val body = response.body ?: throw IOException("Null response body")
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

/** Coil model for a catalog/library cover served by a Tachiyomi extension.
 *
 * The dedicated [Fetcher] loads the cover through the extension's OWN client and headers
 * ([HttpSource.headers] = the source's User-Agent + Referer/Origin etc.) so hotlink-protected
 * CDNs accept it Ã¢ÂÂ the same reason reader pages go through [ExtensionPageImage]. Loading the bare
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
    // (per-host request cap + long call timeouts on the shared client) Ã¢ÂÂ a stuck cover just shows
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
                // Some extensions return relative cover paths (e.g. "/uploads/x.jpg"); resolve them
                // against the source's own base URL or the request will fail outright.
                val coverUrl = if (data.imageUrl.startsWith("http")) data.imageUrl
                    else data.source.baseUrl.trimEnd('/') + "/" + data.imageUrl.trimStart('/')
                // Referer fallback: many CDNs only serve hotlinked images when the request carries a
                // Referer. HttpSource.headers usually includes one, but when it doesn't, use the
                // source's own homepage so the CDN sees a legit referrer.
                val headers = data.source.headers.newBuilder()
                    .apply {
                        if (get("Referer").isNullOrBlank() && data.source.baseUrl.isNotBlank()) {
                            set("Referer", data.source.baseUrl)
                        }
                    }
                    .build()
                val request = okhttp3.Request.Builder()
                    .url(coverUrl)
                    .headers(headers)
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

