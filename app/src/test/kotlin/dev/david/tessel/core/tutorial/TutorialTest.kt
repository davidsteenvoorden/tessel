package dev.david.tessel.core.tutorial

import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.sim.Bot
import dev.david.tessel.core.sim.Phase
import dev.david.tessel.core.sim.PlayerInput
import dev.david.tessel.core.sim.TICK_HZ
import dev.david.tessel.core.sim.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialTest {
    private val inputs = arrayOf(PlayerInput())

    private fun start(): Pair<World, Tutorial> {
        val w = World(seed = 5)
        w.reset(players = 1, seed = 5, difficulty = Difficulty.EASY)
        w.practice = true
        w.spawning = false
        return w to Tutorial().also { it.begin(w) }
    }

    private fun tick(w: World, t: Tutorial) {
        w.step(inputs)
        t.update(w, inputs[0])
    }

    @Test
    fun standingStillLeavesTheFirstStepWhereItWas() {
        val (w, t) = start()
        repeat(10 * TICK_HZ) { tick(w, t) }
        assertEquals(TutorialStep.MOVE, t.step)
        assertEquals(0f, t.progress, 0.001f)
    }

    @Test
    fun walkingAroundFinishesMovingAndMovesOn() {
        val (w, t) = start()
        var n = 0
        while (t.step == TutorialStep.MOVE && n < 30 * TICK_HZ) {
            // Back and forth across the arena.
            inputs[0].moveX = if ((n / 90) % 2 == 0) 1f else -1f
            tick(w, t)
            n++
        }
        assertEquals(TutorialStep.FIRE, t.step)
    }

    @Test
    fun theFightStepBringsTargetsAndCountsBrokenOnes() {
        val (w, t) = start()
        var n = 0
        val bot = Bot()
        // Skip the walking.
        while (t.step == TutorialStep.MOVE) {
            inputs[0].moveX = if ((n++ / 90) % 2 == 0) 1f else -1f
            tick(w, t)
        }
        inputs[0].clear()
        n = 0
        while (t.step == TutorialStep.FIRE && n < 120 * TICK_HZ) {
            bot.drive(w, 0, inputs[0])
            tick(w, t)
            n++
        }
        assertTrue("the Dots were broken", t.step != TutorialStep.FIRE)
        assertTrue(w.players.kills[0] >= Tutorial.FIRE_KILLS)
    }

    @Test
    fun nothingHurtsAndTheStageNeverEnds() {
        val (w, t) = start()
        w.spawnEnemy(0, w.players.x[0] + 5f, w.players.y[0])
        val hp = w.players.hp[0]
        repeat(150 * TICK_HZ) {
            inputs[0].clear()
            tick(w, t)
        }
        assertEquals(hp, w.players.hp[0])
        assertEquals(Phase.FIGHT, w.phase)
        assertEquals(1, w.stage)
    }

    @Test
    fun beingHitWhileDodgingStartsTheCountOver() {
        val (w, t) = start()
        var n = 0
        // Through the first two steps with a bot, which keeps moving and shooting.
        val bot = Bot()
        while (t.step != TutorialStep.DODGE && n < 400 * TICK_HZ) {
            if (t.step == TutorialStep.MOVE) {
                inputs[0].clear()
                inputs[0].moveX = if ((n / 90) % 2 == 0) 1f else -1f
            } else {
                bot.drive(w, 0, inputs[0])
            }
            tick(w, t)
            n++
        }
        assertEquals(TutorialStep.DODGE, t.step)
        // Standing still in front of shooters gets hit sooner or later, and the progress drops back to nothing.
        var sawHit = false
        var peak = 0f
        n = 0
        while (n < 120 * TICK_HZ && t.step == TutorialStep.DODGE) {
            inputs[0].clear()
            tick(w, t)
            peak = maxOf(peak, t.progress)
            if (t.hit) sawHit = true
            n++
        }
        assertTrue("a shot reached the player", sawHit)
        assertTrue(peak < 1f)
        assertFalse(w.players.hp[0] < w.players.maxHp[0])
    }
}
