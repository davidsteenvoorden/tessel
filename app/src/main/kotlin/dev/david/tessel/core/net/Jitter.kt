package dev.david.tessel.core.net

/**
 * How unevenly messages from another phone arrive. Each message carries the
 * time it was made, on the sender's clock; [add] compares that with when it
 * arrived here. The quickest one seen counts as on time ([offsetNanos]), and
 * every other message is late by how much slower it was.
 *
 * [spreadNanos] is how late the late ones get: the second worst of the last
 * [window] messages, so one freak delay does not count but a habit does.
 * Whatever is drawn from these messages should run that far behind, plus one
 * message gap, so the next message is nearly always already here.
 */
class Jitter(private val window: Int) {
    private val late = LongArray(window)
    private var filled = 0
    private var next = 0
    private var behind = 0
    private var lastArrival = 0L

    /** Arrival time minus made time, for the quickest message lately. [Long.MAX_VALUE] before the first. */
    var offsetNanos = Long.MAX_VALUE
        private set

    var spreadNanos = 0L
        private set

    fun reset() {
        offsetNanos = Long.MAX_VALUE
        filled = 0
        next = 0
        behind = 0
        spreadNanos = 0L
        lastArrival = 0L
    }

    fun add(arrivedNanos: Long, madeNanos: Long) {
        val sample = arrivedNanos - madeNanos
        if (offsetNanos == Long.MAX_VALUE) {
            offsetNanos = sample
            lastArrival = arrivedNanos
            record(0L)
            return
        }
        // Lets "on time" drift upward slowly, so a link that got slower for good is followed.
        val creep = ((arrivedNanos - lastArrival).coerceAtLeast(0L) * CREEP_PER_SECOND / 1_000_000_000L)
        lastArrival = arrivedNanos
        if (sample - offsetNanos > RESYNC_NANOS) {
            // Several far-late messages in a row: the sender's clock jumped (pause, new run). Start over.
            if (++behind >= RESYNC_COUNT) {
                reset()
                offsetNanos = sample
                lastArrival = arrivedNanos
                record(0L)
            }
            return
        }
        behind = 0
        offsetNanos = minOf(sample, offsetNanos + creep)
        record(sample - offsetNanos)
    }

    private fun record(lateness: Long) {
        late[next] = lateness
        next = (next + 1) % window
        if (filled < window) filled++
        var first = 0L
        var second = 0L
        for (i in 0 until filled) {
            val v = late[i]
            if (v > first) {
                second = first
                first = v
            } else if (v > second) {
                second = v
            }
        }
        spreadNanos = second
    }

    companion object {
        /** 7.5 ms per second: quick enough for a link that slows down, slow enough to ignore one quick message. */
        const val CREEP_PER_SECOND = 7_500_000L

        /** A message this much later than the quickest, several in a row, means the sender's clock jumped. */
        const val RESYNC_NANOS = 250_000_000L
        const val RESYNC_COUNT = 5
    }
}
