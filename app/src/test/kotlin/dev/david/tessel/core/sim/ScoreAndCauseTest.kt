package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.content.Scoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreAndCauseTest {
    private val inputs = Array(MAX_PLAYERS) { PlayerInput() }
    private val world = World(seed = 6).apply {
        spawning = false
        reset(players = 2, seed = 6, difficulty = Difficulty.NORMAL)
    }

    @Test
    fun killsPutPointsOnTheKiller() {
        val slab = world.spawnEnemy(Bestiary.SLAB, 300f, 300f)
        world.enemies.hp[slab] = 0.5f
        world.remoteHit(1, world.enemies.id[slab] and 0xFFFF, 1f, 0f)
        assertEquals(Scoring.kill(Bestiary.SLAB), world.players.points[1])
        assertEquals(0, world.players.points[0])
        assertTrue(world.score(1) > world.score(0))
    }

    @Test
    fun theScoreGrowsWithTimeAlive() {
        val before = world.score(0)
        repeat(TICK_HZ * 10) { world.step(inputs) }
        assertTrue(world.score(0) >= before + 10 * Scoring.PER_SECOND - 1)
    }

    @Test
    fun aStageInTheShopCountsAsCleared() {
        assertEquals(0, world.stagesCleared)
        while (world.phase != Phase.SHOP) world.step(inputs)
        assertEquals(world.stage, world.stagesCleared)
        world.nextStage()
        assertEquals(world.stage - 1, world.stagesCleared)
    }

    @Test
    fun touchingAnEnemyNamesItAsTheCause() {
        world.players.hp[1] = 1
        val e = world.spawnEnemy(Bestiary.SLAB, world.players.x[1], world.players.y[1])
        assertTrue(e >= 0)
        world.remoteHurt(1, -1)
        assertEquals(Cause.touch(Bestiary.SLAB), world.players.killedBy[1])
        assertFalse(Cause.isShot(world.players.killedBy[1]))
        assertEquals(Bestiary.SLAB, Cause.type(world.players.killedBy[1]))
    }

    @Test
    fun anEnemyShotNamesItsFirerAsTheCause() {
        world.players.hp[0] = 1
        world.spawnHostile(world.players.x[0] + 300f, world.players.y[0], 0f, 0f, Bestiary.TICK)
        val shot = world.hostile.id[0] and 0xFFFF
        world.remoteHurt(0, shot)
        val cause = world.players.killedBy[0]
        assertEquals(Bestiary.TICK, Cause.type(cause))
        assertTrue(Cause.isShot(cause))
    }

    @Test
    fun aShotThatHitsOnTheHostNamesItsFirerToo() {
        world.remote[1] = false
        world.players.hp[0] = 1
        world.players.invuln[0] = 0
        // A shot already on the player; the next tick lands it.
        world.spawnHostile(world.players.x[0], world.players.y[0], 0f, 0f, Bestiary.FANNER)
        world.step(inputs)
        assertEquals(Cause.shot(Bestiary.FANNER), world.players.killedBy[0])
    }

    @Test
    fun aHitThatDoesNotDownYouLeavesNoCause() {
        world.players.hp[1] = 3
        world.remoteHurt(1, -1)
        assertEquals(Cause.NONE, world.players.killedBy[1])
    }

    @Test
    fun gettingBackUpClearsTheCause() {
        world.players.hp[1] = 1
        world.spawnEnemy(Bestiary.DOT, world.players.x[1], world.players.y[1])
        world.remoteHurt(1, -1)
        assertTrue(world.players.killedBy[1] >= 0)
        world.players.x[0] = world.players.x[1] + 30f
        world.players.y[0] = world.players.y[1]
        world.enemies.clear()
        repeat(REVIVE_TICKS) { world.step(inputs) }
        assertTrue(world.players.alive(1))
        assertEquals(Cause.NONE, world.players.killedBy[1])
    }

    @Test
    fun aNewRunForgetsEverything() {
        world.players.points[0] = 99
        world.players.killedBy[0] = Cause.shot(Bestiary.TICK)
        world.reset(players = 2, seed = 6)
        assertEquals(0, world.players.points[0])
        assertEquals(Cause.NONE, world.players.killedBy[0])
    }

    @Test
    fun causeCodesKeepTheirParts() {
        for (type in 0 until Bestiary.size) {
            assertEquals(type, Cause.type(Cause.touch(type)))
            assertEquals(type, Cause.type(Cause.shot(type)))
            assertTrue(Cause.isShot(Cause.shot(type)))
            assertFalse(Cause.isShot(Cause.touch(type)))
        }
        assertEquals(Cause.NONE, Cause.shot(-1))
        assertEquals(-1, Cause.type(Cause.NONE))
    }
}
