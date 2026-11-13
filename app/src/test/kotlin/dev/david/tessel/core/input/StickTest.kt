package dev.david.tessel.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickTest {
    private fun stick() = Stick().apply { radius = 100f }

    @Test
    fun restsUntilTheThumbLeavesTheDeadZone() {
        val s = stick()
        s.press(3, 500f, 500f)
        s.drag(510f, 500f)
        assertTrue(s.held)
        assertFalse(s.engaged)
        assertEquals(0f, s.x, 0f)
    }

    @Test
    fun outputGrowsFromZeroAtTheDeadZoneEdge() {
        val s = stick()
        s.press(0, 0f, 0f)
        s.drag(12.5f, 0f)
        assertEquals(0.5f / 73f, s.x, 1e-4f)

        // Halfway between the dead zone (12) and full output (85).
        s.drag(48.5f, 0f)
        assertEquals(0.5f, s.x, 1e-4f)
        assertEquals(0f, s.y, 0f)
    }

    @Test
    fun fullOutputBeforeTheRim() {
        val s = stick()
        s.press(0, 0f, 0f)
        s.drag(0f, -90f)
        assertEquals(-1f, s.y, 1e-4f)
        assertEquals(-90f, s.knobY, 1e-3f)
    }

    @Test
    fun knobStopsAtTheRim() {
        val s = stick()
        s.press(0, 0f, 0f)
        s.drag(130f, 0f)
        assertEquals(100f, s.knobX, 1e-3f)
        assertEquals(0f, s.originX, 0f)
        assertEquals(1f, s.x, 1e-4f)
    }

    @Test
    fun baseTrailsAThumbThatWandersOff() {
        val s = stick()
        s.press(0, 0f, 0f)
        s.drag(300f, 0f)
        assertEquals(140f, s.originX, 1e-3f)
        assertEquals(240f, s.knobX, 1e-3f)

        // Coming back a little now reads as a real change of direction.
        s.drag(100f, 0f)
        assertEquals(-0.3836f, s.x, 1e-3f)
    }

    @Test
    fun diagonalKeepsUnitLength() {
        val s = stick()
        s.press(0, 0f, 0f)
        s.drag(200f, 200f)
        assertEquals(1.0, Math.hypot(s.x.toDouble(), s.y.toDouble()), 1e-4)
    }

    @Test
    fun releaseStopsOutputButKeepsDirection() {
        val s = stick()
        s.press(7, 0f, 0f)
        s.drag(0f, 60f)
        s.release()
        assertFalse(s.held)
        assertFalse(s.engaged)
        assertEquals(0f, s.dirX, 1e-4f)
        assertEquals(1f, s.dirY, 1e-4f)
    }
}
