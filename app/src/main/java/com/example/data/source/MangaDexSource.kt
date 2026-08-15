package com.example.data.source

import com.example.data.local.ChapterEntity
import com.example.data.local.MangaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Real MangaDex source built against the official public API (no auth required):
 *   GET /manga                 - search + latest catalog
 *   GET /manga/{id}            - full details + cover art
 *   GET /manga/{id}/feed       - chapter list (en)
 *   GET /at-home/server/{id}   - page image URLs for a chapter
 * Docs: https://api.mangadex.org/docs/swagger.html
 */
object MangaDexSource : MangaSource {

    override val id = "mangadex"
    override val name = "MangaDex"
    override val baseUrl = "https://mangadex.org"
    override val lang = "en"
    override val sourceType = "MANGA"

    private const val API = "https://api.mangadex.org"
    private const val ID_PREFIX = "mangadex:"
    private const val CH_PREFIX = "mangadex:ch:"
    private const val PAGE_SIZE = 20

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Nekoread/1.0 (Android manga reader; +https://github.com/codegeasse1/Nekoread)")
                .build()
            chain.proceed(request)
        }
        .build()

    private fun rawMangaId(fullId: String): String = fullId.removePrefix(ID_PREFIX)
    private fun fullMangaId(rawId: String): String = ID_PREFIX + rawId
    private fun fullChapterId(rawId: String): String = CH_PREFIX + rawId

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${body.take(200)}")
            }
            return JSONObject(body)
        }
    }

    private fun mangaListUrl(query: String, page: Int): String {
        val builder = (API + "/manga").toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", (page * PAGE_SIZE).toString())
            .addQueryParameter("hasAvailableChapters", "true")
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("includes[]", "artist")
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
        if (query.isNotBlank()) {
            builder.addQueryParameter("title", query)
            builder.addQueryParameter("order[relevance]", "desc")
        } else {
            builder.addQueryParameter("order[latestUploadedChapter]", "desc")
        }
        return builder.build().toString()
    }

    override suspend fun search(query: String, page: Int): List<MangaEntity> = withContext(Dispatchers.IO) {
        parseMangaCollection(getJson(mangaListUrl(query, page)))
    }

    override suspend fun latest(page: Int): List<MangaEntity> = withContext(Dispatchers.IO) {
        parseMangaCollection(getJson(mangaListUrl("", page)))
    }

    override suspend fun getDetails(fullMangaId: String): MangaEntity = withContext(Dispatchers.IO) {
        val url = (API + "/manga/" + rawMangaId(fullMangaId)).toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("includes[]", "artist")
            .build().toString()
        val data = getJson(url).optJSONObject("data")
            ?: throw IOException("Manga not found on MangaDex")
        parseManga(data)
    }

    override suspend fun getChapters(fullMangaId: String): List<ChapterEntity> = withContext(Dispatchers.IO) {
        val out = mutableListOf<ChapterEntity>()
        var offset = 0
        while (true) {
            val url = (API + "/manga/" + rawMangaId(fullMangaId) + "/feed").toHttpUrl().newBuilder()
                .addQueryParameter("limit", "500")
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("translatedLanguage[]", "en")
                .addQueryParameter("includeExternalUrl", "0")
                .addQueryParameter("includes[]", "scanlation_group")
                .addQueryParameter("contentRating[]", "safe")
                .addQueryParameter("contentRating[]", "suggestive")
                .addQueryParameter("order[volume]", "asc")
                .addQueryParameter("order[chapter]", "asc")
                .build().toString()
            val root = getJson(url)
            val data = root.optJSONArray("data") ?: break
            if (data.length() == 0) break
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val attrs = item.optJSONObject("attributes") ?: continue
                if (attrs.optBoolean("isUnavailable")) continue
                if (attrs.optString("externalUrl").isNotBlank()) continue
                out.add(parseChapter(item, fullMangaId))
            }
            val returned = root.optInt("limit", 0)
            if (returned < 500) break
            offset += returned
            if (offset > 5000) break
        }
        out
    }

    override suspend fun getPageUrls(rawChapterId: String): List<String> = withContext(Dispatchers.IO) {
        if (rawChapterId.isBlank()) return@withContext emptyList()
        val url = "$API/at-home/server/$rawChapterId"
        val root = getJson(url)
        val base = root.optString("baseUrl")
        val chapter = root.optJSONObject("chapter") ?: return@withContext emptyList()
        val hash = chapter.optString("hash")
        val data = chapter.optJSONArray("data") ?: return@withContext emptyList()
        val pages = mutableListOf<String>()
        for (i in 0 until data.length()) {
            val file = data.optString(i)
            if (file.isNotBlank()) pages.add("$base/data/$hash/$file")
        }
        pages
    }

    override suspend fun downloadPageImage(page: MangaSource.PageDescriptor, target: File): File =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(page.imageUrl).build()
            client.newCall(request).execute().use { response ->
                val body = response.body ?: throw IOException("Empty body for ${page.imageUrl}")
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} for ${page.imageUrl}")
                }
                target.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            target
        }

    private fun parseMangaCollection(root: JSONObject): List<MangaEntity> {
        val data = root.optJSONArray("data") ?: JSONArray()
        val list = mutableListOf<MangaEntity>()
        for (i in 0 until data.length()) {
            data.optJSONObject(i)?.let { list.add(parseManga(it)) }
        }
        return list
    }

    private fun parseManga(item: JSONObject): MangaEntity {
        val rawId = item.optString("id")
        val attrs = item.optJSONObject("attributes") ?: JSONObject()

        val title = pickTitle(attrs.optJSONObject("title"), attrs.optJSONArray("altTitles"))
        val description = attrs.optJSONObject("description")?.optString("en", "") ?: ""

        val status = when (attrs.optString("status")) {
            "completed" -> "COMPLETED"
            "hiatus" -> "HIATUS"
            "cancelled" -> "CANCELLED"
            else -> "ONGOING"
        }
        val type = when (attrs.optString("originalLanguage")) {
            "ko" -> "MANHWA"
            "zh" -> "MANHUA"
            else -> "MANGA"
        }

        var author = ""
        var artist = ""
        var coverFile: String? = null
        val relationships = item.optJSONArray("relationships")
        if (relationships != null) {
            for (i in 0 until relationships.length()) {
                val rel = relationships.optJSONObject(i) ?: continue
                when (rel.optString("type")) {
                    "author" -> author = rel.optJSONObject("attributes")?.optString("name") ?: ""
                    "artist" -> artist = rel.optJSONObject("attributes")?.optString("name") ?: ""
                    "cover_art" -> coverFile = rel.optJSONObject("attributes")?.optString("fileName")
                }
            }
        }

        val coverUrl = if (!coverFile.isNullOrBlank()) {
            "https://uploads.mangadex.org/covers/$rawId/$coverFile.512.jpg"
        } else {
            ""
        }

        val genres = buildList<String> {
            val tags = attrs.optJSONArray("tags")
            if (tags != null) {
                for (i in 0 until tags.length()) {
                    val tag = tags.optJSONObject(i) ?: continue
                    val name = tag.optJSONObject("attributes")?.optJSONObject("name")?.optString("en")
                    if (!name.isNullOrBlank()) add(name)
                }
            }
        }.joinToString(", ")

        return MangaEntity(
            id = fullMangaId(rawId),
            title = title,
            coverUrl = coverUrl,
            author = author,
            artist = artist,
            description = description,
            sourceId = id,
            sourceName = name,
            status = status,
            type = type,
            inLibrary = false,
            category = "Reading",
            genres = genres
        )
    }

    private fun parseChapter(item: JSONObject, fullMangaId: String): ChapterEntity {
        val rawId = item.optString("id")
        val attrs = item.optJSONObject("attributes") ?: JSONObject()

        val chapterNum = attrs.optString("chapter")
        val number = chapterNum.toFloatOrNull() ?: 0f
        val chapterTitle = attrs.optString("title")
        val name = when {
            chapterTitle.isNotBlank() && number > 0f -> "Chapter $chapterNum - $chapterTitle"
            number > 0f -> "Chapter $chapterNum"
            chapterTitle.isNotBlank() -> chapterTitle
            else -> "Extra Chapter"
        }

        val publish = attrs.optString("publishAt", "")
        val date = if (publish.length >= 10) publish.substring(0, 10) else "Unknown"

        var scanlator = ""
        val relationships = item.optJSONArray("relationships")
        if (relationships != null) {
            for (i in 0 until relationships.length()) {
                val rel = relationships.optJSONObject(i) ?: continue
                if (rel.optString("type") == "scanlation_group") {
                    scanlator = rel.optJSONObject("attributes")?.optString("name") ?: ""
                }
            }
        }

        return ChapterEntity(
            id = fullChapterId(rawId),
            mangaId = fullMangaId,
            chapterNumber = number,
            name = name,
            scanlator = if (scanlator.isBlank()) "Official" else scanlator,
            releaseDate = date,
            read = false,
            bookmarked = false,
            lastPageRead = 1,
            totalPages = attrs.optInt("pages", 0),
            fetchUrl = rawId,
            dateUpload = parseTimestamp(publish)
        )
    }

    private fun pickTitle(title: JSONObject?, altTitles: JSONArray?): String {
        if (title != null) {
            title.optString("en").takeIf { it.isNotBlank() }?.let { return it }
            title.keys().forEach { key ->
                title.optString(key).takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        if (altTitles != null) {
            for (i in 0 until altTitles.length()) {
                val obj = altTitles.optJSONObject(i) ?: continue
                obj.optString("en").takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return "Unknown Title"
    }

    private fun parseTimestamp(iso: String): Long {
        if (iso.isBlank()) return System.currentTimeMillis()
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(iso)?.time
                ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
