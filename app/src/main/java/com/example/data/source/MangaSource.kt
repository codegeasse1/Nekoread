package com.example.data.source

import android.util.Base64
import com.example.data.extension.ExtensionDexLoader
import com.example.data.local.ChapterEntity
import com.example.data.local.MangaEntity
import java.io.File

/**
 * A "source" is the real equivalent of a Tachiyomi/Mihon extension.
 * Each implementation knows how to talk to one content provider and returns
 * plain [MangaEntity] / [ChapterEntity] objects that are ready to be stored in Room.
 *
 * Conventions:
 *  - manga ids use the format "<sourceId>:<sourceIdRaw>" (e.g. "mangadex:ade0306c-...")
 *  - chapter ids use the format "<sourceId>:ch:<chapterIdRaw>"
 *  - [getPageUrls] receives the RAW chapter id stored in [ChapterEntity.fetchUrl]
 */
interface MangaSource {
    val id: String
    val name: String
    val baseUrl: String
    val lang: String
    val sourceType: String

    /** The User-Agent the source's HTTP requests actually send; used to mirror it in the
     *  Cloudflare-verification WebView so a solved cf_clearance binds to the right UA. */
    val userAgent: String
        get() = ""

    suspend fun search(query: String, page: Int): List<MangaEntity>
    suspend fun latest(page: Int): List<MangaEntity>

    /** "Popular" browse list (Tadami's Popular tab). Default: same as [latest] for sources
     *  without a dedicated popular endpoint. */
    suspend fun popular(page: Int): List<MangaEntity> = latest(page)
    suspend fun getDetails(fullMangaId: String): MangaEntity
    suspend fun getChapters(fullMangaId: String): List<ChapterEntity>
    suspend fun getPageUrls(rawChapterId: String): List<String>

    /**
     * Search every manga in this source carrying the given tag/genre (e.g. "Action", "Adventure").
     * Default: fall back to a plain keyword search so sources without tag support still return
     * something. Extension sources map the tag onto their genre filter list; MangaDex uses the
     * API's includedTags[].
     */
    suspend fun searchByTag(tag: String, page: Int): List<MangaEntity> = search(tag, page)

    /**
     * Coil image models for a chapter's pages. Default: the plain URLs. Extension sources override
     * this to return source-aware models loaded through the extension's own client + headers.
     */
    suspend fun getPageImageModels(rawChapterId: String): List<Any> = getPageUrls(rawChapterId)

    /**
     * Page descriptors (source request URL + final image URL) for a chapter, in order. This is the
     * Tadami-style reader input: each page is downloaded through the source's own client
     * ([downloadPageImage]) rather than decoded into a giant full-image bitmap by Coil — the reader
     * renders them with a tiled (SubsamplingScaleImageView) view, so visible regions decode at full
     * resolution with bounded memory. Default: the plain URLs (request URL empty).
     */
    data class PageDescriptor(val pageUrl: String, val imageUrl: String)

    suspend fun getPageDescriptors(rawChapterId: String): List<PageDescriptor> =
        getPageUrls(rawChapterId).map { PageDescriptor("", it) }

    /**
     * Download one page's image bytes into [target] (overwriting it), using the source's own HTTP
     * client + imageRequest headers (Referer/Origin etc.), exactly like Tadami's HttpPageLoader.
     * Extension sources override this to route through the extension's getImage().
     */
    suspend fun downloadPageImage(page: PageDescriptor, target: File): File =
        throw UnsupportedOperationException("downloadPageImage not implemented for $name")

    /**
     * Coil image model for a manga cover. Default: the plain URL. Extension sources override this
     * to return [ExtensionCoverImage] so covers are fetched through the extension's client +
     * headers (Referer/Origin), fixing blank cover tiles on hotlink-protected CDNs.
     */
    fun coverImageModel(coverUrl: String): Any = coverUrl

    /**
     * Canonical web URL for a manga's page, used by the Cloudflare/site-verification WebView.
     * Extension sources build it through their own URL scheme (e.g. TheBlank: "baseUrl/serie/{slug}");
     * the default joins the stored URL onto [baseUrl]. May resolve via a network request, so call
     * it off the main thread.
     */
    suspend fun getMangaWebUrl(fullMangaId: String): String = runCatching {
        val raw = fullMangaId.substringAfter(":")
        val decoded = String(Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
            decoded
        } else {
            baseUrl.trimEnd('/') + "/" + decoded.trimStart('/')
        }
    }.getOrDefault(baseUrl)
}

object SourceRegistry {

    val sources: Map<String, MangaSource> = linkedMapOf(
        MangaDexSource.id to MangaDexSource
    )

    /**
     * Resolve a source by id. Extension ids ("ext_...") come from loaded extension APKs and are
     * resolved through the [ExtensionDexLoader] registry. "mangadex" is kept only as a runtime
     * implementation for legacy library entries (it is never shown as a source). Anything unknown
     * throws — no silent fallback, so a broken/unloaded source can never masquerade as another.
     */
    fun source(id: String): MangaSource {
        if (id.startsWith("ext_")) {
            return ExtensionDexLoader.get(id)
                ?: throw IllegalStateException("Extension source not loaded (id $id). Reinstall the extension.")
        }
        return sources[id]
            ?: throw IllegalStateException("Unknown source: $id")
    }
}
