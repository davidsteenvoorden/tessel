package dev.david.tessel.core.rng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RngTest {
    @Test
    fun sameSeedSameSequence() {
        val a = Rng(42)
        val b = Rng(42)
        repeat(100) { assertEquals(a.nextLong(), b.nextLong()) }
    }

    @Test
    fun differentSeedsDiverge() {
        assertNotEquals(Rng(1).nextLong(), Rng(2).nextLong())
    }

    @Test
    fun floatsStayInTheUnitRange() {
        val r = Rng(3)
        repeat(10_000) {
            val f = r.nextFloat()
            assertTrue(f >= 0f && f < 1f)
        }
    }

    @Test
    fun intsCoverBothEnds() {
        val r = Rng(4)
        val seen = BooleanArray(4)
        repeat(1000) {
            val n = r.int(3, 6)
            assertTrue(n in 3..6)
            seen[n - 3] = true
        }
        assertTrue(seen.all { it })
    }

    @Test
    fun reseedingRestartsTheSequence() {
        val r = Rng(9)
        val first = r.nextLong()
        r.nextLong()
        r.seed(9)
        assertEquals(first, r.nextLong())
    }
}
