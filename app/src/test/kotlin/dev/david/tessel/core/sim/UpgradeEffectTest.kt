package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Balance
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Upgrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpgradeEffectTest {
    private val inputs = Array(MAX_PLAYERS) { PlayerInput() }
    private val world = World(seed = 12).apply { spawning = false }

    private fun fireRight() {
        inputs[0].aimX = 1f
        inputs[0].aimY = 0f
        inputs[0].fire = true
    }

    @Test
    fun multishotFiresAFan() {
        world.players.levels[0][Upgrades.MULTISHOT] = 2
        fireRight()
        world.step(inputs)
        assertEquals(3, world.bullets.count)
        val ys = (0 until 3).map { world.bullets.vy[it] }.sorted()
        assertTrue(ys[0] < 0f && ys[2] > 0f)
        assertEquals(0f, ys[1], 1e-3f)
    }

    @Test
    fun pierceGoesThroughALine() {
        world.players.levels[0][Upgrades.PIERCE] = 2
        val px = world.players.x[0]
        val py = world.players.y[0]
        world.spawnEnemy(Bestiary.DOT, px + 120f, py)
        world.spawnEnemy(Bestiary.DOT, px + 200f, py)
        world.spawnEnemy(Bestiary.DOT, px + 280f, py)
        fireRight()
        world.step(inputs)
        inputs[0].fire = false
        repeat(30) { world.step(inputs) }
        assertEquals(3, world.players.kills[0])
    }

    @Test
    fun fastBulletsDoNotSkipSmallEnemies() {
        world.players.levels[0][Upgrades.VELOCITY] = Upgrades.of(Upgrades.VELOCITY).maxLevel
        val px = world.players.x[0]
        val py = world.players.y[0]
        for (k in 0 until 6) world.spawnEnemy(Bestiary.PIP, px + 150f + k * 37f, py)
        fireRight()
        world.step(inputs)
        inputs[0].fire = false
        repeat(20) { world.step(inputs) }
        assertEquals(1, world.players.kills[0])
    }

    @Test
    fun damageUpgradeKillsASlabFaster() {
        world.players.levels[0][Upgrades.DAMAGE] = 4
        val hitsNeeded = kotlin.math.ceil(Bestiary.of(Bestiary.SLAB).hp / world.players.damage(0)).toInt()
        assertTrue(hitsNeeded < Bestiary.of(Bestiary.SLAB).hp.toInt())
    }

    @Test
    fun speedUpgradeMovesFarther() {
        world.players.levels[0][Upgrades.SPEED] = 5
        val start = world.players.x[0]
        inputs[0].moveX = 1f
        repeat(TICK_HZ) { world.step(inputs) }
        assertEquals(start + Balance.PLAYER_SPEED * 1.3f, world.players.x[0], 0.5f)
    }

    @Test
    fun wealthRaisesCoinValue() {
        world.players.levels[0][Upgrades.WEALTH] = 8
        val px = world.players.x[0]
        val py = world.players.y[0]
        var total = 0
        repeat(40) {
            world.spawnEnemy(Bestiary.DOT, px + 160f, py)
            fireRight()
            repeat(20) { world.step(inputs) }
        }
        for (i in 0 until world.coins.count) total += world.coins.value[i]
        total += world.players.coins[0]
        // Plain coins are worth 1 each; +120% should land well above the kill count.
        assertTrue("total $total from ${world.players.kills[0]} kills", total > world.players.kills[0] * 1.8f)
    }

    @Test
    fun magnetReachesFarther() {
        world.players.levels[0][Upgrades.MAGNET] = 4
        val px = world.players.x[0]
        val py = world.players.y[0]
        world.coins.add(id = 77, x = px + Balance.MAGNET_RADIUS * 1.8f, y = py, vx = 0f, vy = 0f, value = 1)
        repeat(TICK_HZ) { world.step(inputs) }
        assertEquals(1, world.players.coins[0])
    }
}
