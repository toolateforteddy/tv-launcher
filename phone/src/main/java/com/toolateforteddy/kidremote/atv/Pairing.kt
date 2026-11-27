package com.toolateforteddy.kidremote.atv

import java.io.Closeable
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

class PairingException(message: String, val wrongCode: Boolean = false) : IOException(message)

/**
 * One-time pairing with the TV (the "polo" protocol on port 6467).
 *
 * [start] makes the TV show a 6-character code. [finish] sends proof that we saw it.
 * After that the TV trusts [identity]'s certificate on the command port for good.
 */
class Pairing(
    private val host: String,
    private val identity: ClientIdentity,
    private val clientName: String = "Kid Remote",
    private val port: Int = PAIRING_PORT,
) : Closeable {

    private var socket: SSLSocket? = null

    /** Connects and walks the handshake until the TV is showing its code. */
    fun start() {
        val s = identity.sslContext().socketFactory.createSocket() as SSLSocket
        socket = s
        s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        s.soTimeout = READ_TIMEOUT_MS
        s.startHandshake()

        send { message(PAIRING_REQUEST) { string(1, "atvremote"); string(2, clientName) } }
        expect(PAIRING_REQUEST_ACK)
        send {
            message(OPTIONS) {
                message(1) { int(1, ENCODING_HEXADECIMAL); int(2, CODE_LENGTH) }
                int(3, ROLE_INPUT)
            }
        }
        expect(OPTIONS)
        send {
            message(CONFIGURATION) {
                message(1) { int(1, ENCODING_HEXADECIMAL); int(2, CODE_LENGTH) }
                int(2, ROLE_INPUT)
            }
        }
        expect(CONFIGURATION_ACK)
    }

    /**
     * Sends the code the TV shows. Throws [PairingException] with wrongCode=true if it
     * can't be right; the TV still shows the code then, so the user can try again.
     */
    fun finish(code: String) {
        val s = socket ?: throw IllegalStateException("start() first")
        val clean = code.trim().uppercase()
        if (clean.length != CODE_LENGTH || clean.any { it !in "0123456789ABCDEF" }) {
            throw PairingException("The code is 6 letters and numbers (0-9, A-F).", wrongCode = true)
        }
        val serverCert = s.session.peerCertificates.first() as X509Certificate
        val secret = secret(identity.certificate, serverCert, clean)
            ?: throw PairingException("That code doesn't match. Check the TV and try again.", wrongCode = true)
        send { message(SECRET) { bytes(1, secret) } }
        expect(SECRET_ACK)
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun send(build: ProtoWriter.() -> Unit) {
        val message = ProtoWriter().int(1, PROTOCOL_VERSION).int(2, STATUS_OK).apply(build).toByteArray()
        writeFramed(socket!!.outputStream, message)
    }

    private fun expect(field: Int): ProtoMessage {
        val reply = readFramed(socket!!.inputStream)
        val status = reply.int(2)
        if (status != STATUS_OK) {
            throw when (status) {
                STATUS_BAD_SECRET -> PairingException("The TV didn't accept that code. Start pairing again.")
                else -> PairingException("The TV stopped pairing (status $status).")
            }
        }
        if (!reply.has(field)) throw PairingException("Unexpected reply from the TV: $reply")
        return reply
    }

    companion object {
        const val PAIRING_PORT = 6467
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 15000

        private const val PROTOCOL_VERSION = 2
        private const val STATUS_OK = 200
        private const val STATUS_BAD_SECRET = 402

        // OuterMessage fields.
        private const val PAIRING_REQUEST = 10
        private const val PAIRING_REQUEST_ACK = 11
        private const val OPTIONS = 20
        private const val CONFIGURATION = 30
        private const val CONFIGURATION_ACK = 31
        private const val SECRET = 40
        private const val SECRET_ACK = 41

        private const val ENCODING_HEXADECIMAL = 3
        private const val ROLE_INPUT = 1
        private const val CODE_LENGTH = 6

        /**
         * SHA-256 of both public keys and the last 4 code characters. The first 2 code
         * characters are a check on the result, so a typo is caught here. Returns null then.
         */
        fun secret(client: X509Certificate, server: X509Certificate, code: String): ByteArray? {
            val clientKey = client.publicKey as RSAPublicKey
            val serverKey = server.publicKey as RSAPublicKey
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(unsigned(clientKey.modulus))
            digest.update(unsigned(clientKey.publicExponent))
            digest.update(unsigned(serverKey.modulus))
            digest.update(unsigned(serverKey.publicExponent))
            digest.update(hex(code.substring(2)))
            val hash = digest.digest()
            return if ((hash[0].toInt() and 0xff) == code.substring(0, 2).toInt(16)) hash else null
        }

        private fun unsigned(n: BigInteger): ByteArray {
            val bytes = n.toByteArray()
            val start = bytes.indexOfFirst { it.toInt() != 0 }.coerceAtLeast(0)
            return bytes.copyOfRange(start, bytes.size)
        }

        private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
