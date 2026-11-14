package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Bestiary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevTrialTest {
    private val inputs = arrayOf(PlayerInput())

    private fun world(): World = World(seed = 4).apply { reset(players = 1, seed = 4) }

    @Test
    fun onlyTickedKindsComeAndFromTheFirstStage() {
        val w = world()
        w.devAllow(BooleanArray(Bestiary.size) { it == Bestiary.FANNER })
        repeat(40 * TICK_HZ) { w.step(inputs) }
        assertTrue(w.enemies.count > 0)
        for (i in 0 until w.enemies.count) assertEquals(Bestiary.FANNER, w.enemies.type[i])
    }

    @Test
    fun withNothingTickedNothingComes() {
        val w = world()
        w.devAllow(BooleanArray(Bestiary.size))
        repeat(20 * TICK_HZ) { w.step(inputs) }
        assertEquals(0, w.enemies.count)
    }

    @Test
    fun theChosenBossWalksInAtTheStart() {
        val w = world()
        w.devBoss = Bestiary.GYRE
        w.spawning = false
        w.bringDevBoss()
        repeat(3 * TICK_HZ) { w.step(inputs) }
        assertEquals(Bestiary.GYRE, w.bossType())
    }

    @Test
    fun healthAndDamageMultipliersApply() {
        val w = world()
        w.devHealth[Bestiary.SLAB] = 5f
        val a = w.spawnEnemy(Bestiary.SLAB, 100f, 100f)
        w.devHealth[Bestiary.SLAB] = 1f
        val b = w.spawnEnemy(Bestiary.SLAB, 200f, 100f)
        assertEquals(w.enemies.hp[b] * 5f, w.enemies.hp[a], 0.001f)
    }

    @Test
    fun aHitFromAKindWithMoreDamageCostsMoreHealth() {
        val w = world()
        w.spawning = false
        w.devDamage[Bestiary.DOT] = 3
        val hp = w.players.hp[0]
        w.spawnEnemy(Bestiary.DOT, w.players.x[0] + 2f, w.players.y[0])
        repeat(30) { w.step(inputs) }
        assertEquals(hp - 3, w.players.hp[0])
    }
}
