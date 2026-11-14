package dev.david.tessel.core.audio

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Every sound in the game is made here from sine sweeps and a little noise, so
 * there are no audio files to license. Each one is a short burst of 16-bit
 * mono samples with a quick fade in and a falling envelope.
 */
object Synth {
    const val RATE = 22050

    enum class Sound { SHOT, HIT, KILL, COIN, HURT, DOWN, BOSS, CLEAR, BUY, SURGE }

    fun make(sound: Sound): ShortArray = when (sound) {
        Sound.SHOT -> tone(0.035f, 760f, 430f, 0.22f, noise = 0.05f, decay = 7f)
        Sound.HIT -> tone(0.025f, 260f, 200f, 0.2f, noise = 0.5f, decay = 9f)
        Sound.KILL -> tone(0.11f, 540f, 170f, 0.3f, noise = 0.25f, decay = 5f)
        Sound.COIN -> join(tone(0.045f, 880f, 880f, 0.22f, decay = 4f), tone(0.07f, 1320f, 1320f, 0.22f, decay = 5f))
        Sound.HURT -> tone(0.16f, 170f, 90f, 0.4f, noise = 0.3f, decay = 3.5f, square = true)
        Sound.DOWN -> tone(0.45f, 320f, 55f, 0.45f, noise = 0.15f, decay = 2.5f, square = true)
        Sound.BOSS -> tone(0.7f, 90f, 120f, 0.5f, noise = 0.08f, decay = 1.2f, square = true)
        Sound.CLEAR -> join(tone(0.09f, 520f, 520f, 0.3f), tone(0.09f, 660f, 660f, 0.3f), tone(0.16f, 780f, 780f, 0.3f, decay = 4f))
        Sound.BUY -> join(tone(0.04f, 1000f, 1000f, 0.25f, decay = 4f), tone(0.06f, 1500f, 1500f, 0.25f, decay = 5f))
        Sound.SURGE -> join(tone(0.06f, 600f, 900f, 0.28f), tone(0.1f, 900f, 1400f, 0.28f, decay = 4f))
    }

    /** A sweep from [from] to [to] hertz over [seconds], with an optional share of noise. */
    fun tone(
        seconds: Float,
        from: Float,
        to: Float,
        volume: Float,
        noise: Float = 0f,
        decay: Float = 6f,
        square: Boolean = false,
    ): ShortArray {
        val n = (seconds * RATE).toInt()
        val out = ShortArray(n)
        var phase = 0.0
        var seed = 12345
        for (i in 0 until n) {
            val t = i / n.toFloat()
            val freq = from + (to - from) * t
            phase += 2.0 * PI * freq / RATE
            var s = sin(phase).toFloat()
            if (square) s = if (s >= 0f) 0.6f else -0.6f
            seed = seed * 1103515245 + 12345
            val white = ((seed ushr 16) and 0x7FFF) / 16384f - 1f
            val mixed = s * (1f - noise) + white * noise
            val fade = minOf(1f, i / (RATE * 0.002f))
            val envelope = exp(-decay * t) * fade
            out[i] = (mixed * envelope * volume * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    fun join(vararg parts: ShortArray): ShortArray {
        val out = ShortArray(parts.sumOf { it.size })
        var at = 0
        for (p in parts) {
            p.copyInto(out, at)
            at += p.size
        }
        return out
    }

    /** The samples as a mono 16-bit WAV file. */
    fun wav(samples: ShortArray): ByteArray {
        val data = samples.size * 2
        val out = ByteArrayOutputStream(44 + data)
        fun int(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
        fun short(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
        out.write("RIFF".toByteArray())
        int(36 + data)
        out.write("WAVEfmt ".toByteArray())
        int(16)
        short(1)
        short(1)
        int(RATE)
        int(RATE * 2)
        short(2)
        short(16)
        out.write("data".toByteArray())
        int(data)
        for (s in samples) short(s.toInt())
        return out.toByteArray()
    }
}
