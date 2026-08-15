package com.example.data.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ParsedSource(
    val id: String,
    val name: String,
    val lang: String,
    val baseUrl: String
)

data class ParsedExtension(
    val packageName: String,
    val name: String,
    val versionName: String,
    val versionCode: String,
    val libVersion: String,
    val contentWarning: String,
    val nsfw: Boolean,
    val apkUrl: String,
    val iconUrl: String,
    val sources: List<ParsedSource>
)

data class ParsedRepo(
    val name: String,
    val extensions: List<ParsedExtension>
)

class ExtensionNetworkException(message: String) : Exception(message)

/**
 * Real network layer for extension repos. Two index formats are supported, matching what Mihon /
 * Aniyomi / Tadami consume:
 *  - new format (keiyoushi): {"name":..., "extensionList": {"extensions":[{name, packageName,
 *    resources:{apkUrl, iconUrl, jarUrl}, extensionLib, versionCode, versionName, contentWarning,
 *    sources:[{id,name,language,homeUrl}]}]}}
 *  - legacy format (mihon):  {"extensions":[{name, pkg, apk, icon, lang, code, version, nsfw,
 *    sources:[{name,lang,id,baseUrl}]}]}  (apkUrl = <indexDir>/apk/<apk>)
 *
 * Repos may also ship their list as a bare JSON array (some Aniyomi/old repos).
 */
object ExtensionNetwork {

    val INDEX_FILE_NAMES = listOf("index.json", "repo.json", "index.min.json", "plugins.json", "plugins.min.json")

    /** True if [url] already points at a repo index/metadata file. */
    fun isIndexUrl(url: String): Boolean {
        val trimmed = url.trim().trimEnd('/')
        return INDEX_FILE_NAMES.any { trimmed.endsWith("/$it", ignoreCase = true) }
    }

    /** Base dir of a repo, stripping any known index file suffix. */
    fun indexDirFor(indexUrl: String): String {
        var url = indexUrl.trim().trimEnd('/')
        for (name in INDEX_FILE_NAMES) {
            if (url.endsWith("/$name", ignoreCase = true)) {
                url = url.dropLast(name.length + 1).trimEnd('/')
                break
            }
        }
        return url
    }

