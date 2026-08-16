package com.example.util

import com.example.data.local.ChapterEntity

/** First number found in a chapter name ("Episode 5 - Reunion" → 5f), used to order unnumbered
 *  sources (chapterNumber left at the extension's -1 default) by the number in their own names.
 *  Entries with no number sort last (MAX_VALUE). */
fun chapterNameNumber(name: String): Float =
    Regex("""\d+(\.\d+)?""").find(name)?.value?.toFloatOrNull() ?: Float.MAX_VALUE

/**
 * Reading order for a chapter list. Sources that provide real chapter numbers (MangaDex, most
 * extensions) sort by that number. Sources that leave the extension's -1 default sort by the
 * number in the chapter NAME first (stable, and matches the labels the user sees), then upload
 * date, then name — NEVER by list position. Position is whatever order the source happened to
 * return on that fetch, so numbering/reordering by it made the list, prev/next and continuous
 * scroll point at the wrong chapters (clicking "Episode 5" opened a random neighbour).
 */
fun sortChapters(chapters: List<ChapterEntity>): List<ChapterEntity> =
    if (chapters.any { it.chapterNumber > 0f }) {
        chapters.sortedBy { it.chapterNumber }
    } else {
        chapters.sortedWith(
            compareBy<ChapterEntity> { chapterNameNumber(it.name) }
                .thenBy { it.dateUpload }
                .thenBy { it.name }
        )
    }
