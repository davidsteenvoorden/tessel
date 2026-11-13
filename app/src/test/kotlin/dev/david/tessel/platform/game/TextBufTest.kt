package dev.david.tessel.platform.game

import org.junit.Assert.assertEquals
import org.junit.Test

class TextBufTest {
    private fun TextBuf.text() = String(chars, 0, length)

    @Test
    fun writesIntegers() {
        assertEquals("0", TextBuf().put(0).text())
        assertEquals("7", TextBuf().put(7).text())
        assertEquals("1080", TextBuf().put(1080).text())
        assertEquals("-42", TextBuf().put(-42).text())
    }

    @Test
    fun writesOneDecimal() {
        assertEquals("16.7", TextBuf().putTenths(16.666f).text())
        assertEquals("0.0", TextBuf().putTenths(0.04f).text())
        assertEquals("2.0", TextBuf().putTenths(1.96f).text())
    }

    @Test
    fun writesAClock() {
        assertEquals("0:00", TextBuf().putClock(0).text())
        assertEquals("0:07", TextBuf().putClock(7).text())
        assertEquals("4:07", TextBuf().putClock(247).text())
        assertEquals("12:30", TextBuf().putClock(750).text())
    }

    @Test
    fun chainsAndClears() {
        val buf = TextBuf()
        buf.put("fps ").put(60)
        assertEquals("fps 60", buf.text())
        assertEquals("", buf.clear().text())
    }

    @Test
    fun dropsWhatDoesNotFit() {
        assertEquals("abcd", TextBuf(4).put("abcdef").put(9).text())
    }
}
