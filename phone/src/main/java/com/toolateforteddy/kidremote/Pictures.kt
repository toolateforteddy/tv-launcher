package com.toolateforteddy.kidremote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/** Show pictures: downloaded once and kept on the phone, so the kid screen works even when slow. */
object Pictures {
    private val memory = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun pickedDir(context: Context) = File(context.filesDir, "pictures").apply { mkdirs() }

    fun cached(src: String): Bitmap? = memory.get(src)

    /** Blocking. Returns null if the picture can't be had. */
    fun load(context: Context, src: String): Bitmap? {
        if (src.isBlank()) return null
        memory.get(src)?.let { return it }
        val file = when {
            src.startsWith("file://") -> File(src.removePrefix("file://"))
            src.startsWith("http://") || src.startsWith("https://") -> download(context, src)
            else -> null
        } ?: return null
        val bitmap = decode(file, MAX_SIDE) ?: return null
        memory.put(src, bitmap)
        return bitmap
    }

    /** Copies a picture chosen from the phone's gallery into the app, shrunk. Returns its file:// address. */
    fun savePicked(context: Context, uri: Uri): String? {
        val temp = File(context.cacheDir, "picked-${UUID.randomUUID()}")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                ?: return null
            val bitmap = decode(temp, MAX_SIDE) ?: return null
            val out = File(pickedDir(context), "${UUID.randomUUID()}.jpg")
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            return "file://${out.absolutePath}"
        } finally {
            temp.delete()
        }
    }

    private fun download(context: Context, src: String): File? {
        val dir = File(context.cacheDir, "pictures").apply { mkdirs() }
        val name = MessageDigest.getInstance("SHA-1").digest(src.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(dir, name)
        if (file.length() > 0) return file
        return runCatching {
            val connection = URL(src).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 8000
                connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", USER_AGENT)
                val tmp = File(dir, "$name.part")
                connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(file)
                file
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun decode(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /**
     * Reads a page's preview title and picture (the ones messaging apps show for a link).
     * Blocking. Returns (name, picture), either of which may be null.
     */
    fun lookup(link: String): Pair<String?, String?> {
        val connection = URL(link).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
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
            return meta(html, "og:title") to meta(html, "og:image")
        } finally {
            connection.disconnect()
        }
    }

    private fun meta(html: String, property: String): String? {
        val tag = Regex("<meta[^>]+(?:property|name)=[\"']$property[\"'][^>]*>", RegexOption.IGNORE_CASE)
            .find(html)?.value ?: return null
        val content = Regex("content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
        return content?.replace("&amp;", "&")?.replace("&#39;", "'")?.replace("&quot;", "\"")?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private const val MAX_SIDE = 640
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) KidRemote"
}
