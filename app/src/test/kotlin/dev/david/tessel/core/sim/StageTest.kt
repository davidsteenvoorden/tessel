package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Balance
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StageTest {
    private val idle = Array(MAX_PLAYERS) { PlayerInput() }
    private val world = World(seed = 3).apply { spawning = false }

    private fun stepUntil(limit: Int, done: () -> Boolean): Int {
        var n = 0
        while (!done() && n < limit) {
            world.step(idle)
            n++
        }
        return n
    }

    @Test
    fun anEmptyArenaGoesStraightToTheShopWhenTheClockRunsOut() {
        stepUntil(STAGE_TICKS * 2) { world.phase == Phase.SHOP }
        assertEquals(Phase.SHOP, world.phase)
        assertEquals(STAGE_TICKS, world.stageTicks)
    }

    @Test
    fun stragglersGetAFewSecondsThenVanish() {
        stepUntil(STAGE_TICKS) { world.stageTicks == STAGE_TICKS - 1 }
        world.players.x[0] = 100f
        world.players.y[0] = 100f
        world.spawnEnemy(Bestiary.SLAB, ARENA_W + 60f, ARENA_H + 60f)
        val ticks = stepUntil(TICK_HZ * 20) { world.phase == Phase.SHOP }
        // One tick to run the clock out, then the grace period.
        assertEquals(1 + Balance.CLEAR_SECONDS * TICK_HZ, ticks)
        assertEquals(0, world.enemies.count)
        assertTrue(world.events.has(Events.ENEMY_GONE))
    }

    @Test
    fun theShopSweepsLooseCoinsIntoTheWallet() {
        stepUntil(STAGE_TICKS) { world.stageTicks == STAGE_TICKS - 1 }
        world.coins.add(id = 900, x = 1500f, y = 600f, vx = 0f, vy = 0f, value = 4)
        stepUntil(TICK_HZ * 20) { world.phase == Phase.SHOP }
        assertEquals(4, world.players.coins[0])
        assertEquals(4, world.players.earned[0])
    }

    @Test
    fun nothingMovesInTheShop() {
        stepUntil(STAGE_TICKS * 2) { world.phase == Phase.SHOP }
        val tick = world.tick
        world.step(idle)
        assertEquals(tick, world.tick)
    }

    @Test
    fun theNextStageIsFasterAndPaysMore() {
        val speed1 = world.enemySpeedScale()
        stepUntil(STAGE_TICKS * 2) { world.phase == Phase.SHOP }
        world.nextStage()
        assertEquals(2, world.stage)
        assertEquals(Phase.FIGHT, world.phase)
        assertEquals(0, world.stageTicks)
        assertTrue(world.enemySpeedScale() > speed1)
    }

    @Test
    fun enemiesStartSlowerThanTheirListedSpeed() {
        assertTrue(world.enemySpeedScale() < 0.8f)
    }

    @Test
    fun enemiesToughenAfterTheFirstZone() {
        assertEquals(Difficulty.NORMAL.toughness, world.enemyHpScale(), 0f)
        repeat(3) {
            stepUntil(STAGE_TICKS * 2) { world.phase == Phase.SHOP }
            world.nextStage()
        }
        assertEquals(4, world.stage)
        assertTrue(world.enemyHpScale() > Difficulty.NORMAL.toughness)
    }

    @Test
    fun difficultyScalesSpeedAndToughness() {
        world.reset(seed = 1, difficulty = Difficulty.HARD)
        val hard = world.enemySpeedScale()
        val hardHp = world.enemyHpScale()
        world.reset(seed = 1, difficulty = Difficulty.EASY)
        assertTrue(hard > world.enemySpeedScale())
        assertTrue(hardHp > world.enemyHpScale())
        assertEquals(Difficulty.EASY, world.difficulty)
    }

    @Test
    fun aFallenTeammateReturnsForTheNextStage() {
        world.reset(players = 2, seed = 4)
        world.players.hp[1] = 0
        stepUntil(STAGE_TICKS * 2) { world.phase == Phase.SHOP }
        world.nextStage()
        assertEquals(START_HP / 2, world.players.hp[1])
        assertFalse(world.over)
    }
}

/** What a player starts with on Normal, which these worlds use. */
private val START_HP = Balance.PLAYER_HP + Difficulty.NORMAL.bonusHp
