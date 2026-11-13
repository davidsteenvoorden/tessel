package dev.david.tessel.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun laterVersionsWin() {
        assertTrue(Versions.newer("v0.3", "0.2"))
        assertTrue(Versions.newer("0.10", "0.9"))
        assertTrue(Versions.newer("1.0", "0.9.9"))
        assertTrue(Versions.newer("0.3.1", "0.3"))
    }

    @Test
    fun sameOrOlderDoesNot() {
        assertFalse(Versions.newer("0.3", "0.3"))
        assertFalse(Versions.newer("v0.3.0", "0.3"))
        assertFalse(Versions.newer("0.2", "0.3"))
        assertFalse(Versions.newer("garbage", "0.3"))
    }
}
