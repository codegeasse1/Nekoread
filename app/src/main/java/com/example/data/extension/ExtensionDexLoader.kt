package com.example.data.extension

import android.content.Context
import android.content.pm.PackageManager
import com.example.data.source.TachiyomiHttpSourceAdapter
import dalvik.system.DexClassLoader
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.io.File
import java.security.MessageDigest

/**
 * Tadami/Mihon-style extension loader: loads an extension APK's dex against the app classpath
 * (which contains the real `eu.kanade.tachiyomi.*` source-api runtime) and hands back the
 * [Source] instances it exposes.
 *
 * Two compiler styles are supported, matching what Mihon/Aniyomi and Tadami accept:
 *
 *  1. **Mihon/Aniyomi style** (tachiyomi-extension compiler): the manifest meta-data key
 *     `tachiyomi.extension.class` lists the entry classes (semicolon-separated), each either a
 *     fully-qualified name or a relative name (starting with `.`) resolved against the package.
 *  2. **Keiyoushi/Tadami style**: a class literally named `ExtensionGenerated` in the extension's
 *     own package.
 *
 * Each entry class is either a [Source] itself or a [SourceFactory] exposing `createSources()`.
 * As a last resort a plain `SourceFactory` class referenced by the `tachiyomi.extension.factory`
 * meta-data key is tried too.
 */
object ExtensionDexLoader {

    private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    private const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"

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
     * @param packageName the extension's package name (from the repo index) — used as the base for
     *                    resolving relative entry classes and the `ExtensionGenerated` fallback
     * @param context     used to read the APK's manifest (entry-class meta-data) and as the parent
     *                    for the fallback class loader
     */
    fun loadApk(
        apkFile: File,
        dexCacheDir: File,
        packageName: String,
        context: Context,
    ): List<Source> {
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
        val entryClasses = resolveEntryClasses(apkFile, packageName, context, loader)
        if (entryClasses.isEmpty()) {
            throw IllegalStateException(
                "No source class found in extension APK (no 'tachiyomi.extension.class' " +
                    "manifest entry and no ExtensionGenerated class)"
            )
        }

        val sources = mutableListOf<Source>()
        val failures = mutableListOf<String>()
        for (entry in entryClasses) {
            val instantiated = try {
                instantiate(entry, loader)
            } catch (e: LinkageError) {
                // The dex may reference a symbol that exists in a slightly different (older/newer)
                // form on the parent classpath. Tadami retries with a plain PathClassLoader.
                val fallback = PathClassLoader(apkFile.absolutePath, context.classLoader)
                try {
                    instantiate(entry, fallback)
                } catch (e2: Throwable) {
                    failures.add("$entry (${e2.javaClass.simpleName}: ${e2.message})")
                    continue
                }
            } catch (e: Throwable) {
                failures.add("$entry (${e.javaClass.simpleName}: ${e.message})")
                continue
            }
            sources.addAll(instantiated)
        }
        if (sources.isEmpty() && failures.isNotEmpty()) {
            throw IllegalStateException("Couldn't load any source class: ${failures.joinToString("; ")}")
        }
        return sources
    }

    private fun instantiate(className: String, loader: ClassLoader): List<Source> {
        val clazz = Class.forName(className, true, loader)
        val instance = clazz.getDeclaredConstructor().newInstance()
        return when (instance) {
            is SourceFactory -> instance.createSources()
            is Source -> listOf(instance)
            else -> emptyList()
        }
    }

    private fun resolveEntryClasses(
        apkFile: File,
        packageName: String,
        context: Context,
        loader: ClassLoader,
    ): List<String> {
        val fromManifest = try {
            val info = context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_META_DATA
            )
            val meta = info?.applicationInfo?.metaData
            val declared = meta?.getString(METADATA_SOURCE_CLASS).orEmpty().trim()
            if (declared.isNotEmpty()) {
                declared.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }.map { className ->
            // Relative names (".Comix") resolve against the extension's own package.
            if (className.startsWith(".")) packageName + className else className
        }

        if (fromManifest.isNotEmpty()) return fromManifest

        // Keiyoushi/Tadami compiler style: a class literally named "ExtensionGenerated".
        val convention = "$packageName.ExtensionGenerated"
        if (classExists(convention, loader)) return listOf(convention)

        // Last resort: the legacy factory meta-data key.
        val factory = try {
            context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_META_DATA
            )?.applicationInfo?.metaData?.getString(METADATA_SOURCE_FACTORY)?.trim()
        } catch (e: Exception) {
            null
        }
        if (!factory.isNullOrEmpty()) {
            val name = if (factory.startsWith(".")) packageName + factory else factory
            if (classExists(name, loader)) return listOf(name)
        }
        return emptyList()
    }

    private fun classExists(className: String, loader: ClassLoader): Boolean {
        return try {
            Class.forName(className, false, loader)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
}
