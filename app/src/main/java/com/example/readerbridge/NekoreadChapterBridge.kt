package com.example.readerbridge

import android.content.Context
import io.aatricks.easyreader.data.model.ContentType
import io.aatricks.easyreader.data.repository.LibraryRepository
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.local.ChapterEntity

/**
 * Bridges Nekoread's chapter data into the vendored reader engine without touching the engine's
 * code: each chapter is rendered as a local HTML file whose <img> tags list the chapter's page
 * URLs, wrapped in a container class the engine's parser recognises (`.container-chapter-reader`).
 * The engine then loads it via `file://...html` → HTML content → its own tiled image pipeline.
 *
 * File names are `nekoread_chapter_<index>.html` where <index> is the 1-based position of the
 * chapter in Nekoread's chapter list. The engine's built-in prev/next navigation works by
 * adjusting the number in the URL (`(chapter[-_/])(\d+)`), so neighbouring chapters just need
 * their files to exist — which [ensureWindow] does on every load, keeping the chain warm as the
 * user reads.
 */
object NekoreadChapterBridge {

    private const val FILE_PREFIX = "nekoread_chapter_"
    private const val FILE_SUFFIX = ".html"

    fun bridgeDir(context: Context): File = File(context.cacheDir, "nekoread_bridge")

    fun fileForIndex(index: Int): String = "$FILE_PREFIX$index$FILE_SUFFIX"

    /** 1-based chapter index from a bridge file URL (null for anything else). */
    fun indexFromUrl(url: String?): Int? {
        if (url == null || !url.startsWith("file://")) return null
        val name = url.removePrefix("file://").substringAfterLast('/')
        if (!name.startsWith(FILE_PREFIX) || !name.endsWith(FILE_SUFFIX)) return null
        return name.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX).toIntOrNull()
    }

    fun fileUrl(context: Context, index: Int): String =
        "file://" + File(bridgeDir(context), fileForIndex(index)).absolutePath

    fun fileExists(context: Context, index: Int): Boolean =
        File(bridgeDir(context), fileForIndex(index)).exists()

    /** Writes one chapter's bridge HTML. Returns its file:// URL (null if there are no pages). */
    fun writeChapter(context: Context, index: Int, title: String, pageUrls: List<String>): String? {
        if (pageUrls.isEmpty()) return null
        val dir = bridgeDir(context).apply { mkdirs() }
        val html = buildString {
            append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>")
            append(title.escapeHtml())
            append("</title></head><body><div class=\"container-chapter-reader\">")
            for (u in pageUrls) {
                append("<img src=\"")
                append(u.escapeHtml())
                append("\">")
            }
            append("</div></body></html>")
        }
        val file = File(dir, fileForIndex(index))
        file.writeText(html)
        return "file://" + file.absolutePath
    }

    /**
     * Ensures bridge files exist for the given chapter indices, fetching page lists on demand.
     * Only missing files are written; failures for a chapter are skipped (leaving it unwritten).
     */
    suspend fun ensureFiles(
        context: Context,
        chapters: List<ChapterEntity>,
        indices: Iterable<Int>,
        fetchPages: suspend (String) -> List<String>
    ) = withContext(Dispatchers.IO) {
        // File indices are 1-based (the chapter's position in the list); the list is 0-based.
        for (i in indices) {
            if (i < 1 || i > chapters.size) continue
            if (fileExists(context, i)) continue
            val ch = chapters[i - 1]
            val urls = runCatching { fetchPages(ch.id) }.getOrDefault(emptyList())
            writeChapter(context, i, ch.name.ifBlank { "Chapter $i" }, urls)
        }
    }

    /** Warms the window around [index] (prev/next/next-next) so the engine's prev/next always lands. */
    suspend fun ensureWindow(
        context: Context,
        chapters: List<ChapterEntity>,
        index: Int,
        fetchPages: suspend (String) -> List<String>
    ) {
        ensureFiles(context, chapters, (index - 1..index + 2), fetchPages)
    }

    /**
     * Writes every chapter's bridge file in the background (sequentially, throttled) so the
     * reader's Chapters sheet works even when jumping far ahead. Skipped for already-written files.
     */
    suspend fun writeAllChapters(
        context: Context,
        chapters: List<ChapterEntity>,
        fetchPages: suspend (String) -> List<String>
    ) = withContext(Dispatchers.IO) {
        for (i in chapters.indices) {
            val fileIndex = i + 1 // file indices are 1-based
            if (fileExists(context, fileIndex)) continue
            val ch = chapters[i]
            val urls = runCatching { fetchPages(ch.id) }.getOrDefault(emptyList())
            writeChapter(context, fileIndex, ch.name.ifBlank { "Chapter $fileIndex" }, urls)
            delay(80)
        }
    }

    /**
     * Seeds the engine's own library database with one item per Nekoread chapter (url = bridge file
     * URL, baseTitle = manga title) so its Chapters sheet lists them. Idempotent.
     */
    suspend fun seedLibrary(
        libraryRepository: LibraryRepository,
        mangaTitle: String,
        chapters: List<ChapterEntity>,
        context: Context
    ) = withContext(Dispatchers.IO) {
        for ((i, ch) in chapters.withIndex()) {
            val url = fileUrl(context, i + 1)
            if (libraryRepository.getItemByUrl(url) != null) continue
            runCatching {
                libraryRepository.addItem(
                    title = ch.name.ifBlank { "Chapter ${i + 1}" },
                    url = url,
                    contentType = ContentType.HTML,
                    baseTitle = mangaTitle,
                    totalChapters = chapters.size
                )
            }
        }
    }

    private fun String.escapeHtml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