    /** Derive a readable repo name from its base dir when the index carries no name field. */
    private fun guessRepoName(indexDir: String): String {
        val segments = indexDir.split("/").filter { it.isNotBlank() }
        if (segments.isEmpty()) return "Custom Repo"
        var name = segments.last()
        if (name in setOf("repo", "main", "master", "trunk", "branch")) {
            name = if (segments.size >= 2) segments[segments.size - 2] else "Custom Repo"
        }
        return name
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun getText(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw ExtensionNetworkException("HTTP ${response.code} for $url${if (body.isNotBlank()) ": ${body.take(150)}" else ""}")
            }
            return body
        }
    }

    private fun optStr(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            if (o.has(k) && !o.isNull(k)) return o.getString(k).trim()
        }
        return ""
    }

    private fun optBool(o: JSONObject, vararg keys: String): Boolean {
        for (k in keys) {
            if (o.has(k) && !o.isNull(k)) return o.optBoolean(k)
        }
        return false
    }

    private fun parseSources(json: JSONArray): List<ParsedSource> {
        val out = mutableListOf<ParsedSource>()
        for (i in 0 until json.length()) {
            val s = json.getJSONObject(i)
            val id = optStr(s, "id")
            val name = optStr(s, "name")
            if (id.isBlank() && name.isBlank()) continue
            val lang = optStr(s, "lang", "language").ifBlank { "en" }
            val baseUrl = optStr(s, "baseUrl", "homeUrl")
            out.add(ParsedSource(id = id, name = name, lang = lang, baseUrl = baseUrl))
        }
        return out
    }

    private fun parseLegacyExtension(item: JSONObject, indexDir: String): ParsedExtension? {
        val pkg = optStr(item, "pkg", "packageName")
        if (pkg.isBlank()) return null

        // Some repos (including codegeasse-mihon-extension) publish absolute http(s) URLs in
        // the apk/icon fields rather than paths relative to the repo base dir — use them as-is.
        fun abs(base: String, raw: String): String = when {
            raw.isBlank() -> ""
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            else -> base + raw
        }

        val apk = optStr(item, "apk")
        val apkUrl = abs(indexDir + "/apk/", apk)
        val icon = optStr(item, "icon")
        val iconUrl = abs(indexDir + "/", icon)
        val sources = if (item.has("sources")) parseSources(item.getJSONArray("sources")) else emptyList()
        return ParsedExtension(
            packageName = pkg,
            name = optStr(item, "name").ifBlank { pkg.substringAfterLast(".") },
            versionName = optStr(item, "version", "versionName").ifBlank { "1.0" },
            versionCode = optStr(item, "code", "versionCode").ifBlank { "1" },
            libVersion = optStr(item, "libVersion", "tachiyomix.extensionLib"),
            contentWarning = optStr(item, "contentWarning", "warning"),
            nsfw = optBool(item, "nsfw"),
            apkUrl = apkUrl,
            iconUrl = iconUrl,
            sources = sources
        )
    }

    private fun parseNewExtension(item: JSONObject): ParsedExtension? {
        val pkg = optStr(item, "packageName", "pkg")
        if (pkg.isBlank()) return null
        val resources = if (item.has("resources")) item.getJSONObject("resources") else JSONObject()
        val apkUrl = optStr(resources, "apkUrl")
        val iconUrl = optStr(resources, "iconUrl")
        if (apkUrl.isBlank()) return null
        val sources = if (item.has("sources")) parseSources(item.getJSONArray("sources")) else emptyList()
        val contentWarning = optStr(item, "contentWarning").ifBlank { "" }
        // New-format repos flag NSFW extensions via the CONTENT_WARNING_NSFW marker.
        val nsfw = optBool(item, "nsfw") || contentWarning == "CONTENT_WARNING_NSFW"
        return ParsedExtension(
            packageName = pkg,
            name = optStr(item, "name").ifBlank { pkg.substringAfterLast(".") },
            versionName = optStr(item, "versionName").ifBlank { "1.0" },
            versionCode = optStr(item, "versionCode").ifBlank { "1" },
            libVersion = optStr(item, "extensionLib", "libVersion"),
            contentWarning = if (contentWarning == "CONTENT_WARNING_NSFW") "" else contentWarning,
            nsfw = nsfw,
            apkUrl = apkUrl,
            iconUrl = iconUrl,
            sources = sources
        )
    }

    /** Parse an index.json body, detecting which format it is. */
    fun parseRepoIndex(indexUrl: String, body: String): ParsedRepo {
        val indexDir = indexDirFor(indexUrl)
        val fallbackName = guessRepoName(indexDir)
        val extensions = mutableListOf<ParsedExtension>()
        var repoName = fallbackName

        val trimmed = body.trim()
        if (trimmed.startsWith("[")) {
            // Legacy format as a bare JSON array (some repos ship the extension list directly).
            try {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    parseLegacyExtension(arr.getJSONObject(i), indexDir)?.let { extensions.add(it) }
                }
            } catch (e: Exception) {
                throw ExtensionNetworkException("Not a valid JSON repo index")
            }
        } else {
            val root = try {
                JSONObject(trimmed)
            } catch (e: Exception) {
                throw ExtensionNetworkException("Not a valid JSON repo index")
            }

            repoName = optStr(root, "name").ifBlank { fallbackName }

            // New format: extensionList.extensions[]
            var arr: JSONArray? = null
            if (root.has("extensionList") && root.optJSONObject("extensionList")?.has("extensions") == true) {
                arr = root.getJSONObject("extensionList").getJSONArray("extensions")
                for (i in 0 until arr.length()) {
                    parseNewExtension(arr.getJSONObject(i))?.let { extensions.add(it) }
                }
            }

            // Legacy format: extensions[]
            if (extensions.isEmpty() && root.has("extensions")) {
                arr = root.getJSONArray("extensions")
                for (i in 0 until arr.length()) {
                    parseLegacyExtension(arr.getJSONObject(i), indexDir)?.let { extensions.add(it) }
                }
            }
        }

        if (extensions.isEmpty()) {
            throw ExtensionNetworkException("No extensions found in this index")
        }

        return ParsedRepo(name = repoName, extensions = extensions)
    }

    /** Fetch and parse a repo index over HTTP. Throws [ExtensionNetworkException] on any failure. */
    suspend fun fetchRepoIndex(url: String): ParsedRepo = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        try {
            val body = getText(cleanUrl)
            parseRepoIndex(cleanUrl, body)
        } catch (e: ExtensionNetworkException) {
            throw e
        } catch (e: IOException) {
            throw ExtensionNetworkException("Network error: ${e.message ?: "could not reach repo"}")
        }
    }

    /**
     * Download an extension APK to [destFile]. Streams to disk, verifies it is a valid zip
     * (APK magic "PK"). Throws [ExtensionNetworkException] on failure.
     */
    suspend fun downloadApk(apkUrl: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(apkUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ExtensionNetworkException("Download failed (HTTP ${response.code})")
            }
            val body = response.body ?: throw ExtensionNetworkException("Empty download response")
            destFile.parentFile?.mkdirs()
            body.byteStream().use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        // APK files are zip archives — check the magic bytes so we never mark garbage as installed.
        val head = destFile.inputStream().use { input ->
            val buf = ByteArray(4)
            val n = input.read(buf)
            if (n == 4) buf else buf.copyOf(n)
        }
        val isZip = head.size == 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
        if (!isZip) {
            destFile.delete()
            throw ExtensionNetworkException("Downloaded file is not a valid APK")
        }
        true
    }

    /** Serialize a list of parsed sources into the JSON string stored on an [ExtensionEntity]. */
    fun sourcesToJson(sources: List<ParsedSource>): String {
        val arr = JSONArray()
        for (s in sources) {
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("lang", s.lang)
                    .put("baseUrl", s.baseUrl)
            )
        }
        return arr.toString()
    }

    /** Parse the JSON string produced by [sourcesToJson] back into source descriptors. */
    fun parseSourcesJson(json: String): List<ParsedSource> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            parseSources(arr)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
