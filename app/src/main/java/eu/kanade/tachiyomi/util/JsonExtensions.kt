package eu.kanade.tachiyomi.util

import kotlinx.serialization.json.Json

/**
 * App provided default [Json] instance. Configured as
 * ```
 * Json {
 *     ignoreUnknownKeys = true
 *     explicitNulls = false
 *     coerceInputValues = true
 * }
 * ```
 *
 * @since extensions-lib 16
 */
val defaultJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
