package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Balance
import dev.david.tessel.core.content.Bestiary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatTest {
    private val inputs = Array(MAX_PLAYERS) { PlayerInput() }
    private val world = World(seed = 11).apply { spawning = false }

    private fun aimRight() {
        inputs[0].aimX = 1f
        inputs[0].aimY = 0f
        inputs[0].fire = true
    }

    private fun stepCountingShots(ticks: Int): Int {
        var shots = 0
        repeat(ticks) {
            world.step(inputs)
            for (i in 0 until world.events.count) if (world.events.kind[i] == Events.SHOT) shots++
            world.events.clear()
        }
        return shots
    }

    @Test
    fun holdingFireShootsAtTheFireRate() {
        aimRight()
        val shots = stepCountingShots(TICK_HZ * 4)
        assertEquals(Balance.FIRE_RATE * 4, shots.toFloat(), 1f)
    }

    @Test
    fun noShotsWithoutAnAim() {
        inputs[0].fire = true
        assertEquals(0, stepCountingShots(TICK_HZ))
    }

    @Test
    fun bulletsExpireAfterTheirRange() {
        aimRight()
        world.step(inputs)
        inputs[0].fire = false
        repeat(TICK_HZ) { world.step(inputs) }
        assertEquals(0, world.bullets.count)
    }

    @Test
    fun aShotKillsADotAndDropsItsCoin() {
        val px = world.players.x[0]
        val py = world.players.y[0]
        world.spawnEnemy(Bestiary.DOT, px + 200f, py)
        aimRight()
        var killed = false
        repeat(TICK_HZ) {
            world.step(inputs)
            if (world.events.has(Events.ENEMY_KILLED)) killed = true
            world.events.clear()
        }
        assertTrue(killed)
        assertEquals(1, world.players.kills[0])
        assertEquals(0, world.enemies.count)
    }

    @Test
    fun aSlabTakesSixHits() {
        val px = world.players.x[0]
        val py = world.players.y[0]
        world.spawnEnemy(Bestiary.SLAB, px + 300f, py)
        aimRight()
        var hits = 0
        var kills = 0
        repeat(TICK_HZ * 4) {
            world.step(inputs)
            for (i in 0 until world.events.count) {
                when (world.events.kind[i]) {
                    Events.ENEMY_HIT -> hits++
                    Events.ENEMY_KILLED -> kills++
                }
            }
            world.events.clear()
        }
        assertEquals(5, hits)
        assertEquals(1, kills)
    }

    @Test
    fun coinsNearbyFlyToThePlayer() {
        val px = world.players.x[0]
        val py = world.players.y[0]
        world.coins.add(id = 999, x = px + Balance.MAGNET_RADIUS - 5f, y = py, vx = 0f, vy = 0f, value = 3)
        repeat(TICK_HZ / 2) { world.step(inputs) }
        assertEquals(0, world.coins.count)
        assertEquals(3, world.players.coins[0])
    }

    @Test
    fun coinsOutOfReachStayPut() {
        val px = world.players.x[0]
        val py = world.players.y[0]
        world.coins.add(id = 999, x = px + 300f, y = py, vx = 0f, vy = 0f, value = 1)
        repeat(TICK_HZ) { world.step(inputs) }
        assertEquals(1, world.coins.count)
        assertEquals(px + 300f, world.coins.x[0], 1e-3f)
    }

    @Test
    fun contactHurtsOnceThenGraceProtects() {
        val px = world.players.x[0]
        val py = world.players.y[0]
        world.spawnEnemy(Bestiary.DOT, px + 5f, py)
        world.step(inputs)
        assertEquals(Balance.PLAYER_HP - 1, world.players.hp[0])
        assertTrue(world.players.invuln[0] > 0)

        world.spawnEnemy(Bestiary.DOT, px - 5f, py)
        world.step(inputs)
        assertEquals(Balance.PLAYER_HP - 1, world.players.hp[0])
    }

    @Test
    fun beingHitShovesNearbyEnemiesAway() {
        val px = world.players.x[0]
        val py = world.players.y[0]
        world.spawnEnemy(Bestiary.DOT, px + 5f, py)
        world.spawnEnemy(Bestiary.DOT, px + 60f, py)
        world.step(inputs)
        assertTrue(world.enemies.kx[1] > 0f || world.enemies.kx[0] > 0f)
    }

    @Test
    fun theRunEndsWhenTheLastPlayerFalls() {
        val px = world.players.x[0]
        val py = world.players.y[0]
        world.players.hp[0] = 1
        world.spawnEnemy(Bestiary.DOT, px + 5f, py)
        world.step(inputs)
        assertTrue(world.over)
        assertTrue(world.events.has(Events.RUN_OVER))

        val tickAtEnd = world.tick
        world.step(inputs)
        assertEquals(tickAtEnd, world.tick)
    }

    @Test
    fun resetStartsAFreshRun() {
        world.players.hp[0] = 1
        world.spawnEnemy(Bestiary.DOT, world.players.x[0], world.players.y[0])
        world.step(inputs)
        world.reset(seed = 3)
        assertFalse(world.over)
        assertEquals(0, world.enemies.count)
        assertEquals(Balance.PLAYER_HP, world.players.hp[0])
        assertEquals(0, world.tick)
    }

    @Test
    fun overlappingEnemiesDriftApart() {
        world.players.x[0] = 100f
        world.players.y[0] = 100f
        world.spawnEnemy(Bestiary.DOT, 900f, 500f)
        world.spawnEnemy(Bestiary.DOT, 901f, 500f)
        repeat(20) { world.step(inputs) }
        val dx = world.enemies.x[1] - world.enemies.x[0]
        val dy = world.enemies.y[1] - world.enemies.y[0]
        val reach = Bestiary.of(Bestiary.DOT).radius * 2
        assertTrue(dx * dx + dy * dy > reach * reach * 0.8f)
    }
}
