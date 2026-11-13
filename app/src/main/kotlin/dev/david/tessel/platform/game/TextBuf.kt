package dev.david.tessel.platform.game

/** Builds short readouts in place. String formatting on every frame would feed the garbage collector. */
class TextBuf(capacity: Int = 96) {
    val chars = CharArray(capacity)
    var length = 0
        private set

    fun clear(): TextBuf {
        length = 0
        return this
    }

    fun put(text: String): TextBuf {
        val n = minOf(text.length, chars.size - length)
        text.toCharArray(chars, length, 0, n)
        length += n
        return this
    }

    fun put(value: Int): TextBuf {
        var v = value
        if (v < 0) {
            put('-')
            v = -v
        }
        var divisor = 1
        while (v / divisor >= 10) divisor *= 10
        while (divisor > 0) {
            put('0' + v / divisor % 10)
            divisor /= 10
        }
        return this
    }

    /** One decimal place, rounded. */
    fun putTenths(value: Float): TextBuf {
        val tenths = Math.round(value * 10f)
        if (tenths < 0) put('-')
        val magnitude = if (tenths < 0) -tenths else tenths
        put(magnitude / 10)
        put('.')
        put('0' + magnitude % 10)
        return this
    }

    /** Minutes and seconds, like 4:07. */
    fun putClock(totalSeconds: Int): TextBuf {
        val s = totalSeconds % 60
        put(totalSeconds / 60).put(':')
        if (s < 10) put('0')
        return put(s)
    }

    fun put(c: Char): TextBuf {
        if (length < chars.size) chars[length++] = c
        return this
    }
}
