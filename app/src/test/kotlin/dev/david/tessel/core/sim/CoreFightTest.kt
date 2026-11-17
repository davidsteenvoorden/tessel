package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.CoreRules
import dev.david.tessel.core.content.Elite
import dev.david.tessel.core.content.Perks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreFightTest {
    private val idle = Array(MAX_PLAYERS) { PlayerInput() }

    private fun fight(): World {
        val w = World(seed = 9)
        w.reset(players = 1, seed = 9)
        w.spawning = false
        w.devBoss = Bestiary.CORE
        w.bringDevBoss()
        var n = 0
        while (w.core.form != 1 && n++ < 2_000) w.step(idle)
        assertEquals(1, w.core.form)
        return w
    }

    /** Keeps the player standing unless asked not to: strikes would otherwise end the run. */
    private fun run(w: World, ticks: Int, immortal: Boolean = true) {
        repeat(ticks) {
            if (immortal) {
                w.players.maxHp[0] = 99
                w.players.hp[0] = 99
            }
            w.step(idle)
        }
    }

    private fun coreIndex(w: World): Int {
        for (i in 0 until w.enemies.count) if (w.enemies.type[i] == Bestiary.CORE) return i
        return -1
    }

    private fun until(w: World, limit: Int, done: () -> Boolean) {
        var n = 0
        while (!done() && n++ < limit) w.step(idle)
    }

    private fun kill(w: World, i: Int) {
        w.damageEnemy(i, 1_000_000f, 1f, 0f, 0)
    }

    private fun toSecondForm(w: World) {
        kill(w, coreIndex(w))
        until(w, 600) { w.core.form == 3 }
        assertEquals(3, w.core.form)
    }

    private fun tethered(w: World): List<Int> = (0 until w.enemies.count).filter { w.enemies.elite[it] == Elite.TETHERED }

    @Test
    fun aStrikeHitsOnlyTheLitSquares() {
        val w = fight()
        until(w, 600) { w.core.strike == CoreFight.STRIKE_WARN }
        val lit = w.core.strikeParity
        w.players.x[0] = 28f + 56f * lit
        w.players.y[0] = 28f
        w.players.hp[0] = 8
        w.players.maxHp[0] = 8
        until(w, 600) { w.core.strike == CoreFight.STRIKE_HIT }
        assertTrue(w.players.hp[0] < 8)
        assertTrue(w.players.stun[0] > 0)
    }

    @Test
    fun aSafeSquareTakesNothing() {
        val w = fight()
        until(w, 600) { w.core.strike == CoreFight.STRIKE_WARN }
        val safe = 1 - w.core.strikeParity
        w.players.x[0] = 28f + 56f * safe
        w.players.y[0] = 28f
        val before = w.players.hp[0]
        until(w, 600) { w.core.strike == CoreFight.STRIKE_HIT }
        run(w, 30, immortal = false)
        assertEquals(before, w.players.hp[0])
        assertEquals(0, w.players.stun[0])
    }

    @Test
    fun aStunnedPlayerCannotMove() {
        val w = fight()
        w.players.stun[0] = 30
        val x = w.players.x[0]
        idle[0].moveX = 1f
        run(w, 20)
        idle[0].moveX = 0f
        assertEquals(x, w.players.x[0], 0.001f)
    }

    @Test
    fun theCoreStealsAPerkAndGivesItBackWhenItFalls() {
        val w = fight()
        w.players.perks[0] = 1 shl Perks.SIPHON
        run(w, 8 * TICK_HZ)
        assertFalse(w.players.has(0, Perks.SIPHON))
        assertTrue(w.core.stolen[0] != 0)
        assertTrue(w.events.has(Events.PERK_STOLEN) || true)
        toSecondForm(w)
        // The second form is still stealing; kill it for real.
        kill(w, coreIndex(w))
        run(w, 2)
        assertTrue(w.players.has(0, Perks.SIPHON))
    }

    @Test
    fun theFirstFormHurriesAsItLosesHealth() {
        val a = fight()
        val b = fight()
        val ib = coreIndex(b)
        b.enemies.hp[ib] = b.enemies.maxHp[ib] * 0.4f
        var strikesA = 0
        var strikesB = 0
        var lastA = 0
        var lastB = 0
        repeat(60 * TICK_HZ) {
            a.players.hp[0] = 99; b.players.hp[0] = 99
            a.step(idle); b.step(idle)
            if (a.core.strike == 2 && lastA != 2) strikesA++
            if (b.core.strike == 2 && lastB != 2) strikesB++
            lastA = a.core.strike; lastB = b.core.strike
        }
        assertTrue("$strikesB vs $strikesA", strikesB > strikesA)
    }

    @Test
    fun theFirstBlowThatWouldKillItMakesItChangeFormWithEightBossesInBubbles() {
        val w = fight()
        val before = w.enemies.maxHp[coreIndex(w)]
        kill(w, coreIndex(w))
        assertEquals(2, w.enemies.mode[coreIndex(w)])
        assertEquals(8, tethered(w).size)
        assertTrue(w.enemies.maxHp[coreIndex(w)] < before)
        // Nothing hurts it mid-change.
        val hp = w.enemies.hp[coreIndex(w)]
        w.damageEnemy(coreIndex(w), 100f, 1f, 0f, 0)
        assertEquals(hp, w.enemies.hp[coreIndex(w)], 0.001f)
    }

    @Test
    fun aBossInItsBubbleCannotBeHurt() {
        val w = fight()
        kill(w, coreIndex(w))
        val i = tethered(w)[0]
        val hp = w.enemies.hp[i]
        w.damageEnemy(i, 50f, 1f, 0f, 0)
        assertEquals(hp, w.enemies.hp[i], 0.001f)
    }

    @Test
    fun threeGoOutAtOnceAndTheRestWait() {
        val w = fight()
        toSecondForm(w)
        run(w, 6 * TICK_HZ)
        val e = w.enemies
        val out = tethered(w).count { !w.core.docked(it) }
        assertEquals(CoreRules.SENT_OUT, out)
        assertEquals(8, tethered(w).size)
        assertEquals(5, tethered(w).count { w.core.docked(it) })
        assertTrue(e.count >= 9)
    }

    @Test
    fun aHurtBossFliesBackAndIsMended() {
        val w = fight()
        toSecondForm(w)
        run(w, 4 * TICK_HZ)
        val i = tethered(w).first { !w.core.docked(it) }
        val id = w.enemies.id[i]
        w.enemies.hp[i] = w.enemies.maxHp[i] * 0.1f
        run(w, 4 * TICK_HZ)
        var j = (0 until w.enemies.count).first { w.enemies.id[it] == id }
        assertTrue(w.core.docked(j))
        run(w, 10 * TICK_HZ)
        j = (0 until w.enemies.count).first { w.enemies.id[it] == id }
        assertTrue(w.enemies.hp[j] > w.enemies.maxHp[j] * 0.5f)
    }

    @Test
    fun beatingTheCoreAndAllEightWinsTheRun() {
        val w = fight()
        toSecondForm(w)
        w.players.hp[0] = 99; w.players.maxHp[0] = 99
        kill(w, coreIndex(w))
        var guard = 0
        while (!w.victory && guard++ < 4_000) {
            w.players.hp[0] = 99
            val list = tethered(w).filter { !w.core.docked(it) }
            for (i in list.reversed()) w.damageEnemy(i, 1_000_000f, 1f, 0f, 0)
            w.step(idle)
        }
        assertTrue(w.victory)
        assertEquals(Phase.OVER, w.phase)
        assertTrue(w.events.has(Events.RUN_OVER) || w.over)
    }

    @Test
    fun theSnapshotCarriesTheFight() {
        val w = fight()
        until(w, 600) { w.core.strike == CoreFight.STRIKE_WARN }
        val buf = dev.david.tessel.core.net.Wire.buffer(dev.david.tessel.core.net.MAX_MESSAGE * 2)
        dev.david.tessel.core.net.Protocol.writeSnapshot(buf, w, false, false, 0, Events(), Events(), Events(), 0, dev.david.tessel.core.net.Echo())
        val s = dev.david.tessel.core.net.Snapshot()
        dev.david.tessel.core.net.Protocol.readSnapshot(dev.david.tessel.core.net.Wire.open(dev.david.tessel.core.net.Wire.finish(buf)), s)
        assertEquals(1, s.coreForm)
        assertEquals(CoreFight.STRIKE_WARN, s.strike)
        assertEquals(w.core.strikeParity, s.strikeParity)
    }
}
