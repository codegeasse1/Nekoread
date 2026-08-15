package com.example.data.source

import android.util.Base64
import com.example.data.extension.ExtensionDexLoader
import com.example.data.local.ChapterEntity
import com.example.data.local.MangaEntity
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    override suspend fun search(query: String, page: Int): List<MangaEntity> = withContext(Dispatchers.IO) {
        ext.fetchSearchManga(page, query, FilterList())
            .toBlocking()
            .first()
            .mangas
            .map { it.toManga() }
    }

    override suspend fun latest(page: Int): List<MangaEntity> = withContext(Dispatchers.IO) {
        ext.fetchLatestUpdates(page)
            .toBlocking()
            .first()
            .mangas
            .map { it.toManga() }
    }

    override suspend fun getDetails(fullMangaId: String): MangaEntity = withContext(Dispatchers.IO) {
        ext.fetchMangaDetails(sm(mangaUrl(fullMangaId))).toBlocking().first().toManga()
    }

    override suspend fun getChapters(fullMangaId: String): List<ChapterEntity> = withContext(Dispatchers.IO) {
        ext.fetchChapterList(sm(mangaUrl(fullMangaId)))
            .toBlocking()
            .first()
            .map { it.toChapter(fullMangaId) }
    }

    override suspend fun getPageUrls(rawChapterId: String): List<String> = withContext(Dispatchers.IO) {
        val pages = ext.fetchPageList(ch(rawChapterId)).toBlocking().first()
        pages.map { page ->
            page.imageUrl ?: ext.fetchImageUrl(page).toBlocking().first()
        }
    }

    /**
     * Coil models that load each page through the extension's own client + `imageRequest(page)`
     * headers (Referer/Origin/etc.) — the exact path Tadami's reader uses. Without this, pages
     * were fetched as bare URLs via Coil's generic client, so hotlink-protected CDNs rejected
     * them (blank/black pages).
     */
    override suspend fun getPageImageModels(rawChapterId: String): List<Any> = withContext(Dispatchers.IO) {
        val pages = ext.fetchPageList(ch(rawChapterId)).toBlocking().first()
        pages.map { page ->
            val url = page.imageUrl ?: ext.fetchImageUrl(page).toBlocking().first()
            page.imageUrl = url
            ExtensionPageImage(url, ext)
        }
    }
}
