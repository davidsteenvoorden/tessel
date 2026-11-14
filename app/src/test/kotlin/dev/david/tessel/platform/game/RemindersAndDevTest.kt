package dev.david.tessel.platform.game

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.platform.net.CoopLobby
import dev.david.tessel.platform.store.Dev
import dev.david.tessel.platform.store.LedgerStore
import dev.david.tessel.platform.store.SettingsStore
import dev.david.tessel.platform.store.SoloSaves
import dev.david.tessel.platform.update.Updater
import dev.david.tessel.ui.TesselApp
import dev.david.tessel.ui.theme.TesselTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** The welcome prompt, the break reminder and the hidden developer page, through the real app shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w914dp-h411dp-land-night-420dpi")
class RemindersAndDevTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var dir: File
    private lateinit var game: GameSurfaceView
    private lateinit var saves: SoloSaves
    private lateinit var store: SettingsStore

    private fun launch(seen: Boolean) {
        dir = Files.createTempDirectory("tessel-reminders").toFile()
        game = GameSurfaceView(compose.activity)
        saves = SoloSaves(dir)
        game.saves = saves
        val ledger = LedgerStore(File(dir, "ledger.dat"))
        game.ledger = ledger
        val lobby = CoopLobby(compose.activity, game)
        store = SettingsStore(compose.activity)
        store.update { it.copy(tutorialSeen = seen) }
        val updater = Updater(compose.activity)
        compose.setContent { TesselTheme { TesselApp(game, lobby, store, ledger, saves, updater, null) } }
    }

    @After
    fun tearDown() {
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    private fun waitForMode(mode: Mode) {
        val until = System.currentTimeMillis() + 5_000
        while (game.mode.value != mode) {
            compose.waitForIdle()
            if (System.currentTimeMillis() > until) throw AssertionError("still ${game.mode.value}, wanted $mode")
            Thread.sleep(10)
        }
    }

    private fun settle(ms: Long = 1500) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    @Test
    fun aNewInstallOffersTheTutorialAndSkipRemembersIt() {
        launch(seen = false)
        settle()
        compose.onNodeWithText("New to Tessel?").assertExists()
        compose.onNodeWithText("Skip").performClick()
        settle()
        assertTrue(store.settings.value.tutorialSeen)
        compose.onNodeWithText("New to Tessel?").assertDoesNotExist()
    }

    @Test
    fun startingTheTutorialPutsThePracticeOnScreen() {
        launch(seen = false)
        settle()
        compose.onNodeWithText("Start Tutorial").performClick()
        waitForMode(Mode.PLAYING)
        settle()
        assertNotNull(game.tutorial.value)
        compose.onNodeWithText("Move").assertExists()
        compose.onNodeWithText("Skip").performClick()
        waitForMode(Mode.HOME)
        assertEquals(null, game.tutorial.value)
        // Practice leaves no run and no save behind.
        assertTrue(saves.saves.value.isEmpty())
    }

    @Test
    fun theBreakCardPausesAndKeepPlayingCarriesOn() {
        launch(seen = true)
        store.update { it.copy(breakEvery = 20) }
        game.startRun(Difficulty.NORMAL)
        waitForMode(Mode.PLAYING)
        game.breakNow()
        waitForMode(Mode.PAUSED)
        settle()
        assertTrue(game.breakDue.value)
        compose.onNodeWithText("Time for a Break").assertExists()
        compose.onNodeWithText("Keep Playing").performClick()
        waitForMode(Mode.PLAYING)
        assertFalse(game.breakDue.value)
    }

    @Test
    fun theBreakCardCanSaveAndQuit() {
        launch(seen = true)
        game.startRun(Difficulty.NORMAL)
        waitForMode(Mode.PLAYING)
        game.breakNow()
        waitForMode(Mode.PAUSED)
        settle()
        compose.onNodeWithText("Save and Quit").performClick()
        waitForMode(Mode.HOME)
        val until = System.currentTimeMillis() + 3_000
        while (saves.saves.value.isEmpty() && System.currentTimeMillis() < until) Thread.sleep(10)
        assertEquals(1, saves.saves.value.size)
    }

    private fun openSettings() {
        settle(1000)
        compose.onNodeWithText("Settings").performClick()
        settle()
    }

    private fun tapVersion(times: Int) {
        val version = compose.onNodeWithText("Version")
        version.performScrollTo()
        repeat(times) {
            version.performClick()
            compose.mainClock.advanceTimeBy(150)
        }
    }

    @Test
    fun sevenTapsOnTheVersionAskForTheCodeAndTheRightCodeUnlocks() {
        launch(seen = true)
        openSettings()
        tapVersion(7)
        settle(1500)
        compose.onNodeWithText("Enter the code.").assertExists()
        for (d in "33155") compose.onNodeWithText(d.toString()).performClick()
        settle()
        assertTrue(store.settings.value.devUnlocked)
        compose.onNodeWithText("Start Stage").assertExists()
    }

    @Test
    fun aWrongCodeClearsAndDoesNotUnlock() {
        launch(seen = true)
        openSettings()
        tapVersion(7)
        settle(1500)
        for (d in "11111") compose.onNodeWithText(d.toString()).performClick()
        settle()
        assertFalse(store.settings.value.devUnlocked)
        compose.onNodeWithText("That is not it.").assertExists()
    }

    @Test
    fun spammingTheVersionDoesNotAskForTheCode() {
        launch(seen = true)
        openSettings()
        tapVersion(12)
        settle(1500)
        compose.onNodeWithText("Enter the code.").assertDoesNotExist()
    }

    @Test
    fun resetPutsTheDeveloperOptionsBackToNormal() {
        launch(seen = true)
        store.update { it.copy(devUnlocked = true, dev = Dev(difficulty = Difficulty.HARD, startStage = 12, invincible = true)) }
        openSettings()
        compose.onNodeWithText("Developer Options").performScrollTo().performClick()
        settle()
        compose.onNodeWithText("Reset Developer Settings").performScrollTo().performClick()
        settle()
        assertEquals(Dev(), store.settings.value.dev)
        assertTrue("still unlocked", store.settings.value.devUnlocked)
    }

    @Test
    fun aDeveloperRunIsNotSavedAndStartsWhereAsked() {
        launch(seen = true)
        store.update { it.copy(devUnlocked = true, dev = Dev(startStage = 6, startCoins = 500, invincible = true)) }
        settle(300)
        game.startRun(Difficulty.NORMAL)
        waitForMode(Mode.PLAYING)
        Thread.sleep(200)
        assertTrue(saves.saves.value.isEmpty())
    }
}
