package com.example.data.coil

import coil.request.ImageRequest
import coil.request.Options
import java.io.File

/** Parameter key used to route a request through [TachiyomiReaderDecoder]'s border-crop path. */
internal const val KEY_CROP_BORDERS = "nekoread_crop_borders"

/**
 * Coil 2 request extension mirroring chimahon's Coil 3 builder helper. When [enabled], the request
 * is decoded by [TachiyomiReaderDecoder] (which crops the page's blank borders) and given a
 * distinct memory/disk cache key so cropped and uncropped decodes of the same file never collide in
 * the cache. The parameter also flows to [Options.parameters] so the decoder can see the flag.
 */
fun ImageRequest.Builder.cropBorders(enabled: Boolean): ImageRequest.Builder = apply {
    setParameter(KEY_CROP_BORDERS, enabled)
    if (enabled) {
        decoderFactory(TachiyomiReaderDecoder.Factory())
        val base = when (val d = data) {
            is File -> d.absolutePath
            is String -> d
            else -> d?.toString() ?: "null"
        }
        memoryCacheKey("${base}#crop")
        diskCacheKey("${base}#crop")
    }
}

/** Whether this request asked for border cropping (read from [Options.parameters]). */
val Options.cropBorders: Boolean
    get() = parameters.value<Boolean>(KEY_CROP_BORDERS) ?: false
