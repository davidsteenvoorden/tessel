package dev.david.tessel.platform.game

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.sim.Checkpoint
import dev.david.tessel.platform.net.CoopLobby
import dev.david.tessel.platform.store.LedgerStore
import dev.david.tessel.platform.store.SettingsStore
import dev.david.tessel.platform.store.SoloSaves
import dev.david.tessel.platform.update.Updater
import dev.david.tessel.ui.TesselApp
import dev.david.tessel.ui.theme.TesselTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * The home screen with the real app shell on top: what a tap on Play does
 * with runs waiting, and that the dialog it opens can be reached.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w914dp-h411dp-land-night-420dpi")
class PlayDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var dir: File
    private lateinit var game: GameSurfaceView
    private lateinit var saves: SoloSaves

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("tessel-dialog").toFile()
        game = GameSurfaceView(compose.activity)
        saves = SoloSaves(dir)
        game.saves = saves
        val ledger = LedgerStore(File(dir, "ledger.dat"))
        game.ledger = ledger
        val lobby = CoopLobby(compose.activity, game)
        val store = SettingsStore(compose.activity)
        store.update { it.copy(tutorialSeen = true) }
        val updater = Updater(compose.activity)
        compose.setContent { TesselTheme { TesselApp(game, lobby, store, ledger, saves, updater, null) } }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun waitForMode(mode: Mode) {
        val until = System.currentTimeMillis() + 5_000
        while (game.mode.value != mode) {
            compose.waitForIdle()
            if (System.currentTimeMillis() > until) throw AssertionError("still ${game.mode.value}, wanted $mode")
            Thread.sleep(10)
        }
    }

    @Test
    fun withNothingSavedPlayStartsARunAtOnce() {
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Play").performClick()
        waitForMode(Mode.PLAYING)
    }

    @Test
    fun withARunSavedPlayOpensADialogThatNewRunCanBePressedIn() {
        saves.write(1, Checkpoint(Difficulty.NORMAL, 4, 4 * 3900, 1, true, 0))
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Play").performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        // The title says New Run too; the button is the last of them.
        compose.onAllNodesWithText("New Run").onLast().performClick()
        waitForMode(Mode.PLAYING)
        // The new run takes its own slot a moment after it starts.
        val until = System.currentTimeMillis() + 5_000
        while (saves.saves.value.size < 2 && System.currentTimeMillis() < until) Thread.sleep(10)
        assertEquals(2, saves.saves.value.size)
    }

    @Test
    fun aSavedRunCanBeContinuedFromTheDialog() {
        saves.write(1, Checkpoint(Difficulty.NORMAL, 4, 4 * 3900, 1, true, 0))
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Play").performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        compose.onNodeWithText("Continue").performClick()
        waitForMode(Mode.SHOP)
    }
}
