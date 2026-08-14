package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Real implementation of the Tachiyomi/Mihon [HttpSource] contract. Loaded extension APKs
 * subclass this class; the request/parse methods come from the extension while the network
 * plumbing lives here (exactly like Tadami/Mihon).
 */
@Suppress("unused", "unused_parameter", "MemberVisibilityCanBePrivate")
abstract class HttpSource : CatalogueSource {

    protected val network: NetworkHelper = NetworkHelper.getInstance()

    abstract val baseUrl: String

    open val versionId: Int = 1

    override val id: Long by lazy { generateId(name, lang, versionId) }

    /**
     * Generates a unique ID for the source: first 8 bytes (64 bits) of the MD5 of
     * `"${name.lowercase()}/$lang/$versionId"`, sign bit cleared. Mirrors the real source-api.
     */
    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val md5 = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return ByteBuffer.wrap(md5, 0, 8).long and Long.MAX_VALUE
    }

    val headers: Headers by lazy { headersBuilder().build() }

    open val client: OkHttpClient by lazy { getNetworkClient() }

    open protected fun getNetworkClient(): OkHttpClient = network.client

    open protected fun headersBuilder(): Headers.Builder = Headers.Builder().apply {
        add("User-Agent", "Nekoread/" + AppInfo.getVersionName())
    }

    override fun toString(): String = name

    override fun fetchPopularManga(page: Int): Observable<MangasPage> =
        client.newCall(popularMangaRequest(page)).asObservableSuccess().map(::popularMangaParse)

    abstract protected fun popularMangaRequest(page: Int): Request

    abstract protected fun popularMangaParse(response: Response): MangasPage

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        client.newCall(searchMangaRequest(page, query, filters)).asObservableSuccess().map(::searchMangaParse)

    abstract protected fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request

    abstract protected fun searchMangaParse(response: Response): MangasPage

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> =
        client.newCall(latestUpdatesRequest(page)).asObservableSuccess().map(::latestUpdatesParse)

    abstract protected fun latestUpdatesRequest(page: Int): Request

    abstract protected fun latestUpdatesParse(response: Response): MangasPage

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(mangaDetailsRequest(manga)).asObservableSuccess().map { response ->
            mangaDetailsParse(response).apply { initialized = true }
        }

    open fun mangaDetailsRequest(manga: SManga): Request =
        GET(getMangaUrl(manga), headers)

    abstract protected fun mangaDetailsParse(response: Response): SManga

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        client.newCall(chapterListRequest(manga)).asObservableSuccess().map(::chapterListParse)

    open protected fun chapterListRequest(manga: SManga): Request =
        GET(getMangaUrl(manga), headers)

    abstract protected fun chapterListParse(response: Response): List<SChapter>

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        client.newCall(pageListRequest(chapter)).asObservableSuccess().map(::pageListParse)

    open protected fun pageListRequest(chapter: SChapter): Request =
        GET(getChapterUrl(chapter), headers)

    abstract protected fun pageListParse(response: Response): List<Page>

    open fun fetchImageUrl(page: Page): Observable<String> =
        client.newCall(imageUrlRequest(page)).asObservableSuccess().map(::imageUrlParse)

    open protected fun imageUrlRequest(page: Page): Request = GET(page.url, headers)

    abstract protected fun imageUrlParse(response: Response): String

    fun fetchImage(page: Page): Observable<Response> =
        client.newCall(imageRequest(page)).asObservableSuccess()

    open protected fun imageRequest(page: Page): Request = GET(page.imageUrl ?: page.url, headers)

    fun SChapter.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    fun SManga.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    private fun getUrlWithoutDomain(orig: String): String {
        val url = if (orig.startsWith("http")) orig else "http://$orig"
        return try {
            val uri = URI(url)
            if (uri.host == null) orig else url.replace(uri.scheme + "://" + uri.host, "")
        } catch (e: URISyntaxException) {
            orig
        }
    }

    override val supportsRelatedMangas: Boolean get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        throw UnsupportedOperationException("Related mangas are not supported")
    }

    open protected fun relatedMangaListRequest(manga: SManga): Request {
        throw UnsupportedOperationException("Related mangas are not supported")
    }

    open protected fun relatedMangaListParse(response: Response): List<SManga> =
        throw UnsupportedOperationException("Related mangas are not supported")

    open fun getMangaUrl(manga: SManga): String =
        if (manga.url.startsWith("http")) manga.url else baseUrl + manga.url

    open fun getChapterUrl(chapter: SChapter): String =
        if (chapter.url.startsWith("http")) chapter.url else baseUrl + chapter.url

    open fun prepareNewChapter(chapter: SChapter, manga: SManga) {}

    override fun getFilterList(): FilterList = FilterList()
}
