package com.example.data.extension

import com.example.data.source.TachiyomiHttpSourceAdapter
import dalvik.system.DexClassLoader
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.io.File
import java.security.MessageDigest

/**
 * Tadami/Mihon-style extension loader: loads an extension APK's dex against the app classpath
 * (which contains the real `eu.kanade.tachiyomi.*` source-api runtime) and hands back the
 * [Source] instances it exposes.
 *
 * The keiyoushi/Mihon compiler generates an `ExtensionGenerated` class that is either the single
 * source itself or a [SourceFactory] with `createSources()`, so both are handled here.
 */
object ExtensionDexLoader {

    private val registry = HashMap<String, TachiyomiHttpSourceAdapter>()

    /** Sources that are currently loaded and usable. */
    val loaded: List<TachiyomiHttpSourceAdapter> get() = registry.values.toList()

    fun register(adapter: TachiyomiHttpSourceAdapter) {
        registry[adapter.id] = adapter
    }

    fun get(key: String): TachiyomiHttpSourceAdapter? = registry[key]

    /** Remove every loaded source that belongs to [packageName] (used on uninstall). */
    fun unregisterExtension(packageName: String) {
        val toRemove = registry.values.filter { it.packageName == packageName }
        for (a in toRemove) registry.remove(a.id)
    }

    fun clear() {
        registry.clear()
    }

    /** Stable id for an extension source, used as the manga-id prefix and the Sources-tab row id. */
    fun key(pkg: String, rawSourceId: String): String = "ext_" + md5("$pkg:$rawSourceId").take(12)

    fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { String.format("%02x", it) }
    }

    /**
     * Load an extension APK and return the source instances it provides.
     *
     * @param apkFile     the downloaded extension APK
     * @param dexCacheDir optimized-dex cache dir (app-private)
     * @param packageName the extension's package name (from the repo index) — used to derive the
     *                    generated class name when the manifest can't be parsed
     */
    fun loadApk(apkFile: File, dexCacheDir: File, packageName: String): List<Source> {
        // DexClassLoader needs a real, writable optimized-dex directory (API 24/25) or loading
        // fails with "not writable" errors.
        if (!dexCacheDir.exists() && !dexCacheDir.mkdirs()) {
            throw IllegalStateException("Cannot create dex cache dir $dexCacheDir")
        }
        val loader = DexClassLoader(
            apkFile.absolutePath,
            dexCacheDir.absolutePath,
            null,
            ExtensionDexLoader::class.java.classLoader
        )
        val className = resolveExtensionClass(loader, packageName)
        val clazz = Class.forName(className, true, loader)
        val instance = clazz.getDeclaredConstructor().newInstance()
        return when (instance) {
            is SourceFactory -> instance.createSources()
            is Source -> listOf(instance)
            else -> emptyList()
        }
    }

    private fun resolveExtensionClass(loader: ClassLoader, packageName: String): String {
        // Every keiyoushi/Mihon extension generates "ExtensionGenerated" in its own package.
        val convention = "$packageName.ExtensionGenerated"
        return try {
            Class.forName(convention, false, loader)
            convention
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException("No source class found in extension APK", e)
        }
    }
}
