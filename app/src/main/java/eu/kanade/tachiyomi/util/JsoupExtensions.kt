package eu.kanade.tachiyomi.util

import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

fun Response.parse(): Document = Jsoup.parse(body()?.string() ?: "")
