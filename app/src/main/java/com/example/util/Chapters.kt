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
 *
 * A source's `chapterNumber` is trusted only when it forms a strictly increasing sequence (no
 * ties/regressions). Extensions that parse the wrong number out of names like "0026 Season 001
 * Finale" (e.g. grabbing the trailing "001" → 1) produce ties like 1,1,1,2,2,... — sorting by
 * them scrambles the list (newest-first ties land on top). Those sources fall back to the name
 * sort, whose leading-number parse ("0026 ..." → 26) gives the correct reading order.
 */
fun sortChapters(chapters: List<ChapterEntity>): List<ChapterEntity> {
    val numbered = chapters.filter { it.chapterNumber > 0f }
    if (numbered.isNotEmpty()) {
        var prev = Float.NEGATIVE_INFINITY
        var strictlyIncreasing = true
        for (c in numbered.sortedBy { it.chapterNumber }) {
            if (c.chapterNumber <= prev) {
                strictlyIncreasing = false
                break
            }
            prev = c.chapterNumber
        }
        if (strictlyIncreasing) {
            return chapters.sortedBy { it.chapterNumber }
        }
    }
    return chapters.sortedWith(
        compareBy<ChapterEntity> { chapterNameNumber(it.name) }
            .thenBy { it.dateUpload }
            .thenBy { it.name }
    )
}
