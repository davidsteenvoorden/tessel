package dev.david.tessel.core.net

import dev.david.tessel.core.sim.PlayerInput
import dev.david.tessel.core.sim.TICK_HZ

/**
 * Where another phone said its player was, stamped with that phone's clock,
 * played back at an even pace a little behind the newest report. Reports
 * that arrive in a bunch then move the player smoothly instead of in jumps.
 * How far behind follows how unevenly they arrive ([Jitter]).
 */
internal class Trail {
    val jitter = Jitter(WINDOW)

    private val at = LongArray(SIZE)
    private val x = FloatArray(SIZE)
    private val y = FloatArray(SIZE)
    private var count = 0
    private var head = 0
    private var started = false
    private var lastStamp = 0
    private var clockMillis = 0L
    private var lastPlay = 0L

    /** How far behind the sender's clock the playback runs right now. */
    var bufferNanos = START_NANOS
        private set

    fun reset() {
        jitter.reset()
        count = 0
        started = false
        lastPlay = 0L
        bufferNanos = START_NANOS
    }

    /** A report made at [stamp] (sender's milliseconds) that arrived at [arrivedNanos] (ours). */
    fun add(stamp: Int, px: Float, py: Float, arrivedNanos: Long) {
        // The sender's clock wraps around after a few weeks; differences survive that.
        if (started) clockMillis += stamp - lastStamp else clockMillis = stamp.toLong()
        started = true
        lastStamp = stamp
        val made = clockMillis * 1_000_000L
        if (count > 0) {
            if (made < at[head]) return
            if (made == at[head]) {
                // Two steps in one frame share a time; the later one is where the player ended up.
                x[head] = px
                y[head] = py
                return
            }
        }
        jitter.add(arrivedNanos, made)
        head = (head + 1) % SIZE
        at[head] = made
        x[head] = px
        y[head] = py
        if (count < SIZE) count++
    }

    /** Sets [out]'s position to where the player was a moment ago, blended between reports. */
    fun play(nowNanos: Long, out: PlayerInput) {
        if (count == 0 || jitter.offsetNanos == Long.MAX_VALUE) return
        val target = (STEP_NANOS + jitter.spreadNanos + MARGIN_NANOS).coerceIn(MIN_NANOS, MAX_NANOS)
        val dt = if (lastPlay == 0L) 0L else (nowNanos - lastPlay).coerceIn(0L, 100_000_000L)
        lastPlay = nowNanos
        // Easing toward the target bends time a little rather than jumping the player.
        bufferNanos = if (target > bufferNanos) minOf(target, bufferNanos + dt * GROW_PERCENT / 100)
        else maxOf(target, bufferNanos - dt * SHRINK_PERCENT / 100)

        val t = nowNanos - jitter.offsetNanos - bufferNanos
        var k = head
        var newer = 0
        while (newer < count && at[k] > t) {
            k = (k - 1 + SIZE) % SIZE
            newer++
        }
        when (newer) {
            // Past the newest report: hold it there until the next one comes.
            0 -> {
                out.posX = x[head]
                out.posY = y[head]
            }
            // Older than anything kept: show the oldest.
            count -> {
                val oldest = (head - count + 1 + SIZE) % SIZE
                out.posX = x[oldest]
                out.posY = y[oldest]
            }
            else -> {
                val b = (k + 1) % SIZE
                val f = ((t - at[k]).toFloat() / (at[b] - at[k]).toFloat()).coerceIn(0f, 1f)
                out.posX = x[k] + (x[b] - x[k]) * f
                out.posY = y[k] + (y[b] - y[k]) * f
            }
        }
    }

    private companion object {
        const val SIZE = 64
        const val WINDOW = 120
        const val STEP_NANOS = 1_000_000_000L / TICK_HZ
        const val MARGIN_NANOS = 4_000_000L
        const val MIN_NANOS = 8_000_000L
        const val MAX_NANOS = 150_000_000L
        const val START_NANOS = 50_000_000L
        const val GROW_PERCENT = 15L
        const val SHRINK_PERCENT = 4L
    }
}
