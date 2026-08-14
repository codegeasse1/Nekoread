package eu.kanade.tachiyomi.source.model

/**
 * Filter model, byte-for-byte compatible with the Tachiyomi/Mihon source-api (extensions-lib)
 * contract. Loaded extension APKs instantiate these classes and read/write their state, so the
 * concrete subclasses must stay concrete (open) and the JVM signatures must match what the
 * compiled extensions link against.
 */
open class Filter<T>(val name: String, var state: T) {
    open fun isVisible(): Boolean = true
}

open class Header(name: String) : Filter<Any>(name, 0)

open class Separator(name: String = "") : Filter<Any>(name, 0)

open class CheckBox(name: String, state: Boolean = false) : Filter<Boolean>(name, state)

open class TriState(name: String, val state: Int = 0) : Filter<Int>(name, state) {
    fun isIgnored() = state == STATE_IGNORE
    fun isIncluded() = state == STATE_INCLUDED
    fun isExcluded() = state == STATE_EXCLUDED

    companion object {
        const val STATE_IGNORE = 0
        const val STATE_INCLUDED = 1
        const val STATE_EXCLUDED = 2
    }
}

open class Select<T>(name: String, val values: Array<T>, state: Int = 0) : Filter<Int>(name, state)

open class Text(name: String, state: String = "") : Filter<String>(name, state)

open class Group<T>(name: String, val state: List<T>) : Filter<List<T>>(name, state)

class Sort(name: String, val values: Array<String>, state: Selection = Selection(0, false)) : Filter<Selection>(name, state) {
    class Selection(val index: Int, val ascending: Boolean) {
        operator fun component1() = index
        operator fun component2() = ascending
        fun isAscending() = ascending
    }
}
