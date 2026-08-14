package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import rx.Observable

interface Source {
    val id: Long
    val name: String
    fun fetchMangaDetails(manga: SManga): Observable<SManga>
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>>
    fun fetchPageList(chapter: SChapter): Observable<List<Page>>
}
