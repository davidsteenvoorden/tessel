package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Balance
import dev.david.tessel.core.content.Upgrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopTest {
    private val idle = Array(MAX_PLAYERS) { PlayerInput() }
    private val world = World(seed = 8).apply { spawning = false }
    private val shop get() = world.shop

    private fun openShop(coins: Int) {
        while (world.phase != Phase.SHOP) world.step(idle)
        world.players.coins[0] = coins
    }

    private fun slotOf(upgrade: Int): Int {
        for (s in 0 until SHOP_SLOTS) if (shop.item(0, s) == upgrade) return s
        return -1
    }

    @Test
    fun stockHoldsDifferentUpgrades() {
        openShop(0)
        val items = (0 until SHOP_SLOTS).map { shop.item(0, it) }
        assertEquals(SHOP_SLOTS, items.toSet().size)
        assertTrue(items.all { it in 0 until Upgrades.count })
    }

    @Test
    fun buyingSpendsCoinsAndRaisesTheLevel() {
        openShop(500)
        val u = shop.item(0, 0)
        val price = shop.price(world, 0, 0)
        assertEquals(Upgrades.price(u, 0, world.stage), price)
        assertTrue(shop.buy(world, 0, 0))
        assertEquals(500 - price, world.players.coins[0])
        assertEquals(1, world.players.levels[0][u])
    }

    @Test
    fun theSameSlotCanBeBoughtAgainAtTheNextPrice() {
        openShop(100_000)
        val u = shop.item(0, 0)
        var bought = 0
        while (shop.buy(world, 0, 0)) bought++
        // Coins were no limit here, so it stops only at the last level.
        assertEquals(Upgrades.of(u).maxLevel, bought)
        assertEquals(Upgrades.of(u).maxLevel, world.players.levels[0][u])
        assertTrue(shop.maxed(world, 0, 0))
    }

    @Test
    fun tenFireRateLevelsInOneVisitIfTheCoinsAreThere() {
        openShop(0)
        for (u in 0 until Upgrades.count) world.players.levels[0][u] = Upgrades.of(u).maxLevel
        world.players.levels[0][Upgrades.FIRE_RATE] = 0
        world.players.coins[0] = 100_000
        shop.reroll(world, 0)
        val slot = slotOf(Upgrades.FIRE_RATE)
        repeat(10) { assertTrue(shop.buy(world, 0, slot)) }
        assertEquals(10, world.players.levels[0][Upgrades.FIRE_RATE])
        assertEquals(Balance.FIRE_RATE * 2f, world.players.fireRate(0), 0.001f)
    }

    @Test
    fun cannotBuyWithoutTheCoins() {
        openShop(0)
        assertFalse(shop.buy(world, 0, 0))
        assertEquals(0, world.players.coins[0])
    }

    @Test
    fun pricesClimbWithLevelAndWithStage() {
        assertTrue(Upgrades.price(Upgrades.DAMAGE, 3, 1) > Upgrades.price(Upgrades.DAMAGE, 2, 1))
        assertEquals(Upgrades.of(Upgrades.DAMAGE).basePrice, Upgrades.price(Upgrades.DAMAGE, 0, 1))
        assertTrue(Upgrades.price(Upgrades.DAMAGE, 0, 8) > 2 * Upgrades.price(Upgrades.DAMAGE, 0, 1))
        assertTrue(Upgrades.healPrice(10) > 3 * Upgrades.healPrice(1))
    }

    @Test
    fun rerollCostsMoreEachTimeAndRestocks() {
        openShop(1000)
        val first = shop.rerollPrice(world, 0)
        assertTrue(shop.buy(world, 0, 0))
        val afterBuy = world.players.coins[0]
        assertTrue(shop.reroll(world, 0))
        assertEquals(afterBuy - first, world.players.coins[0])
        assertTrue(shop.rerollPrice(world, 0) > first)
    }

    @Test
    fun healIsCappedPerShopAndAtFullHealth() {
        openShop(1000)
        assertFalse(shop.canHeal(world, 0))
        world.players.hp[0] = 1
        assertTrue(shop.heal(world, 0))
        assertEquals(1 + Upgrades.HEAL_AMOUNT, world.players.hp[0])
        assertTrue(shop.heal(world, 0))
        assertFalse(shop.heal(world, 0))
        assertEquals(0, shop.healsLeft(0))
    }

    @Test
    fun maxedUpgradesLeaveTheStock() {
        openShop(0)
        for (u in 0 until Upgrades.count) world.players.levels[0][u] = Upgrades.of(u).maxLevel
        world.players.levels[0][Upgrades.RANGE] = 0
        world.players.coins[0] = 1000
        shop.reroll(world, 0)
        assertEquals(Upgrades.RANGE, shop.item(0, 0))
        for (s in 1 until SHOP_SLOTS) assertEquals(-1, shop.item(0, s))
    }

    @Test
    fun maxHealthAddsAFilledPip() {
        openShop(0)
        for (u in 0 until Upgrades.count) world.players.levels[0][u] = Upgrades.of(u).maxLevel
        world.players.levels[0][Upgrades.MAX_HEALTH] = 0
        world.players.coins[0] = 1000
        shop.reroll(world, 0)
        val slot = slotOf(Upgrades.MAX_HEALTH)
        world.players.hp[0] = 3
        assertTrue(shop.buy(world, 0, slot))
        assertEquals(Balance.PLAYER_HP + 1, world.players.maxHp[0])
        assertEquals(4, world.players.hp[0])
    }

    @Test
    fun aNewShopResetsTheRerollPrice() {
        openShop(1000)
        val fresh = shop.rerollPrice(world, 0)
        shop.reroll(world, 0)
        assertTrue(shop.rerollPrice(world, 0) > fresh)
        world.nextStage()
        while (world.phase != Phase.SHOP) world.step(idle)
        assertEquals(Upgrades.rerollPrice(world.stage, 0), shop.rerollPrice(world, 0))
    }
}
