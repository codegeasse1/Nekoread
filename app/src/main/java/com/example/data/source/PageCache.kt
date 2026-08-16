package com.example.data.source

import java.security.MessageDigest

/**
 * Collision-proof cache key for a reader page image: MD5 of the full image URL. The previous key
 * (imageUrl.hashCode(), a 32-bit hash) collided across the ever-growing on-device reader_pages
 * cache, so two DIFFERENT images could share one cache file and the reader displayed a random
 * other chapter's page ("clicking episode 5 loaded episode 8"). 32 hex chars = effectively unique.
 */
fun pageCacheKey(imageUrl: String): String =
    MessageDigest.getInstance("MD5")
        .digest(imageUrl.toByteArray())
        .joinToString("") { "%02x".format(it) }
