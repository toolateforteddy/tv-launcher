package com.toolateforteddy.tvlauncher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A button on the kid remote: a show and the link that opens it. */
data class Show(val id: String, val name: String, val link: String, val image: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("link", link).put("image", image)

    companion object {
        fun fromJson(o: JSONObject) = Show(
            id = o.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
            name = o.optString("name"),
            link = o.optString("link"),
            image = o.optString("image"),
        )
    }
}

/** The kid remote's shows, kept on the TV so every phone sees the same buttons. */
class ShowStore(context: Context) {
    private val prefs = context.getSharedPreferences("kid_remote", Context.MODE_PRIVATE)

    fun all(): List<Show> {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        return (0 until array.length()).map { Show.fromJson(array.getJSONObject(it)) }
    }

    fun find(id: String) = all().firstOrNull { it.id == id }

    fun save(shows: List<Show>) {
        prefs.edit().putString(KEY, JSONArray(shows.map { it.toJson() }).toString()).apply()
    }

    fun toJson(): String = JSONArray(all().map { it.toJson() }).toString()

    private companion object {
        const val KEY = "shows"
    }
}

object ShowLauncher {
    /** TV apps that should handle each site's links, so Android doesn't offer a browser instead. */
    private val TV_APPS = listOf(
        listOf("youtube.com", "youtu.be") to "com.google.android.youtube.tv",
        listOf("netflix.com") to "com.netflix.ninja",
        listOf("primevideo.com", "amazon.com", "amazon.co.uk") to "com.amazon.amazonvideo.livingroom",
        listOf("disneyplus.com") to "com.disney.disneyplus",
    )

    fun open(context: Context, show: Show): Boolean {
        val uri = Uri.parse(show.link)
        val host = uri.host.orEmpty().removePrefix("www.").removePrefix("m.")
        val pkg = TV_APPS.firstOrNull { (hosts, _) -> hosts.any { host == it || host.endsWith(".$it") } }?.second
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (pkg != null) {
            try {
                context.startActivity(Intent(intent).setPackage(pkg))
                return true
            } catch (e: ActivityNotFoundException) {
                // That app isn't installed or doesn't take this link; let Android pick.
            }
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** Reads a page's preview title and picture (the ones messaging apps show for a link). */
    fun lookup(link: String): JSONObject {
        val result = JSONObject()
        val connection = URL(link).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) TVLauncher")
            connection.setRequestProperty("Accept-Language", "en-US,en")
            val html = connection.inputStream.use { input ->
                val bytes = ByteArray(400_000)
                var total = 0
                while (total < bytes.size) {
                    val n = input.read(bytes, total, bytes.size - total)
                    if (n < 0) break
                    total += n
                }
                String(bytes, 0, total, Charsets.UTF_8)
            }
            meta(html, "og:title")?.let { result.put("name", it) }
            meta(html, "og:image")?.let { result.put("image", it) }
        } finally {
            connection.disconnect()
        }
        return result
    }

    private fun meta(html: String, property: String): String? {
        val tag = Regex("<meta[^>]+(?:property|name)=[\"']$property[\"'][^>]*>", RegexOption.IGNORE_CASE)
            .find(html)?.value ?: return null
        val content = Regex("content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
        return content?.replace("&amp;", "&")?.replace("&#39;", "'")?.replace("&quot;", "\"")
    }
}
