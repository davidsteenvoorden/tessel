package dev.david.tessel.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SynthTest {
    @Test
    fun everySoundHasSamplesWithinRangeAndIsNotSilent() {
        for (sound in Synth.Sound.entries) {
            val s = Synth.make(sound)
            assertTrue("$sound has samples", s.size > Synth.RATE / 50)
            assertTrue("$sound is audible", s.any { kotlin.math.abs(it.toInt()) > 500 })
            assertTrue("$sound is not clipped hard", s.all { kotlin.math.abs(it.toInt()) < 32000 })
        }
    }

    @Test
    fun theWavHasAProperHeaderAndSize() {
        val samples = Synth.make(Synth.Sound.COIN)
        val wav = Synth.wav(samples)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals(44 + samples.size * 2, wav.size)
    }
}
