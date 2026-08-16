package com.example.util

import com.example.data.local.ChapterEntity

/** Grouping key for duplicate detection — the chapter number AND name when the manga has
 *  numbered chapters (so only genuine same-release duplicates collapse, never two different
 *  chapters that merely share a number across a source's mirror sites/series), otherwise the
 *  normalized chapter name. */
fun chapterKey(ch: ChapterEntity, numbered: Boolean): Any =
    if (numbered && ch.chapterNumber > 0f) Pair(ch.chapterNumber, normalizeChapterName(ch.name))
    else normalizeChapterName(ch.name)

/**
 * Collapse duplicate chapters into one entry per release. Some sources (e.g. Comix) return the
 * same chapter once per mirror site / scanlator, which made the reader auto-advance "chapter 1
 * → chapter 1 → chapter 1" (one row per source) instead of 1 → 2, and made scrolling back up to
 * the top re-load a whole duplicate chapter. Keeps the release the user has already read or made
 * progress on when one exists, otherwise the first occurrence in the given order.
 */
fun dedupeChapters(chapters: List<ChapterEntity>): List<ChapterEntity> {
    if (chapters.size <= 1) return chapters
    val numbered = chapters.any { it.chapterNumber > 0f }
    val kept = LinkedHashMap<Any, ChapterEntity>()
    for (ch in chapters) {
        val key = chapterKey(ch, numbered)
        val cur = kept[key]
        if (cur == null || (ch.read && !cur.read) || (ch.lastPageRead > 1 && cur.lastPageRead <= 1 && !cur.read)) {
            kept[key] = ch
        }
    }
    return kept.values.toList()
}

/**
 * Resolve which chapter a stored chapter id should open. The EXACT chapter always wins — clicking
 * a row must open that exact row, never a same-numbered sibling from another mirror/series. The
 * id is only absent if saved progress predates a source re-fetch; in that case null lets the
 * reader fall back gracefully.
 */
fun resolveDedupedChapter(chapters: List<ChapterEntity>, chapterId: String): ChapterEntity? =
    chapters.firstOrNull { it.id == chapterId }

private fun normalizeChapterName(name: String): String =
    name.trim().lowercase().replace(Regex("\\s+"), " ")
