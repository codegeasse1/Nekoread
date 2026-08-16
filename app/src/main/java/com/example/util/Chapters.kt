package com.example.util

import com.example.data.local.ChapterEntity

/** Grouping key for duplicate detection — the chapter number when the manga has numbered
 *  chapters, otherwise the normalized chapter name (unnumbered extras keep distinct names). */
fun chapterKey(ch: ChapterEntity, numbered: Boolean): Any =
    if (numbered && ch.chapterNumber > 0f) ch.chapterNumber
    else ch.name.trim().lowercase().replace(Regex("\\s+"), " ")

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
 * Map a stored chapter id (e.g. from saved reading progress / "continue reading") to the chapter
 * that survived deduplication, so resuming a manga whose last-read chapter was a removed duplicate
 * still opens the right release. Falls back to null when the id doesn't exist in the list.
 */
fun resolveDedupedChapter(chapters: List<ChapterEntity>, chapterId: String): ChapterEntity? {
    val direct = chapters.firstOrNull { it.id == chapterId } ?: return null
    val numbered = chapters.any { it.chapterNumber > 0f }
    return dedupeChapters(chapters).firstOrNull { chapterKey(it, numbered) == chapterKey(direct, numbered) }
}
