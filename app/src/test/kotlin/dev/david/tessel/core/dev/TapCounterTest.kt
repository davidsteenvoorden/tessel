package dev.david.tessel.core.dev

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapCounterTest {
    private fun taps(c: TapCounter, n: Int) = repeat(n) { c.tap() }

    @Test
    fun sevenTapsThenQuietOpensIt() {
        val c = TapCounter()
        taps(c, 7)
        assertTrue(c.settle())
        assertFalse("only once", c.settle())
    }

    @Test
    fun moreThanSevenDoesNotOpenIt() {
        val c = TapCounter()
        taps(c, 8)
        assertFalse(c.settle())
    }

    @Test
    fun fewerThanSevenDoesNotOpenIt() {
        val c = TapCounter()
        taps(c, 6)
        assertFalse(c.settle())
    }

    @Test
    fun spammingPastSevenAndSettlingStartsOver() {
        val c = TapCounter()
        taps(c, 12)
        assertFalse(c.settle())
        taps(c, 7)
        assertTrue(c.settle())
    }

    @Test
    fun aPauseInTheMiddleSplitsTheRun() {
        val c = TapCounter()
        taps(c, 4)
        assertFalse("a second of quiet ended the first run", c.settle())
        taps(c, 3)
        assertFalse("three more are three, not seven in all", c.settle())
    }

    @Test
    fun theCodeIsFiveDigits() {
        assertTrue(DevAccess.CODE.length == DevAccess.CODE_LENGTH)
    }
}
