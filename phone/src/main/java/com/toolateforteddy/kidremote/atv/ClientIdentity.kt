package com.toolateforteddy.kidremote.atv

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.SSLContext
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager

/**
 * The phone's identity towards the TV: an RSA key and a self-signed certificate.
 * Pairing teaches the TV to trust this certificate, so it must be kept and reused.
 */
class ClientIdentity(val privateKey: PrivateKey, val certificate: X509Certificate) {

    /**
     * TLS for both TV ports. The TV's own certificate is self-signed, so it can't be checked
     * against a CA. Pairing binds both certificates together through the PIN instead.
     */
    fun sslContext(): SSLContext {
        val keyManager = object : X509KeyManager {
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
            override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
            override fun getCertificateChain(alias: String?) = arrayOf(certificate)
            override fun getPrivateKey(alias: String?) = privateKey
        }
        val trustTv = @Suppress("CustomX509TrustManager") object : X509TrustManager {
            @Suppress("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            @Suppress("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply { init(arrayOf(keyManager), arrayOf(trustTv), SecureRandom()) }
    }

    /** "key|cert", both base64, for saving in app storage. */
    fun encode(): String {
        val b64 = Base64.getEncoder()
        return b64.encodeToString(privateKey.encoded) + "|" + b64.encodeToString(certificate.encoded)
    }

    companion object {
        private const val ALIAS = "kidremote"

        fun decode(saved: String): ClientIdentity {
            val (key, cert) = saved.split("|")
            val b64 = Base64.getDecoder()
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(b64.decode(key)))
            return ClientIdentity(privateKey, parseCertificate(b64.decode(cert)))
        }

        fun generate(commonName: String = "Kid Remote"): ClientIdentity {
            val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val now = System.currentTimeMillis()
            val notBefore = Date(now - 24L * 3600 * 1000)
            val notAfter = Date(now + 20L * 365 * 24 * 3600 * 1000)
            val name = der(0x30, der(0x31, der(0x30, oid("2.5.4.3") + der(0x0c, commonName.toByteArray()))))
            val sha256WithRsa = der(0x30, oid("1.2.840.113549.1.1.11") + byteArrayOf(0x05, 0x00))
            // Basic constraints CA:true, path length 0, as the TV's own remote libraries use.
            val basicConstraints = der(
                0x30,
                oid("2.5.29.19") + der(0x04, der(0x30, byteArrayOf(0x01, 0x01, 0xff.toByte(), 0x02, 0x01, 0x00))),
            )
            // Subject alternative name, also as those libraries do.
            val subjectAltName = der(
                0x30,
                oid("2.5.29.17") + der(0x04, der(0x30, der(0x82, commonName.toByteArray()))),
            )
            val serial = BigInteger(63, SecureRandom()).add(BigInteger.ONE)
            val tbs = der(
                0x30,
                der(0xa0, der(0x02, byteArrayOf(2))) +
                    der(0x02, serial.toByteArray()) +
                    sha256WithRsa +
                    name +
                    der(0x30, time(notBefore) + time(notAfter)) +
                    name +
                    keyPair.public.encoded +
                    der(0xa3, der(0x30, basicConstraints + subjectAltName)),
            )
            val signature = Signature.getInstance("SHA256withRSA").run {
                initSign(keyPair.private)
                update(tbs)
                sign()
            }
            val cert = der(0x30, tbs + sha256WithRsa + der(0x03, byteArrayOf(0) + signature))
            return ClientIdentity(keyPair.private, parseCertificate(cert))
        }

        private fun parseCertificate(der: ByteArray) =
            CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate

        private fun der(tag: Int, content: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(tag)
            val len = content.size
            when {
                len < 0x80 -> out.write(len)
                len < 0x100 -> { out.write(0x81); out.write(len) }
                len < 0x10000 -> { out.write(0x82); out.write(len shr 8); out.write(len and 0xff) }
                else -> { out.write(0x83); out.write(len shr 16); out.write((len shr 8) and 0xff); out.write(len and 0xff) }
            }
            out.write(content)
            return out.toByteArray()
        }

        private fun oid(dotted: String): ByteArray {
            val parts = dotted.split(".").map { it.toLong() }
            val out = ByteArrayOutputStream()
            out.write((parts[0] * 40 + parts[1]).toInt())
            for (part in parts.drop(2)) {
                val groups = mutableListOf<Int>()
                var v = part
                do {
                    groups.add((v and 0x7f).toInt())
                    v = v shr 7
                } while (v > 0)
                for (i in groups.indices.reversed()) out.write(groups[i] or if (i > 0) 0x80 else 0)
            }
            return der(0x06, out.toByteArray())
        }

        private fun time(date: Date): ByteArray {
            val format = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            return der(0x17, format.format(date).toByteArray())
        }
    }
}
