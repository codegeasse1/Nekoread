package com.example.data.source

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.FetchResult
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.IOException

/**
 * Coil model for a reader page served by a Tachiyomi extension.
 *
 * The dedicated [Fetcher] loads the image through the extension's own client and its
 * [HttpSource.getImage] path — which builds the request via the source's `imageRequest(page)`
 * (carrying the source's Referer/Origin/custom headers) and runs it through the source's client
 * (including source-specific interceptors like Comix's Descrambler and 404-fallback). This is
 * exactly how Tadami/Mihon's reader loads online pages; loading the bare URL through a generic
 * client omits those headers, which is why hotlink-protected CDNs returned blank/black pages.
 */
data class ExtensionPageImage(
    val pageUrl: String,
    val source: HttpSource,
)

/** Memory-cache pages by URL so scrolling the reader doesn't re-fetch them. */
class ExtensionPageImageKeyer : Keyer<ExtensionPageImage> {
    override fun key(data: ExtensionPageImage, options: Options): String = data.pageUrl
}

class ExtensionPageImageFetcherFactory : Fetcher.Factory<ExtensionPageImage> {
    override fun create(
        data: ExtensionPageImage,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher? {
        return object : Fetcher {
            override suspend fun fetch(): FetchResult? {
                val page = Page(0, imageUrl = data.pageUrl)
                val response = data.source.getImage(page)
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
