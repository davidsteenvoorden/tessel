package dev.david.tessel.platform.game

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.platform.store.LedgerStore
import dev.david.tessel.platform.store.SoloSaves
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** The menu commands in the order a player gives them, against the real game view and stores. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RunFlowTest {
    private lateinit var dir: File
    private lateinit var game: GameSurfaceView
    private lateinit var saves: SoloSaves

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("tessel-flow").toFile()
        val context = ApplicationProvider.getApplicationContext<Context>()
        game = GameSurfaceView(context)
        saves = SoloSaves(dir)
        game.saves = saves
        game.ledger = LedgerStore(File(dir, "ledger.dat"))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun waitFor(what: String, check: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!check()) {
            if (System.currentTimeMillis() > until) throw AssertionError("timed out waiting for $what (mode ${game.mode.value})")
            Thread.sleep(10)
        }
    }

    @Test
    fun saveAndQuitThenANewRunThenContinue() {
        game.startRun(Difficulty.NORMAL)
        waitFor("the run") { game.mode.value == Mode.PLAYING }
        waitFor("its slot") { saves.saves.value.size == 1 }

        game.pause()
        waitFor("pause") { game.mode.value == Mode.PAUSED }
        game.saveForLater()
        waitFor("home") { game.mode.value == Mode.HOME }
        assertEquals(1, saves.saves.value.size)

        game.startRun(Difficulty.HARD)
        waitFor("a second run") { game.mode.value == Mode.PLAYING }
        waitFor("its slot") { saves.saves.value.size == 2 }
        assertEquals(setOf(1, 2), saves.saves.value.map { it.slot }.toSet())

        game.pause()
        waitFor("pause") { game.mode.value == Mode.PAUSED }
        game.abandonRun()
        waitFor("home") { game.mode.value == Mode.HOME }
        assertEquals(listOf(1), saves.saves.value.map { it.slot })

        game.continueRun(1)
        waitFor("the continued run") { game.mode.value == Mode.PLAYING }
    }

    @Test
    fun everySlotTakenStopsANewRunWithoutHarm() {
        for (i in 1..SoloSaves.SLOTS) {
            game.startRun(Difficulty.NORMAL)
            waitFor("run $i") { game.mode.value == Mode.PLAYING }
            waitFor("slot $i") { saves.saves.value.size == i }
            game.pause()
            waitFor("pause") { game.mode.value == Mode.PAUSED }
            game.saveForLater()
            waitFor("home") { game.mode.value == Mode.HOME }
        }
        assertEquals(-1, saves.free())
        game.startRun(Difficulty.NORMAL)
        Thread.sleep(200)
        assertEquals(Mode.HOME, game.mode.value)
        assertTrue(saves.saves.value.size == SoloSaves.SLOTS)
    }
}
