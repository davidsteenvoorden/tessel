package dev.david.tessel.platform.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.david.tessel.core.content.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun breakRemindersAndTheTutorialFlagComeBackAfterARestart() {
        val first = SettingsStore(context)
        assertEquals(0, first.settings.value.breakEvery)
        assertFalse(first.settings.value.tutorialSeen)
        first.update { it.copy(breakEvery = 45, tutorialSeen = true) }
        val again = SettingsStore(context)
        assertEquals(45, again.settings.value.breakEvery)
        assertTrue(again.settings.value.tutorialSeen)
    }

    @Test
    fun developerOptionsComeBackAndResetClearsThem() {
        val first = SettingsStore(context)
        val dev = Dev(difficulty = Difficulty.IMPOSSIBLE, startStage = 20, startCoins = 5_000, invincible = true, noSpawns = true, speed = 2f)
        first.update { it.copy(devUnlocked = true, dev = dev) }
        val again = SettingsStore(context)
        assertEquals(dev, again.settings.value.dev)
        assertTrue(again.settings.value.devUnlocked)
        assertTrue(dev.active)
        again.update { it.copy(dev = Dev()) }
        assertFalse(SettingsStore(context).settings.value.dev.active)
        assertTrue("reset leaves the page unlocked", SettingsStore(context).settings.value.devUnlocked)
    }
}
