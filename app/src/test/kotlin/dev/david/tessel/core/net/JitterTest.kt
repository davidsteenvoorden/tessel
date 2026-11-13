package dev.david.tessel.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JitterTest {
    private val ms = 1_000_000L
    private val gap = 33 * ms

    @Test
    fun anEvenLinkHasNoSpread() {
        val j = Jitter(64)
        for (k in 0 until 100) j.add(k * gap + 20 * ms, k * gap)
        assertEquals(0L, j.spreadNanos)
        assertEquals(20 * ms, j.offsetNanos)
    }

    @Test
    fun lateMessagesSetTheSpreadButOneFreakDoesNot() {
        val j = Jitter(64)
        for (k in 0 until 64) j.add(k * gap + 10 * ms + (if (k % 8 == 4) 40 * ms else 0L), k * gap)
        assertEquals(40.0, j.spreadNanos / 1e6, 1.0)

        val once = Jitter(64)
        for (k in 0 until 64) once.add(k * gap + 10 * ms + (if (k == 30) 200 * ms else 0L), k * gap)
        assertTrue(once.spreadNanos < 2 * ms)
    }

    @Test
    fun aLinkThatSlowsDownForGoodIsFollowed() {
        val j = Jitter(64)
        var k = 0
        repeat(300) { j.add(k * gap + 10 * ms, k * gap); k++ }
        repeat(400) { j.add(k * gap + 60 * ms, k * gap); k++ }
        assertTrue("spread ${j.spreadNanos / ms} ms", j.spreadNanos < 5 * ms)
    }

    @Test
    fun aClockThatJumpedStartsOver() {
        val j = Jitter(64)
        var t = 0L
        repeat(60) { j.add(t + 10 * ms, t); t += gap }
        // The sender paused for three seconds: its clock stood still while ours ran on.
        val resumed = t + 3000 * ms
        repeat(120) { k -> j.add(resumed + k * gap + 10 * ms, t + k * gap) }
        assertTrue("spread ${j.spreadNanos / ms} ms", j.spreadNanos < 2 * ms)
    }
}
