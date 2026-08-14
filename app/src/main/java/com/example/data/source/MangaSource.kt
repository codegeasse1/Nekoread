package com.example.data.source

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

    fun source(id: String): MangaSource = sources[id] ?: MangaDexSource
}
