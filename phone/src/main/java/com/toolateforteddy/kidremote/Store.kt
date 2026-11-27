package com.toolateforteddy.kidremote

import android.content.Context
import com.toolateforteddy.kidremote.atv.ClientIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A picture button on the kid screen: a show and the link that opens it on the TV. */
data class Show(val id: String, val name: String, val link: String, val image: String) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("link", link).put("image", image)

    companion object {
        fun fromJson(o: JSONObject) = Show(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            name = o.optString("name"),
            link = o.optString("link"),
            image = o.optString("image"),
        )
    }
}

/** Everything the app remembers, in its private storage: the pairing key, which TV, and the shows. */
class Store(private val context: Context) {
    private val prefs = context.getSharedPreferences("kid_remote", Context.MODE_PRIVATE)

    private val _shows = MutableStateFlow(loadShows())
    val shows: StateFlow<List<Show>> = _shows

    var tvHost: String?
        get() = prefs.getString("tv_host", null)
        set(value) = prefs.edit().putString("tv_host", value).apply()

    var tvName: String?
        get() = prefs.getString("tv_name", null)
        set(value) = prefs.edit().putString("tv_name", value).apply()

    /** Made once and kept: it's what the TV remembers from pairing. Call off the main thread. */
    @Synchronized
    fun identity(): ClientIdentity {
        prefs.getString("identity", null)?.let { saved ->
            runCatching { return ClientIdentity.decode(saved) }
        }
        return ClientIdentity.generate().also { prefs.edit().putString("identity", it.encode()).commit() }
    }

    fun saveShows(shows: List<Show>) {
        val old = _shows.value
        _shows.value = shows
        prefs.edit().putString("shows", JSONArray(shows.map { it.toJson() }).toString()).apply()
        // Clean up pictures picked from the phone that no show uses any more.
        val inUse = shows.map { it.image }.toSet()
        old.map { it.image }.filter { it !in inUse && it.startsWith("file://") }.forEach { image ->
            val file = File(image.removePrefix("file://"))
            if (file.parentFile == Pictures.pickedDir(context)) file.delete()
        }
    }

    private fun loadShows(): List<Show> {
        val array = runCatching { JSONArray(prefs.getString("shows", "[]")) }.getOrElse { JSONArray() }
        return (0 until array.length()).map { Show.fromJson(array.getJSONObject(it)) }
    }
}
