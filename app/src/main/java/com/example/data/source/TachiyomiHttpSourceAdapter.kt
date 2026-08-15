package com.example.data.source

import android.util.Base64
import com.example.data.extension.ExtensionDexLoader
import com.example.data.local.ChapterEntity
import com.example.data.local.MangaEntity
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException

/**
 * Bridges a DexClassLoader-loaded Tachiyomi [HttpSource] (from an installed extension APK) onto
 * Nekoread's [MangaSource] interface, exactly like Tadami/Mihon run their extensions.
 *
 * Manga ids look like "<extKey>:<base64url(mangaUrl)>"; chapter ids like "<mangaId>:ch:<base64url(chapterUrl)>".
 */
class TachiyomiHttpSourceAdapter(
    private val ext: HttpSource,
    val packageName: String
) : MangaSource {

    override val id: String = ExtensionDexLoader.key(packageName, ext.id.toString())
    override val name: String = ext.name
    override val baseUrl: String = ext.baseUrl
    override val lang: String = ext.lang
    override val sourceType: String = "MANGA"

    /**
     * The exact User-Agent the extension's requests will send: the extension's own UA if it sets
     * one, otherwise the app default (which NetworkHelper injects). Mirrored by the
     * Cloudflare-verification WebView so cf_clearance binds to the right UA.
     */
    override val userAgent: String
        get() {
            val extUa = runCatching { ext.headers["User-Agent"] }.getOrNull()
            if (!extUa.isNullOrBlank()) return extUa
            return runCatching {
                eu.kanade.tachiyomi.network.NetworkHelper.getInstance().defaultUserAgentProvider()
            }.getOrNull() ?: ""
        }

    override fun toString(): String = name

    private fun b64(raw: String): String =
        Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)

    private fun unb64(encoded: String): String =
        String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)

    private fun mangaUrl(fullMangaId: String): String = unb64(fullMangaId.substringAfter(":"))

    private fun sm(url: String): SManga = SManga.create().apply { this.url = url }

    private fun ch(url: String): SChapter = SChapter.create().apply { this.url = url }

    private fun SManga.toManga(): MangaEntity = MangaEntity(
        id = idOf(url),
        title = title.ifBlank { name },
        coverUrl = thumbnail_url ?: "",
        author = author ?: "Unknown",
        artist = artist ?: "Unknown",
        description = description ?: "",
        sourceId = this@TachiyomiHttpSourceAdapter.id,
        sourceName = name,
        status = when (status) {
            SManga.COMPLETED -> "COMPLETED"
            SManga.ON_HIATUS -> "HIATUS"
            SManga.CANCELLED -> "CANCELLED"
            else -> "ONGOING"
        },
        type = "MANGA",
        genres = genre ?: ""
    )

    private fun SChapter.toChapter(mangaId: String): ChapterEntity = ChapterEntity(
        id = "$mangaId:ch:" + b64(url),
        mangaId = mangaId,
        chapterNumber = chapter_number,
        name = name,
        scanlator = scanlator ?: "Unknown",
        releaseDate = "",
        dateUpload = date_upload,
        fetchUrl = url
    )

    private fun idOf(mangaUrl: String): String = "${this.id}:" + b64(mangaUrl)

    // Nekoread drives extensions through the SUSPEND API (getLatestUpdates/getPopularManga/
    // getSearchManga/getMangaDetails/getChapterList/getPageList/getImageUrl) — exactly what
    // Tadami/Mihon call. The old Rx fetch* methods are still provided by the vendored HttpSource
    // for lib-1.4 sources (their defaults run request+parse), but keiyoushi lib-1.6 (KeiSource)
    // sources force those Rx methods to throw, so calling fetch* directly broke sources like 4KHD.
    private suspend fun <T> loading(tag: String, block: suspend () -> T): T =
        withTimeout(120_000) { block() }

    override suspend fun search(query: String, page: Int): List<MangaEntity> = withContext(Dispatchers.IO) {
        loading("search") { ext.getSearchManga(page, query, FilterList()) }
            .mangas
            .map { it.toManga() }
    }

    override suspend fun latest(page: Int): List<MangaEntity> = withContext(Dispatchers.IO) {
        loading("latest") { ext.getLatestUpdates(page) }
            .mangas
            .map { it.toManga() }
    }

    override suspend fun getDetails(fullMangaId: String): MangaEntity = withContext(Dispatchers.IO) {
        loading("details") { ext.getMangaDetails(sm(mangaUrl(fullMangaId))) }.toManga()
    }

    override suspend fun getChapters(fullMangaId: String): List<ChapterEntity> = withContext(Dispatchers.IO) {
        val chapters = loading("chapters") { ext.getChapterList(sm(mangaUrl(fullMangaId))) }
            .mapIndexed { i, ch -> ch.toChapter(fullMangaId) }
        // Sources that don't set chapter_number leave the SChapter default (-1) on every chapter,
        // which shows "-1" all over the UI and breaks chapter ordering, prev/next navigation and
        // continuous scroll. Extensions return chapters NEWEST FIRST (Mihon convention) — position
        // 0 is the LAST chapter — so number them in REVERSE to get 1,2,3… in true reading order.
        // (Numbering in the returned order inverted every number: the oldest chapter became N and
        // the newest became 1, so the list, "Read", prev/next and continuous scroll all pointed the
        // wrong way — e.g. TheBlank's "Chapter 1" opened showing "Ch. 109".)
        if (chapters.isNotEmpty() && chapters.all { it.chapterNumber <= 0f }) {
            val readingOrder = chapters.asReversed()
            readingOrder.mapIndexed { i, ch ->
                if (ch.chapterNumber > 0f) ch else ch.copy(chapterNumber = (i + 1).toFloat())
            }
        } else {
            chapters
        }
    }

    override suspend fun getPageUrls(rawChapterId: String): List<String> = withContext(Dispatchers.IO) {
        val pages = loading("pages") { ext.getPageList(ch(rawChapterId)) }
        pages.map { page ->
            page.imageUrl ?: ext.getImageUrl(page)
        }
    }

    /**
     * Coil models that load each page through the extension's own client + `imageRequest(page)`
     * headers (Referer/Origin/etc.) — the exact path Tadami's reader uses. Without this, pages
     * were fetched as bare URLs via Coil's generic client, so hotlink-protected CDNs rejected
     * them (blank/black pages).
     */
    override suspend fun getPageImageModels(rawChapterId: String): List<Any> = withContext(Dispatchers.IO) {
        val pages = loading("pages") { ext.getPageList(ch(rawChapterId)) }
        pages.map { page ->
            val url = page.imageUrl ?: ext.getImageUrl(page)
            page.imageUrl = url
            ExtensionPageImage(page.url, url, ext)
        }
    }

    override suspend fun getPageDescriptors(rawChapterId: String): List<MangaSource.PageDescriptor> =
        withContext(Dispatchers.IO) {
            loading("pages") { ext.getPageList(ch(rawChapterId)) }
                .map { page ->
                    val url = page.imageUrl ?: ext.getImageUrl(page)
                    page.imageUrl = url
                    MangaSource.PageDescriptor(page.url, url)
                }
        }

    /** Downloads one page through the extension's own getImage() (exact Tadami path: the source's
     *  imageRequest headers + client), so hotlink-protected CDNs and signed URLs work. */
    override suspend fun downloadPageImage(
        page: MangaSource.PageDescriptor,
        target: File,
    ): File = withContext(Dispatchers.IO) {
        val spage = Page(0, url = page.pageUrl, imageUrl = page.imageUrl)
        val response = ext.getImage(spage)
        val body = response.body ?: throw IOException("Empty image body for ${page.imageUrl.take(80)}")
        try {
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for ${page.imageUrl.take(80)}")
            }
            target.parentFile?.mkdirs()
            body.byteStream().use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
        } finally {
            body.close()
        }
        target
    }

    /** Covers go through the extension's own client + headers, so hotlink-protected CDNs accept them. */
    override fun coverImageModel(coverUrl: String): Any = ExtensionCoverImage(coverUrl, ext)

    /**
     * Canonical web page URL for the manga, built by the extension's own URL scheme via
     * [HttpSource.getMangaUrl]. The app's naive "baseUrl + '/' + url" join 404s on sources like
     * TheBlank, whose stored manga url is a bare slug ("a-naughty") whose real page is
     * "https://theblank.net/serie/a-naughty".
     *
     * getMangaUrl can fail while the Cloudflare challenge it is meant to solve is still up (its
     * token bootstrap hits the challenged homepage), so bound the wait and fall back to the source
     * homepage — a real page that reliably triggers the challenge, which is all the verification
     * WebView needs to issue a host-wide cf_clearance.
     */
    override suspend fun getMangaWebUrl(fullMangaId: String): String {
        val stored = runCatching { mangaUrl(fullMangaId) }.getOrNull() ?: ""
        if (stored.isNotBlank()) {
            val canonical = runCatching {
                withTimeout(8_000) { ext.getMangaUrl(sm(stored)) }
            }.getOrNull()
            if (!canonical.isNullOrBlank()) {
                val trimmed = canonical.trimEnd('/')
                if (trimmed.isNotBlank() && trimmed != ext.baseUrl.trimEnd('/')) {
                    return canonical
                }
            }
        }
        return ext.baseUrl
    }
}
