package dev.david.tessel.ui

import dev.david.tessel.ui.screens.ago
import dev.david.tessel.ui.screens.grouped
import dev.david.tessel.ui.screens.playtime
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun timeSinceIsInTheFewestWords() {
        val now = 10_000_000_000L
        assertEquals("just now", ago(now - 20_000, now))
        assertEquals("5 min ago", ago(now - 5 * 60_000, now))
        assertEquals("3 h ago", ago(now - 3 * 3_600_000L, now))
        assertEquals("yesterday", ago(now - 30 * 3_600_000L, now))
        assertEquals("4 days ago", ago(now - 4 * 86_400_000L, now))
        // A clock that moved back does not say something odd.
        assertEquals("just now", ago(now + 5_000, now))
    }

    @Test
    fun timePlayedFitsOnOneLine() {
        assertEquals("45 s", playtime(45))
        assertEquals("14 min", playtime(14 * 60 + 20))
        assertEquals("2 h 14 min", playtime(2 * 3_600 + 14 * 60))
    }

    @Test
    fun bigNumbersGetCommas() {
        assertEquals("0", grouped(0))
        assertEquals("999", grouped(999))
        assertEquals("12,408", grouped(12_408))
        assertEquals("1,234,567", grouped(1_234_567))
    }
}
