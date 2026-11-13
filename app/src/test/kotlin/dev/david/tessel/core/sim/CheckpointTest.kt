package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.content.Upgrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

class CheckpointTest {
    private val idle = Array(MAX_PLAYERS) { PlayerInput() }

    private fun inTheShop(players: Int): World {
        val w = World(seed = 8)
        w.reset(players = players, seed = 8, difficulty = Difficulty.HARD)
        w.spawning = false
        while (w.phase != Phase.SHOP) w.step(idle)
        return w
    }

    @Test
    fun aRunInTheShopComesBackAsItWas() {
        val w = inTheShop(3)
        val pl = w.players
        pl.coins[1] = 500
        pl.levels[1][Upgrades.DAMAGE] = 3
        pl.kills[2] = 42
        pl.dealt[0] = 1234.5f
        pl.taken[2] = 7
        pl.hp[2] = 0
        val cp = w.capture()
        cp.ids[1] = 77L
        cp.names[1] = "Alex"

        val back = Checkpoint.read(cp.write())
        assertNotNull(back)
        assertEquals(77L, back!!.ids[1])
        assertEquals("Alex", back.names[1])
        assertEquals(w.stage + 1, back.nextStage)

        val r = World(seed = 1)
        r.restore(back, seed = 99) { true }
        assertEquals(Phase.SHOP, r.phase)
        assertEquals(w.stage, r.stage)
        assertEquals(w.tick, r.tick)
        assertEquals(Difficulty.HARD, r.difficulty)
        assertEquals(3, r.playerCount)
        assertEquals(500, r.players.coins[1])
        assertEquals(3, r.players.levels[1][Upgrades.DAMAGE])
        assertEquals(42, r.players.kills[2])
        assertEquals(1234.5f, r.players.dealt[0], 0f)
        assertEquals(7, r.players.taken[2])
        // Down when it was saved: back on its feet, at half health, like at any new stage.
        assertEquals(max(1, r.players.maxHp[2] / 2), r.players.hp[2])
        for (p in 0 until 3) for (slot in 0 until SHOP_SLOTS) assertEquals(w.shop.item(p, slot), r.shop.item(p, slot))

        r.nextStage()
        assertEquals(Phase.FIGHT, r.phase)
        assertEquals(w.stage + 1, r.stage)
    }

    @Test
    fun aSeatNobodyFilledSitsOut() {
        val cp = inTheShop(3).capture()
        val r = World(seed = 1)
        r.restore(cp, seed = 3) { it != 2 }
        assertFalse(r.players.present[2])
        assertEquals(0, r.players.hp[2])
        assertEquals(2, r.presentCount())
    }

    @Test
    fun aRunSavedAtItsStartStartsOver() {
        val w = World(seed = 2)
        w.reset(players = 2, seed = 2, difficulty = Difficulty.EASY)
        val cp = w.capture()
        assertFalse(cp.inShop)
        assertEquals(1, cp.nextStage)
        val r = World(seed = 1)
        r.restore(Checkpoint.read(cp.write())!!, seed = 3) { true }
        assertEquals(Phase.FIGHT, r.phase)
        assertEquals(1, r.stage)
        assertEquals(Difficulty.EASY, r.difficulty)
        assertTrue(r.players.alive(1))
    }

    @Test
    fun anythingElseIsNotASave() {
        assertNull(Checkpoint.read(ByteArray(0)))
        assertNull(Checkpoint.read(byteArrayOf(1, 2, 3, 4)))
        assertNull(Checkpoint.read(inTheShop(2).capture().write().copyOf(24)))
    }
}
