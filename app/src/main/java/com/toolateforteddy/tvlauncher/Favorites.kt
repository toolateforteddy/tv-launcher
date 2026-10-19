package com.toolateforteddy.tvlauncher

import android.content.Context

/** An ordered list of favorite tile keys, kept in SharedPreferences. */
class Favorites(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun keys(): List<String> =
        prefs.getString(KEY, null)?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()

    fun contains(key: String) = key in keys()

    fun add(key: String) = save(keys() - key + key)

    fun remove(key: String) = save(keys() - key)

    /** Moves [key] by [delta] places, clamped to the ends of the list. */
    fun move(key: String, delta: Int) {
        val list = keys().toMutableList()
        val from = list.indexOf(key)
        if (from < 0) return
        list.removeAt(from)
        list.add((from + delta).coerceIn(0, list.size), key)
        save(list)
    }

    private fun save(keys: List<String>) {
        prefs.edit().putString(KEY, keys.joinToString("\n")).apply()
    }

    private companion object {
        const val KEY = "order"
    }
}
