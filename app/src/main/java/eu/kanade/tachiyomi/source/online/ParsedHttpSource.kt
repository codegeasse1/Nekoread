package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.parse
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Real base class for HTML-parsing (jsoup) sources, matching the extensions-lib contract.
 */
@Suppress("unused", "unused_parameter")
abstract class ParsedHttpSource : HttpSource() {

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.parse()
        val elements = document.select(popularMangaSelector())
        val nextPageUrl = elements.first()?.let {
            popularMangaNextPageSelector()?.let { selector ->
                document.select(selector).first()?.attr("href")
            }
        }
        val mangas = elements.map { popularMangaFromElement(it) }
        return MangasPage(mangas, nextPageUrl != null)
    }

    abstract protected fun popularMangaSelector(): String

    abstract protected fun popularMangaFromElement(element: Element): SManga

    abstract protected fun popularMangaNextPageSelector(): String?

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.parse()
        val elements = document.select(searchMangaSelector())
        val nextPageUrl = elements.first()?.let {
            searchMangaNextPageSelector()?.let { selector ->
                document.select(selector).first()?.attr("href")
            }
        }
        val mangas = elements.map { searchMangaFromElement(it) }
        return MangasPage(mangas, nextPageUrl != null)
    }

    abstract protected fun searchMangaSelector(): String

    abstract protected fun searchMangaFromElement(element: Element): SManga

    abstract protected fun searchMangaNextPageSelector(): String?

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.parse()
        val elements = document.select(latestUpdatesSelector())
        val nextPageUrl = elements.first()?.let {
            latestUpdatesNextPageSelector()?.let { selector ->
                document.select(selector).first()?.attr("href")
            }
        }
        val mangas = elements.map { latestUpdatesFromElement(it) }
        return MangasPage(mangas, nextPageUrl != null)
    }

    abstract protected fun latestUpdatesSelector(): String

    abstract protected fun latestUpdatesFromElement(element: Element): SManga

    abstract protected fun latestUpdatesNextPageSelector(): String?

    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.parse())

    abstract protected fun mangaDetailsParse(document: Document): SManga

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.parse()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    abstract protected fun chapterListSelector(): String

    abstract protected fun chapterFromElement(element: Element): SChapter

    override fun pageListParse(response: Response): List<Page> = pageListParse(response.parse())

    abstract protected fun pageListParse(document: Document): List<Page>

    override fun imageUrlParse(response: Response): String = imageUrlParse(response.parse())

    abstract protected fun imageUrlParse(document: Document): String
}
