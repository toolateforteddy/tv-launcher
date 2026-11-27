package com.toolateforteddy.kidremote.atv

import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/** The TV refused our certificate: this phone isn't paired (any more). */
class NotPairedException(cause: Throwable? = null) : IOException("This phone isn't paired with the TV", cause)

/**
 * A live command connection to the TV (port 6466). The TV drives the handshake and then
 * pings every few seconds; a background thread answers so the connection stays open.
 */
class RemoteSession(
    private val host: String,
    private val identity: ClientIdentity,
    private val listener: Listener,
    private val port: Int = REMOTE_PORT,
) : Closeable {

    interface Listener {
        /** The TV is ready for commands. [screenOn] is false when it's in standby. */
        fun onReady(screenOn: Boolean) {}
        fun onClosed(error: Throwable?) {}
    }

    private val ready = CountDownLatch(1)
    @Volatile private var socket: SSLSocket? = null
    @Volatile private var closed = false
    @Volatile var screenOn = false
        private set
    @Volatile var tvName: String = ""
        private set

    /** Connects and waits until the TV says it's ready. */
    fun connect() {
        val s = identity.sslContext().socketFactory.createSocket() as SSLSocket
        socket = s
        try {
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            s.soTimeout = READ_TIMEOUT_MS
        } catch (e: IOException) {
            close()
            throw e
        }
        try {
            s.startHandshake()
        } catch (e: IOException) {
            // The TV is there but turned down our certificate. Depending on timing this
            // shows up as a TLS alert or as a reset connection.
            close()
            throw if (e is SSLException || e is java.net.SocketException) NotPairedException(e) else e
        }
        val reader = Thread({ readLoop(s) }, "tv-remote-reader").apply { isDaemon = true }
        reader.start()
        if (!ready.await(READY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            val wasClosed = closed
            close()
            // An unpaired client often gets through TLS and is then hung up on right away.
            throw if (wasClosed) NotPairedException() else IOException("The TV didn't answer")
        }
        if (closed) throw NotPairedException()
    }

    val isOpen get() = !closed && socket != null

    fun sendKey(keyCode: Int, direction: Int = DIRECTION_SHORT) =
        send { message(KEY_INJECT) { int(1, keyCode); int(2, direction) } }

    /** Opens a link on the TV, such as a show's page in Netflix or Prime Video. */
    fun openLink(link: String) = send { message(APP_LINK_LAUNCH) { string(1, link) } }

    override fun close() {
        closed = true
        runCatching { socket?.close() }
        ready.countDown()
    }

    private fun send(build: ProtoWriter.() -> Unit) {
        val s = socket ?: throw IOException("Not connected")
        if (closed) throw IOException("Not connected")
        val bytes = ProtoWriter().apply(build).toByteArray()
        synchronized(this) { writeFramed(s.outputStream, bytes) }
    }

    private fun readLoop(s: SSLSocket) {
        var error: Throwable? = null
        try {
            val input = s.inputStream
            while (!closed) {
                val msg = readFramed(input)
                when {
                    msg.has(CONFIGURE) -> {
                        val info = msg.message(CONFIGURE)?.message(2)
                        tvName = listOfNotNull(info?.string(2), info?.string(1)).joinToString(" ")
                        send {
                            message(CONFIGURE) {
                                int(1, FEATURES)
                                message(2) {
                                    string(1, "Kid Remote")
                                    string(2, "toolateforteddy")
                                    int(3, 1)
                                    string(4, "1")
                                    string(5, "atvremote")
                                    string(6, "1.0.0")
                                }
                            }
                        }
                    }
                    msg.has(SET_ACTIVE) -> send { message(SET_ACTIVE) { int(1, FEATURES) } }
                    msg.has(PING_REQUEST) -> {
                        val value = msg.message(PING_REQUEST)?.int(1) ?: 0
                        send { message(PING_RESPONSE) { int(1, value) } }
                    }
                    msg.has(START) -> {
                        screenOn = msg.message(START)?.long(1) == 1L
                        if (ready.count > 0) {
                            listener.onReady(screenOn)
                            ready.countDown()
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            if (!closed) error = e
        } finally {
            close()
            listener.onClosed(error)
        }
    }

    companion object {
        const val REMOTE_PORT = 6466
        private const val CONNECT_TIMEOUT_MS = 5000
        // The TV pings every ~5 s, so a long silence means the connection is gone.
        private const val READ_TIMEOUT_MS = 20000
        private const val READY_TIMEOUT_MS = 8000L

        // RemoteMessage fields.
        private const val CONFIGURE = 1
        private const val SET_ACTIVE = 2
        private const val PING_REQUEST = 8
        private const val PING_RESPONSE = 9
        private const val KEY_INJECT = 10
        private const val START = 40
        private const val APP_LINK_LAUNCH = 90

        // PING | KEY | POWER | VOLUME | APP_LINK
        private const val FEATURES = 1 or 2 or 32 or 64 or 512

        const val DIRECTION_SHORT = 3

        const val KEY_HOME = 3
        const val KEY_BACK = 4
        const val KEY_DPAD_UP = 19
        const val KEY_DPAD_DOWN = 20
        const val KEY_DPAD_LEFT = 21
        const val KEY_DPAD_RIGHT = 22
        const val KEY_DPAD_CENTER = 23
        const val KEY_VOLUME_UP = 24
        const val KEY_VOLUME_DOWN = 25
        const val KEY_POWER = 26
        const val KEY_PLAY_PAUSE = 85
    }
}
