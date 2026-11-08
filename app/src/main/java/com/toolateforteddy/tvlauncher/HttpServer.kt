package com.toolateforteddy.tvlauncher

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/** Just enough HTTP/1.1 to serve the phone remote on the home network. */
class HttpServer(private val port: Int, private val handler: (Request) -> Response) {

    class Request(val method: String, val path: String, val query: Map<String, String>, val body: ByteArray) {
        val text: String get() = String(body, Charsets.UTF_8)
    }

    class Response(val status: Int, val contentType: String, val body: ByteArray) {
        companion object {
            fun text(status: Int, text: String) =
                Response(status, "text/plain; charset=utf-8", text.toByteArray())

            fun json(text: String) = Response(200, "application/json; charset=utf-8", text.toByteArray())
        }
    }

    private val workers = Executors.newFixedThreadPool(4)
    @Volatile private var socket: ServerSocket? = null

    fun start() {
        val server = ServerSocket().apply {
            reuseAddress = true
            bind(java.net.InetSocketAddress(port))
        }
        socket = server
        Thread({
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (e: java.io.IOException) {
                    break
                }
                workers.execute { serve(client) }
            }
        }, "remote-http").start()
    }

    fun stop() {
        socket?.close()
        workers.shutdownNow()
    }

    private fun serve(client: Socket) {
        client.use { serveOn(it) }
    }

    private fun serveOn(conn: Socket) {
        try {
            conn.soTimeout = 10_000
            val input = conn.getInputStream()
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            var length = 0
            while (true) {
                val line = readLine(input) ?: return
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0 && line.substring(0, colon).trim().equals("Content-Length", ignoreCase = true)) {
                    length = line.substring(colon + 1).trim().toIntOrNull() ?: 0
                }
            }
            if (length > MAX_BODY) return
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(body, read, length - read)
                if (n < 0) return
                read += n
            }
            val target = parts[1]
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "").split('&').filter { q -> q.contains('=') }
                .associate { q -> decode(q.substringBefore('=')) to decode(q.substringAfter('=')) }

            val response = try {
                handler(Request(parts[0], path, query, body))
            } catch (e: Exception) {
                Response.text(500, e.toString())
            }
            val out = conn.getOutputStream()
            out.write(
                ("HTTP/1.1 ${response.status} OK\r\n" +
                    "Content-Type: ${response.contentType}\r\n" +
                    "Content-Length: ${response.body.size}\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Connection: close\r\n\r\n").toByteArray()
            )
            out.write(response.body)
            out.flush()
        } catch (e: java.io.IOException) {
            // Client went away; nothing to do.
        }
    }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buffer.size() == 0) null else buffer.toString("UTF-8")
            if (b == '\n'.code) return buffer.toString("UTF-8").trimEnd('\r')
            if (buffer.size() > 8192) return null
            buffer.write(b)
        }
    }

    private fun decode(s: String) = URLDecoder.decode(s, "UTF-8")

    private companion object {
        const val MAX_BODY = 256 * 1024
    }
}
