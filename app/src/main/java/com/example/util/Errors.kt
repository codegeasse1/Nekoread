package com.example.util

import eu.kanade.tachiyomi.network.HttpException

/**
 * Formats a [Throwable] as "ClassName: message → cause: message" (up to a few levels) so no
 * failure ever surfaces as a blank "Failed to load ..." fallback. HttpException carries its HTTP
 * code in a field even when the message is present, so print it explicitly.
 */
fun Throwable.describe(): String {
    val sb = StringBuilder()
    var t: Throwable? = this
    var depth = 0
    while (t != null && depth < 4) {
        if (sb.isNotEmpty()) sb.append(" → ")
        sb.append(
            if (t is HttpException) {
                "HttpException(HTTP ${t.code}${if (t.url.isNotBlank()) " — ${t.url}" else ""})"
            } else {
                val cls = t.javaClass.simpleName.ifBlank { t.javaClass.name.substringAfterLast('.') }
                val msg = t.message?.takeIf { it.isNotBlank() } ?: "(no message)"
                "$cls: $msg"
            }
        )
        t = t.cause
        depth++
    }
    return sb.toString()
}
