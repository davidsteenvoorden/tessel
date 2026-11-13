package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Balance
import dev.david.tessel.core.content.Bestiary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BossTest {
    private val idle = Array(MAX_PLAYERS) { PlayerInput() }

    private fun World.hold(ticks: Int) = repeat(ticks) {
        for (p in 0 until playerCount) if (players.present[p]) players.hp[p] = players.maxHp[p]
        step(idle)
        // The game reads and clears these every frame; a full buffer would drop the ones a test looks for.
        events.clear()
    }

    private fun bossAt(stage: Int, players: Int = 1): World {
        val w = World(seed = 77)
        w.reset(players = players, seed = 77)
        w.spawning = false
        w.skipTo(stage)
        w.hold(TICK_HZ * 3)
        return w
    }

    @Test
    fun everyFifthStageHasABossAndTheLineRepeats() {
        assertEquals(Bestiary.HEXARCH, bossAt(5).bossType())
        assertEquals(Bestiary.LANCER, bossAt(10).bossType())
        assertEquals(Bestiary.APIARY, bossAt(15).bossType())
        assertEquals(Bestiary.GYRE, bossAt(20).bossType())
        assertEquals(Bestiary.HEXARCH, bossAt(25).bossType())
        assertEquals(-1, bossAt(6).bossType())
    }

    @Test
    fun theSecondRoundOfBossesIsTougher() {
        val first = bossAt(5)
        val again = bossAt(25)
        assertTrue(again.enemies.maxHp[again.boss] > first.enemies.maxHp[first.boss] * 1.4f)
    }

    @Test
    fun moreFriendsMeanALongerBossFight() {
        val solo = bossAt(5, players = 1)
        val four = bossAt(5, players = 4)
        assertEquals(solo.enemies.maxHp[solo.boss] * (1f + 3 * Balance.BOSS_HP_PER_PLAYER), four.enemies.maxHp[four.boss], 0.5f)
    }

    @Test
    fun theClockDoesNotEndABossStageButTheBossDoes() {
        val w = bossAt(5)
        w.hold(STAGE_TICKS + 60)
        assertEquals(Phase.FIGHT, w.phase)
        assertTrue(w.boss >= 0)

        // Bring it down with one well-placed shot.
        val b = w.boss
        w.enemies.hp[b] = 0.5f
        w.players.x[0] = w.enemies.x[b]
        w.players.y[0] = (w.enemies.y[b] + 150f).coerceAtMost(ARENA_H - 20f)
        w.hostile.clear()
        val aim = Array(MAX_PLAYERS) { PlayerInput() }
        aim[0].aimY = -1f
        aim[0].fire = true
        var downed = false
        repeat(30) {
            w.players.hp[0] = w.players.maxHp[0]
            w.step(aim)
            if (w.events.has(Events.BOSS_DOWN)) downed = true
            w.events.clear()
        }
        assertTrue(downed)
        assertEquals(-1, w.bossType())
        assertTrue(w.phase == Phase.CLEAR || w.phase == Phase.SHOP)
    }

    @Test
    fun theApiaryShrugsOffShotsWhileItsDronesFly() {
        val w = bossAt(15)
        w.hold(TICK_HZ * 4)
        var drones = 0
        for (i in 0 until w.enemies.count) if (w.enemies.type[i] == Bestiary.DRONE) drones++
        assertTrue("drones out: $drones", drones > 0)
        val b = w.boss
        val before = w.enemies.hp[b]
        w.players.x[0] = w.enemies.x[b]
        w.players.y[0] = (w.enemies.y[b] + 120f).coerceAtMost(ARENA_H - 20f)
        // A drone in the way would take the shot instead; clear the line.
        for (i in 0 until w.enemies.count) if (w.enemies.type[i] == Bestiary.DRONE) w.enemies.x[i] = 30f
        val aim = Array(MAX_PLAYERS) { PlayerInput() }
        aim[0].aimY = -1f
        aim[0].fire = true
        w.step(aim)
        w.hold(15)
        val lost = before - w.enemies.hp[b]
        assertTrue("took $lost", lost > 0f && lost < Balance.BULLET_DAMAGE * 0.5f)
    }

    @Test
    fun bossesKeepToTheArena() {
        for (stage in intArrayOf(5, 10, 15, 20)) {
            val w = bossAt(stage)
            w.hold(TICK_HZ * 40)
            val b = w.boss
            assertTrue("stage $stage lost its boss", b >= 0)
            val r = Bestiary.of(w.enemies.type[b]).radius
            // A charging Lancer can be one step past the wall for a tick before it turns.
            val slack = 15f
            assertTrue(w.enemies.x[b] >= r - slack && w.enemies.x[b] <= ARENA_W - r + slack)
            assertTrue(w.enemies.y[b] >= r - slack && w.enemies.y[b] <= ARENA_H - r + slack)
            assertTrue("stage $stage boss never shot", w.hostile.count > 0 || w.enemies.count > 1)
        }
    }

    @Test
    fun aLongRunThroughEveryStageStaysSane() {
        // Bots on a team of three that cannot die, twenty stages, everything spawning.
        val w = World(seed = 99)
        w.reset(players = 3, seed = 99)
        val bots = Array(3) { Bot() }
        val inputs = Array(MAX_PLAYERS) { PlayerInput() }
        for (stage in 1..20) {
            w.skipTo(stage)
            repeat(TICK_HZ * 20) {
                for (p in 0 until 3) {
                    w.players.hp[p] = w.players.maxHp[p]
                    bots[p].drive(w, p, inputs[p])
                }
                w.step(inputs)
                w.events.clear()
            }
            for (i in 0 until w.enemies.count) {
                assertTrue("stage $stage enemy at ${w.enemies.x[i]}", w.enemies.x[i].isFinite() && w.enemies.y[i].isFinite())
                assertTrue(w.enemies.x[i] > -OUTSIDE * 2 && w.enemies.x[i] < ARENA_W + OUTSIDE * 2)
            }
            assertTrue(w.enemies.count <= MAX_ENEMIES)
            assertFalse(w.over)
        }
    }
}
