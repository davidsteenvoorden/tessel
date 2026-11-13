package dev.david.tessel.core.rng

private val GOLDEN = 0x9E3779B97F4A7C15uL.toLong()
private val MIX1 = 0xBF58476D1CE4E5B9uL.toLong()
private val MIX2 = 0x94D049BB133111EBuL.toLong()

/** SplitMix64. The sim draws only from its own seeded instance, so a seed replays a run exactly. */
class Rng(seed: Long) {
    private var state = seed

    fun seed(value: Long) {
        state = value
    }

    fun nextLong(): Long {
        state += GOLDEN
        var z = state
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    /** Uniform in [0, 1). */
    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() / (1 shl 24)

    fun range(lo: Float, hi: Float) = lo + (hi - lo) * nextFloat()

    /** Uniform in lo..hi, both ends included. */
    fun int(lo: Int, hi: Int) = lo + ((nextLong() ushr 33) % (hi - lo + 1)).toInt()

    fun sign() = if (nextLong() < 0) -1f else 1f
}
