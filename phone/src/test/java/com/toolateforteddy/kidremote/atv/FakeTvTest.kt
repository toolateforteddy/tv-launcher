package com.toolateforteddy.kidremote.atv

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

/**
 * A stand-in TV that follows the same protocol, written from the TV's side: it makes up
 * a code, checks the secret against it, and then only accepts the paired certificate.
 */
class FakeTvTest {
    private val tvIdentity = ClientIdentity.generate("Fake TV")
    @Volatile private var trusted: X509Certificate? = null
    @Volatile private var shownCode: String? = null
    private val received = LinkedBlockingQueue<ProtoMessage>()
    private val servers = mutableListOf<SSLServerSocket>()

    @After fun tearDown() = servers.forEach { it.close() }

    private fun serverContext(): SSLContext {
        val store = KeyStore.getInstance("PKCS12").apply {
            load(null)
            setKeyEntry("tv", tvIdentity.privateKey, CharArray(0), arrayOf(tvIdentity.certificate))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, CharArray(0)) }
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String?) {
                val t = trusted
                if (t != null && !chain[0].encoded.contentEquals(t.encoded)) throw CertificateException("not paired")
                if (t == null && !pairingMode) throw CertificateException("not paired")
            }
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, arrayOf(tm), null) }
    }

    @Volatile private var pairingMode = true

    private fun server(handler: (SSLSocket) -> Unit): Int {
        val ss = serverContext().serverSocketFactory.createServerSocket(0) as SSLServerSocket
        ss.needClientAuth = true
        servers += ss
        thread(isDaemon = true) {
            while (!ss.isClosed) {
                val s = runCatching { ss.accept() as SSLSocket }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { s.use(handler) } }
            }
        }
        return ss.localPort
    }

    private fun reply(s: SSLSocket, build: ProtoWriter.() -> Unit) =
        writeFramed(s.outputStream, ProtoWriter().int(1, 2).int(2, 200).apply(build).toByteArray())

    private fun pairingServer() = server { s ->
        val clientCert = s.session.peerCertificates[0] as X509Certificate
        while (true) {
            val m = readFramed(s.inputStream)
            when {
                m.has(10) -> {
                    assertEquals("atvremote", m.message(10)!!.string(1))
                    reply(s) { message(11) { string(1, "Fake TV") } }
                }
                m.has(20) -> reply(s) {
                    message(20) { message(1) { int(1, 3); int(2, 6) }; int(3, 1) }
                }
                m.has(30) -> {
                    val enc = m.message(30)!!.message(1)!!
                    assertEquals(3, enc.int(1)); assertEquals(6, enc.int(2))
                    shownCode = makeCode(clientCert)
                    reply(s) { message(31) {} }
                }
                m.has(40) -> {
                    val expected = Pairing.secret(clientCert, tvIdentity.certificate, shownCode!!)
                    if (expected != null && m.message(40)!!.bytes(1)!!.contentEquals(expected)) {
                        trusted = clientCert
                        reply(s) { message(41) { bytes(1, expected) } }
                    } else {
                        writeFramed(s.outputStream, ProtoWriter().int(1, 2).int(2, 402).toByteArray())
                    }
                    return@server
                }
            }
        }
    }

    /** The TV picks 4 hex digits; the first 2 digits shown are the hash's first byte. */
    private fun makeCode(client: X509Certificate): String {
        val tail = "%04X".format((0..0xffff).random())
        val digest = MessageDigest.getInstance("SHA-256")
        for (key in listOf(client.publicKey, tvIdentity.certificate.publicKey)) {
            key as RSAPublicKey
            digest.update(key.modulus.toByteArray().dropWhile { it.toInt() == 0 }.toByteArray())
            digest.update(key.publicExponent.toByteArray().dropWhile { it.toInt() == 0 }.toByteArray())
        }
        digest.update(byteArrayOf(tail.substring(0, 2).toInt(16).toByte(), tail.substring(2).toInt(16).toByte()))
        return "%02X".format(digest.digest()[0].toInt() and 0xff) + tail
    }

    private fun remoteServer() = server { s ->
        reply0(s) { message(1) { int(1, 622); message(2) { string(1, "XBR-75X900H"); string(2, "Sony") } } }
        val configure = readFramed(s.inputStream)
        assertTrue(configure.has(1))
        reply0(s) { message(2) {} }
        val active = readFramed(s.inputStream)
        assertEquals(611, active.message(2)!!.int(1))
        reply0(s) { message(40) { int(1, 1) } }
        reply0(s) { message(8) { int(1, 42); int(2, 7) } }
        while (true) received.put(readFramed(s.inputStream))
    }

    private fun reply0(s: SSLSocket, build: ProtoWriter.() -> Unit) =
        writeFramed(s.outputStream, ProtoWriter().apply(build).toByteArray())

    @Test fun identityRoundTrips() {
        val id = ClientIdentity.generate()
        id.certificate.checkValidity()
        id.certificate.verify(id.certificate.publicKey)
        assertEquals(3, id.certificate.version)
        assertEquals(0, id.certificate.basicConstraints)
        val copy = ClientIdentity.decode(id.encode())
        assertArrayEquals(id.certificate.encoded, copy.certificate.encoded)
        assertArrayEquals(id.privateKey.encoded, copy.privateKey.encoded)
    }

    @Test fun pairsThenSendsCommands() {
        val phone = ClientIdentity.generate()
        val pairPort = pairingServer()
        val remotePort = remoteServer()

        Pairing("127.0.0.1", phone, port = pairPort).use { p ->
            p.start()
            val code = shownCode!!
            // A typo in the check digits is caught on the phone, and the TV keeps waiting.
            val typo = (if (code[0] == 'A') "B" else "A") + code.substring(1)
            try { p.finish(typo); fail("typo accepted") } catch (e: PairingException) { assertTrue(e.wrongCode) }
            p.finish(code.lowercase())
        }
        assertNotNull(trusted)
        pairingMode = false

        var readyScreenOn: Boolean? = null
        val session = RemoteSession("127.0.0.1", ClientIdentity.decode(phone.encode()), object : RemoteSession.Listener {
            override fun onReady(screenOn: Boolean) { readyScreenOn = screenOn }
        }, port = remotePort)
        session.connect()
        assertEquals(true, readyScreenOn)
        assertEquals("Sony XBR-75X900H", session.tvName)

        val ping = received.poll(5, TimeUnit.SECONDS)!!
        assertEquals(42, ping.message(9)!!.int(1))

        session.sendKey(RemoteSession.KEY_PLAY_PAUSE)
        val key = received.poll(5, TimeUnit.SECONDS)!!.message(10)!!
        assertEquals(85, key.int(1)); assertEquals(3, key.int(2))

        session.openLink("https://www.netflix.com/title/80018294")
        val link = received.poll(5, TimeUnit.SECONDS)!!.message(90)!!
        assertEquals("https://www.netflix.com/title/80018294", link.string(1))
        session.close()
        assertFalse(session.isOpen)
    }

    @Test fun unpairedPhoneIsTold() {
        pairingMode = false
        val remotePort = remoteServer()
        val stranger = ClientIdentity.generate()
        try {
            RemoteSession("127.0.0.1", stranger, object : RemoteSession.Listener {}, port = remotePort).connect()
            fail("connected without pairing")
        } catch (e: NotPairedException) {
            // expected
        }
    }
}
