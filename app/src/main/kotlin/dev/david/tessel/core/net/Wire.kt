package dev.david.tessel.core.net

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Message framing shared by every phone in a game. Every message starts with
 * 'T' 'S', a protocol version and a type byte. Bump [VERSION] whenever any
 * layout below changes; mismatched phones then refuse each other politely.
 */
object Wire {
    const val VERSION = 6

    const val HELLO = 1
    const val START = 2
    const val SNAPSHOT = 3
    const val INPUT = 4
    const val ACTION = 5
    const val BYE = 6

    /** Host to everyone in the lobby: who is in it. */
    const val ROSTER = 7

    /** Host to a joining phone, through Nearby: where to reach it directly, and the token to say. */
    const val DIRECT_OFFER = 8

    /**
     * Through Nearby, both ways: from here on my messages come through the
     * direct pipe. Everything sent before it came through Nearby.
     */
    const val DIRECT_ON = 9

    /** Joining phone to host, first thing through the direct pipe. */
    const val DIRECT_HELLO = 10

    /** Host to everyone, through Nearby: the game was saved for later and this session is over. */
    const val SAVED = 11

    // Actions a client asks the host for.
    const val BUY = 1
    const val HEAL = 2
    const val REROLL = 3
    const val READY = 4
    const val PAUSE = 5
    const val RESUME = 6
    const val RESTART = 7

    /** Save the game for later and stop, for everyone. */
    const val SAVE = 8

    /** Positions travel as 16-bit integers in eighths of an arena unit. */
    const val POS_SCALE = 8f

    fun buffer(capacity: Int): ByteBuffer = ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN)

    fun begin(buf: ByteBuffer, type: Int) {
        buf.clear()
        buf.put('T'.code.toByte())
        buf.put('S'.code.toByte())
        buf.put(VERSION.toByte())
        buf.put(type.toByte())
    }

    /** Copies what was written into a fresh array the radio can own while it sends. */
    fun finish(buf: ByteBuffer): ByteArray {
        val out = ByteArray(buf.position())
        buf.flip()
        buf.get(out)
        return out
    }

    /** Message type, or -1 when the bytes are not ours or come from another version. */
    fun typeOf(bytes: ByteArray): Int {
        if (bytes.size < 4 || bytes[0] != 'T'.code.toByte() || bytes[1] != 'S'.code.toByte()) return -1
        if (bytes[2].toInt() != VERSION) return VERSION_MISMATCH
        return bytes[3].toInt()
    }

    const val VERSION_MISMATCH = -2

    fun open(bytes: ByteArray): ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).also { it.position(4) }

    fun putPos(buf: ByteBuffer, v: Float) {
        buf.putShort((v * POS_SCALE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
    }

    fun getPos(buf: ByteBuffer): Float = buf.short / POS_SCALE

    /** A value in -1..1 as a signed byte. */
    fun putUnit(buf: ByteBuffer, v: Float) {
        buf.put((v.coerceIn(-1f, 1f) * 127f).toInt().toByte())
    }

    fun getUnit(buf: ByteBuffer): Float = buf.get() / 127f

    fun putString(buf: ByteBuffer, s: String) {
        val bytes = s.encodeToByteArray()
        val n = minOf(bytes.size, 60)
        buf.put(n.toByte())
        buf.put(bytes, 0, n)
    }

    fun getString(buf: ByteBuffer): String {
        val n = buf.get().toInt() and 0xFF
        val bytes = ByteArray(n)
        buf.get(bytes)
        return bytes.decodeToString()
    }

    fun hello(name: String): ByteArray {
        val buf = buffer(80)
        begin(buf, HELLO)
        putString(buf, name)
        return finish(buf)
    }

    /** A run begins. Tells each phone which player it is and how many there are. */
    fun start(difficulty: Int, you: Int, players: Int): ByteArray {
        val buf = buffer(8)
        begin(buf, START)
        buf.put(difficulty.toByte())
        buf.put(you.toByte())
        buf.put(players.toByte())
        return finish(buf)
    }

    class Start(val difficulty: Int, val you: Int, val players: Int)

    fun readStart(bytes: ByteArray): Start {
        val buf = open(bytes)
        return Start(buf.get().toInt(), buf.get().toInt(), buf.get().toInt())
    }

    fun roster(names: List<String>): ByteArray {
        val buf = buffer(8 + names.size * 64)
        begin(buf, ROSTER)
        buf.put(names.size.toByte())
        for (n in names) putString(buf, n)
        return finish(buf)
    }

    fun readRoster(bytes: ByteArray): List<String> {
        val buf = open(bytes)
        val n = buf.get().toInt().coerceIn(0, 16)
        return List(n) { getString(buf) }
    }

    fun action(kind: Int, arg: Int = 0): ByteArray {
        val buf = buffer(8)
        begin(buf, ACTION)
        buf.put(kind.toByte())
        buf.put(arg.toByte())
        return finish(buf)
    }

    class Offer(val token: Long, val port: Int, val addresses: List<java.net.Inet4Address>)

    fun directOffer(token: Long, port: Int, addresses: List<java.net.Inet4Address>): ByteArray {
        val list = addresses.take(MAX_OFFERED)
        val buf = buffer(16 + list.size * 4)
        begin(buf, DIRECT_OFFER)
        buf.putLong(token)
        buf.putShort(port.toShort())
        buf.put(list.size.toByte())
        for (a in list) buf.put(a.address)
        return finish(buf)
    }

    fun readDirectOffer(bytes: ByteArray): Offer {
        val buf = open(bytes)
        val token = buf.long
        val port = buf.short.toInt() and 0xFFFF
        val n = buf.get().toInt().coerceIn(0, MAX_OFFERED)
        val addresses = List(n) {
            val raw = ByteArray(4)
            buf.get(raw)
            java.net.InetAddress.getByAddress(raw) as java.net.Inet4Address
        }
        return Offer(token, port, addresses)
    }

    fun directOn(token: Long): ByteArray {
        val buf = buffer(12)
        begin(buf, DIRECT_ON)
        buf.putLong(token)
        return finish(buf)
    }

    fun readDirectOn(bytes: ByteArray): Long = open(bytes).long

    class Hello(val token: Long, val seat: Int)

    fun directHello(token: Long, seat: Int): ByteArray {
        val buf = buffer(13)
        begin(buf, DIRECT_HELLO)
        buf.putLong(token)
        buf.put(seat.toByte())
        return finish(buf)
    }

    fun readDirectHello(bytes: ByteArray): Hello {
        val buf = open(bytes)
        return Hello(buf.long, buf.get().toInt())
    }

    private const val MAX_OFFERED = 8

    fun saved(stage: Int): ByteArray {
        val buf = buffer(6)
        begin(buf, SAVED)
        buf.putShort(stage.toShort())
        return finish(buf)
    }

    fun readSaved(bytes: ByteArray): Int = open(bytes).short.toInt()

    fun bye(): ByteArray {
        val buf = buffer(4)
        begin(buf, BYE)
        return finish(buf)
    }
}
