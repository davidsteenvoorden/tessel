package dev.david.tessel.core.sim

import dev.david.tessel.core.net.Echo
import dev.david.tessel.core.net.MAX_MESSAGE
import dev.david.tessel.core.net.Protocol
import dev.david.tessel.core.net.Snapshot
import dev.david.tessel.core.net.Wire
import dev.david.tessel.core.stats.Achievements
import dev.david.tessel.core.stats.Ledger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuelTest {
    private val inputs = Array(2) { PlayerInput() }

    private fun duel(target: Int = 2, hp: Int = 3, power: Int = 0): World {
        val w = World(seed = 5)
        w.startDuel(2, 5, DuelSetup(target, hp, power))
        // Close enough for a plain shot to land.
        w.players.x[0] = 700f; w.players.y[0] = 360f
        w.players.x[1] = 900f; w.players.y[1] = 360f
        inputs[0].aimX = 1f; inputs[0].fire = true
        inputs[1].aimX = 0f; inputs[1].fire = false
        return w
    }

    private fun run(w: World, ticks: Int) { repeat(ticks) { w.step(inputs) } }

    @Test
    fun aDuelHasNoEnemiesAndEveryoneStartsAlike() {
        val w = duel(hp = 5)
        run(w, 600)
        assertEquals(0, w.enemies.count)
        assertEquals(5, w.players.maxHp[0])
        assertEquals(5, w.players.maxHp[1])
        assertEquals(1, w.stage)
    }

    @Test
    fun aShotCostsTheOtherPlayerOnePipAndNotTheShooter() {
        val w = duel(hp = 3)
        run(w, 40)
        assertTrue(w.players.hp[1] < 3)
        assertEquals(3, w.players.hp[0])
    }

    @Test
    fun theLastOneStandingTakesTheRoundAndTheNextStartsFresh() {
        val w = duel(target = 2, hp = 1)
        run(w, 60)
        assertEquals(1, w.duel.wins[0])
        assertEquals(0, w.duel.wins[1])
        run(w, 4 * 60)
        assertEquals(2, w.duel.round)
        assertEquals(1, w.players.hp[1])
        assertEquals(1, w.players.hp[0])
    }

    @Test
    fun reachingTheTargetEndsTheMatch() {
        val w = duel(target = 1, hp = 1)
        run(w, 60)
        assertEquals(0, w.duel.champion)
        assertFalse(w.over)
        run(w, 4 * 60)
        assertTrue(w.over)
    }

    @Test
    fun standingPlayersAreSolid() {
        val w = World(seed = 3)
        w.reset(players = 2, seed = 3)
        w.spawning = false
        w.players.x[0] = 800f; w.players.y[0] = 360f
        w.players.x[1] = 806f; w.players.y[1] = 360f
        inputs[0].fire = false
        run(w, 1)
        assertTrue(w.players.x[1] - w.players.x[0] > 6f)
    }

    @Test
    fun duelWinsAreCountedAndOldFilesStillRead() {
        val l = Ledger()
        l.recordDuel(true)
        l.recordDuel(false)
        val back = Ledger.read(l.write())!!
        assertEquals(2, back.totals.pvpPlayed)
        assertEquals(1, back.totals.pvpWins)
        // A file from before duels has no tail.
        val old = l.write().copyOf(l.write().size - 8)
        assertEquals(0, Ledger.read(old)!!.totals.pvpWins)
    }

    @Test
    fun theKillLadderStepsUp() {
        val kills = Achievements.all.filter { it.name == "Pest Control" || it.name == "Crowd Control" || it.name == "Swarm Breaker" }
        assertEquals(listOf(500, 5_000, 25_000), kills.map { it.target })
        assertTrue(Achievements.all.map { it.name }.toSet().size == Achievements.all.size)
    }

    @Test
    fun theScoreTravelsInASnapshot() {
        val w = duel(target = 3, hp = 1)
        run(w, 60)
        val buf = Wire.buffer(MAX_MESSAGE * 2)
        Protocol.writeSnapshot(buf, w, false, false, 0, Events(), Events(), Events(), 0, Echo())
        val s = Snapshot()
        Protocol.readSnapshot(Wire.open(Wire.finish(buf)), s)
        assertTrue(s.duel)
        assertEquals(3, s.duelTarget)
        assertEquals(1, s.duelRound)
        assertEquals(1, s.duelWins[0])
        assertEquals(0, s.duelRoundWinner)
        assertTrue(s.duelPause > 0)
    }

    @Test
    fun everyoneStartsOnTheirOwnSpotFacingTheMiddle() {
        val spots = (0 until 3).map { DuelSpawn.x(it, 3) to DuelSpawn.y(it, 3) }
        assertEquals(3, spots.toSet().size)
        assertTrue(DuelSpawn.aimX(0, 2) > 0f)
    }
}
