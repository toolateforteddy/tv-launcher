package com.toolateforteddy.kidremote.atv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Just enough protobuf to speak the Android TV remote protocol: a handful of small
 * messages with varint, string, bytes and nested message fields. Avoids pulling in
 * the protobuf runtime and a code generator for about a dozen fields.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun int(field: Int, value: Long): ProtoWriter = apply {
        writeVarint(out, (field.toLong() shl 3) or 0)
        writeVarint(out, value)
    }

    fun int(field: Int, value: Int): ProtoWriter = int(field, value.toLong())

    fun bytes(field: Int, value: ByteArray): ProtoWriter = apply {
        writeVarint(out, (field.toLong() shl 3) or 2)
        writeVarint(out, value.size.toLong())
        out.write(value)
    }

    fun string(field: Int, value: String): ProtoWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, build: ProtoWriter.() -> Unit): ProtoWriter =
        bytes(field, ProtoWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()
}

/** A parsed message: field number to its values, in order. Varints are Long, length-delimited fields ByteArray. */
class ProtoMessage private constructor(private val fields: Map<Int, List<Any>>) {

    fun has(field: Int) = fields.containsKey(field)

    fun long(field: Int, default: Long = 0): Long = fields[field]?.lastOrNull() as? Long ?: default

    fun int(field: Int, default: Int = 0): Int = long(field, default.toLong()).toInt()

    fun bytes(field: Int): ByteArray? = fields[field]?.lastOrNull() as? ByteArray

    fun string(field: Int): String? = bytes(field)?.toString(Charsets.UTF_8)

    fun message(field: Int): ProtoMessage? = bytes(field)?.let { parse(it) }

    fun messages(field: Int): List<ProtoMessage> =
        fields[field].orEmpty().filterIsInstance<ByteArray>().map { parse(it) }

    override fun toString(): String = fields.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
        "$k=" + v.joinToString(",") { if (it is ByteArray) "[${it.size} bytes]" else it.toString() }
    }

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val fields = LinkedHashMap<Int, MutableList<Any>>()
            var pos = 0
            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    if (pos >= data.size) throw IOException("Truncated varint")
                    val b = data[pos++].toInt() and 0xff
                    result = result or ((b and 0x7f).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                    if (shift > 63) throw IOException("Varint too long")
                }
            }
            while (pos < data.size) {
                val key = varint()
                val field = (key ushr 3).toInt()
                val value: Any? = when ((key and 7).toInt()) {
                    0 -> varint()
                    1 -> { pos += 8; null }
                    2 -> {
                        val len = varint().toInt()
                        if (len < 0 || pos + len > data.size) throw IOException("Truncated field $field")
                        data.copyOfRange(pos, pos + len).also { pos += len }
                    }
                    5 -> { pos += 4; null }
                    else -> throw IOException("Unsupported wire type in field $field")
                }
                if (value != null) fields.getOrPut(field) { mutableListOf() }.add(value)
            }
            return ProtoMessage(fields)
        }
    }
}

internal fun writeVarint(out: OutputStream, value: Long) {
    var v = value
    while (true) {
        if (v and 0x7fL.inv() == 0L) {
            out.write(v.toInt())
            return
        }
        out.write(((v and 0x7f) or 0x80).toInt())
        v = v ushr 7
    }
}

/** Both ports frame each message as a varint length followed by the message bytes. */
internal fun writeFramed(out: OutputStream, message: ByteArray) {
    val buffer = ByteArrayOutputStream(message.size + 5)
    writeVarint(buffer, message.size.toLong())
    buffer.write(message)
    out.write(buffer.toByteArray())
    out.flush()
}

internal fun readFramed(input: InputStream): ProtoMessage {
    var length = 0L
    var shift = 0
    while (true) {
        val b = input.read()
        if (b < 0) throw EOFException("The TV closed the connection")
        length = length or ((b and 0x7f).toLong() shl shift)
        if (b and 0x80 == 0) break
        shift += 7
        if (shift > 28) throw IOException("Bad message length")
    }
    if (length > 1_000_000) throw IOException("Message too large: $length")
    val data = ByteArray(length.toInt())
    var read = 0
    while (read < data.size) {
        val n = input.read(data, read, data.size - read)
        if (n < 0) throw EOFException("The TV closed the connection")
        read += n
    }
    return ProtoMessage.parse(data)
}
