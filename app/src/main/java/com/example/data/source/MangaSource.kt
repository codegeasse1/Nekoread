package com.example.data.source

import com.example.data.extension.ExtensionDexLoader
import com.example.data.local.ChapterEntity
import com.example.data.local.MangaEntity

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
    suspend fun getDetails(fullMangaId: String): MangaEntity
    suspend fun getChapters(fullMangaId: String): List<ChapterEntity>
    suspend fun getPageUrls(rawChapterId: String): List<String>
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
