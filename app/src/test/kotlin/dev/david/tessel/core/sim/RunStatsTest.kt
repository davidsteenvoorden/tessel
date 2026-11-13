package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Upgrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunStatsTest {
    private val inputs = Array(MAX_PLAYERS) { PlayerInput() }
    private val world = World(seed = 4).apply {
        spawning = false
        reset(players = 2, seed = 4)
    }

    @Test
    fun everyBulletFiredCounts() {
        world.players.levels[0][Upgrades.MULTISHOT] = 2
        inputs[0].aimX = 1f
        inputs[0].fire = true
        world.step(inputs)
        assertEquals(3, world.players.fired[0])
    }

    @Test
    fun damageCountsOnlyWhatTheEnemyHadLeft() {
        val slab = world.spawnEnemy(Bestiary.SLAB, 300f, 300f)
        val id = world.enemies.id[slab] and 0xFFFF
        val full = world.enemies.hp[slab]
        world.remoteHit(0, id, 1f, 0f)
        assertEquals(world.players.damage(0), world.players.dealt[0], 0.001f)

        // A shot far bigger than what is left counts as what was left.
        world.players.levels[1][Upgrades.DAMAGE] = 60
        world.remoteHit(1, id, 1f, 0f)
        assertEquals(full - world.players.damage(0), world.players.dealt[1], 0.001f)
        assertEquals(1, world.players.kills[1])
    }

    @Test
    fun hitsTakenAndTimesDownedCount() {
        world.players.hp[1] = 2
        world.remoteHurt(1, -1)
        world.players.invuln[1] = 0
        world.remoteHurt(1, -1)
        assertEquals(2, world.players.taken[1])
        assertEquals(1, world.players.downs[1])
    }

    @Test
    fun whoeverStandsCloseGetsTheRevive() {
        world.players.hp[1] = 0
        world.players.x[0] = world.players.x[1] + 30f
        world.players.y[0] = world.players.y[1]
        repeat(REVIVE_TICKS) { world.step(inputs) }
        assertTrue(world.players.alive(1))
        assertEquals(1, world.players.revived[0])
        assertEquals(0, world.players.revived[1])
    }

    @Test
    fun aBeatenBossCountsForTheTeam() {
        val boss = world.spawnEnemy(Bestiary.bossOrder[0], 800f, 300f)
        world.enemies.hp[boss] = 0.5f
        world.remoteHit(1, world.enemies.id[boss] and 0xFFFF, 1f, 0f)
        assertEquals(1, world.bossesDown)
    }

    @Test
    fun aNewRunStartsFromNothing() {
        world.players.fired[0] = 9
        world.players.dealt[1] = 9f
        world.bossesDown = 2
        world.reset(players = 2, seed = 4)
        assertEquals(0, world.players.fired[0])
        assertEquals(0f, world.players.dealt[1], 0f)
        assertEquals(0, world.bossesDown)
    }
}
