package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Elite
import dev.david.tessel.core.content.Perks
import dev.david.tessel.core.content.Scoring
import dev.david.tessel.core.content.Upgrades
import dev.david.tessel.core.net.Echo
import dev.david.tessel.core.net.MAX_MESSAGE
import dev.david.tessel.core.net.Protocol
import dev.david.tessel.core.net.Snapshot
import dev.david.tessel.core.net.Wire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerksAndElitesTest {
    private val inputs = arrayOf(PlayerInput())

    private fun world(stage: Int = 1): World {
        val w = World(seed = 12)
        w.reset(players = 1, seed = 12)
        w.spawning = false
        if (stage > 1) w.skipTo(stage)
        return w
    }

    @Test
    fun aBossStageShopOffersThreePerksAndPickingOneKeepsIt() {
        val w = world(5)
        w.shop.open(w)
        assertTrue(w.shop.perkPending(0))
        val offered = (0 until 3).map { w.shop.perkOffer(0, it) }
        assertEquals(3, offered.toSet().size)
        assertTrue(w.shop.pickPerk(w, 0, 1))
        assertTrue(w.players.has(0, offered[1]))
        assertFalse(w.shop.perkPending(0))
    }

    @Test
    fun otherStagesOfferNoPerk() {
        val w = world(4)
        w.shop.open(w)
        assertFalse(w.shop.perkPending(0))
    }

    @Test
    fun thickSkinAddsTwoPips() {
        val w = world()
        w.players.perks[0] = 0
        val hp = w.players.maxHp[0]
        w.devPerks(setOf(Perks.THICK_SKIN))
        assertEquals(hp + 2, w.players.maxHp[0])
        assertEquals(hp + 2, w.players.hp[0])
    }

    @Test
    fun rareUpgradesTurnUpFromStageFourAndBuyThreeLevels() {
        val w = world(6)
        w.players.coins[0] = 1_000_000
        var slot = -1
        repeat(200) {
            if (slot < 0) {
                w.shop.reroll(w, 0)
                slot = (0 until SHOP_SLOTS).firstOrNull { w.shop.isRare(0, it) } ?: -1
            }
        }
        assertTrue("a rare upgrade showed up", slot >= 0)
        val u = w.shop.item(0, slot)
        val before = w.players.levels[0][u]
        val coins = w.players.coins[0]
        val single = Upgrades.price(u, before, w.stage)
        assertTrue(w.shop.price(w, 0, slot) > single * 3)
        assertTrue(w.shop.buy(w, 0, slot))
        assertEquals(before + 3, w.players.levels[0][u])
        assertEquals(coins - w.shop.price(w, 0, slot).let { coins - w.players.coins[0] }, w.players.coins[0])
    }

    @Test
    fun noRareUpgradesInTheEarlyShops() {
        val w = world(2)
        w.players.coins[0] = 1_000_000
        repeat(100) {
            w.shop.reroll(w, 0)
            for (s in 0 until SHOP_SLOTS) assertFalse(w.shop.isRare(0, s))
        }
    }

    @Test
    fun secondWindSavesFromADownedHitOncePerStage() {
        val w = world()
        w.devPerks(setOf(Perks.SECOND_WIND))
        w.players.hp[0] = 1
        fun hit() {
            w.players.invuln[0] = 0
            w.spawnEnemy(Bestiary.DOT, w.players.x[0] + 1f, w.players.y[0])
            repeat(5) { w.step(inputs) }
        }
        hit()
        assertEquals(1, w.players.hp[0])
        assertTrue(w.players.alive(0))
        w.enemies.clear()
        hit()
        assertFalse("the second hit in the stage downs", w.players.alive(0))
    }

    @Test
    fun anArmoredEliteTakesHalfTheDamageAndEveryEliteHasMoreHealth() {
        val w = world()
        val plain = w.spawnEnemy(Bestiary.SLAB, 300f, 100f)
        val armored = w.spawnEnemy(Bestiary.SLAB, 600f, 100f)
        w.makeElite(armored, Elite.ARMORED)
        assertEquals(w.enemies.hp[plain] * Elite.HEALTH, w.enemies.hp[armored], 0.001f)
    }

    @Test
    fun aSplittingEliteLeavesPipsAndPaysMore() {
        val w = world()
        w.players.x[0] = 100f
        w.players.y[0] = 100f
        val i = w.spawnEnemy(Bestiary.SLAB, 800f, 300f)
        w.makeElite(i, Elite.SPLITTING)
        w.enemies.hp[i] = 0.01f
        val bullets = w.bullets
        bullets.add(1, 800f, 300f, 1f, 0f, 30, 5f, 8f, 0, 0)
        val before = w.players.points[0]
        repeat(3) { w.step(inputs) }
        assertTrue(w.players.points[0] - before >= 3 * Scoring.kill(Bestiary.SLAB))
        var pips = 0
        for (k in 0 until w.enemies.count) if (w.enemies.type[k] == Bestiary.PIP) pips++
        assertEquals(3, pips)
    }

    @Test
    fun perkStatsApply() {
        val w = world()
        val base = w.players.moveSpeed(0)
        w.devPerks(setOf(Perks.QUICK_FEET, Perks.HEAVY_ROUNDS, Perks.MAGNET_CORE))
        assertTrue(w.players.moveSpeed(0) > base)
        assertEquals(1, w.players.pierce(0))
    }

    @Test
    fun eliteTraitsPerksAndRareStockTravelAndSave() {
        val w = world(5)
        w.players.perks[0] = 1 shl Perks.SIPHON
        w.shop.open(w)
        val i = w.spawnEnemy(Bestiary.DRIFTER, 500f, 200f)
        w.makeElite(i, Elite.FAST)
        w.step(inputs)
        val buf = Wire.buffer(MAX_MESSAGE * 2)
        w.shop.let { }
        Protocol.writeSnapshot(buf, w, false, false, 0, Events(), Events(), Events(), 0, Echo())
        val s = Snapshot()
        Protocol.readSnapshot(Wire.open(Wire.finish(buf)), s)
        assertEquals(Elite.FAST, s.enemyElite[0])
        assertEquals(Bestiary.DRIFTER, s.enemyType[0])
        assertTrue(s.perks[0] shr Perks.SIPHON and 1 != 0)

        val cp = w.capture()
        val back = Checkpoint.read(cp.write())!!
        assertEquals(cp.perks[0], back.perks[0])
        assertEquals(cp.perkOffer[0].toList(), back.perkOffer[0].toList())
    }
}
