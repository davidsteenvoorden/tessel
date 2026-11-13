package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Upgrades
import kotlin.math.min

const val SHOP_SLOTS = 5

/**
 * The between-stage shop. Each player gets their own random stock of
 * upgrades they have not maxed yet, plus a heal that is always on offer. A
 * slot can be bought again and again while the coins last; every level costs
 * more than the last. Every rule lives here so all phones in co-op agree.
 */
class Shop {
    private val stock = Array(MAX_PLAYERS) { IntArray(SHOP_SLOTS) }
    private val rerolls = IntArray(MAX_PLAYERS)
    private val heals = IntArray(MAX_PLAYERS)
    private val pool = IntArray(Upgrades.count)

    fun open(w: World) {
        for (p in 0 until w.playerCount) {
            rerolls[p] = 0
            heals[p] = 0
            restock(w, p)
        }
    }

    /** Upgrade id in this slot, or -1 when the player has maxed nearly everything. */
    fun item(p: Int, slot: Int) = stock[p][slot]

    fun maxed(w: World, p: Int, slot: Int): Boolean {
        val u = stock[p][slot]
        return u < 0 || w.players.levels[p][u] >= Upgrades.of(u).maxLevel
    }

    fun price(w: World, p: Int, slot: Int): Int {
        val u = stock[p][slot]
        return if (u < 0) 0 else Upgrades.price(u, w.players.levels[p][u], w.stage)
    }

    fun canBuy(w: World, p: Int, slot: Int) = !maxed(w, p, slot) && w.players.coins[p] >= price(w, p, slot)

    fun buy(w: World, p: Int, slot: Int): Boolean {
        if (!canBuy(w, p, slot)) return false
        val u = stock[p][slot]
        val pl = w.players
        pl.coins[p] -= price(w, p, slot)
        pl.levels[p][u]++
        if (u == Upgrades.MAX_HEALTH) {
            pl.maxHp[p]++
            pl.hp[p] = min(pl.hp[p] + 1, pl.maxHp[p])
        }
        return true
    }

    fun healPrice(w: World) = Upgrades.healPrice(w.stage)

    fun healsLeft(p: Int) = Upgrades.HEALS_PER_SHOP - heals[p]

    fun canHeal(w: World, p: Int) =
        healsLeft(p) > 0 && w.players.hp[p] < w.players.maxHp[p] && w.players.coins[p] >= healPrice(w)

    fun heal(w: World, p: Int): Boolean {
        if (!canHeal(w, p)) return false
        val pl = w.players
        pl.coins[p] -= healPrice(w)
        pl.hp[p] = min(pl.hp[p] + Upgrades.HEAL_AMOUNT, pl.maxHp[p])
        heals[p]++
        return true
    }

    fun rerollPrice(w: World, p: Int) = Upgrades.rerollPrice(w.stage, rerolls[p])

    fun canReroll(w: World, p: Int) = w.players.coins[p] >= rerollPrice(w, p)

    fun reroll(w: World, p: Int): Boolean {
        if (!canReroll(w, p)) return false
        w.players.coins[p] -= rerollPrice(w, p)
        rerolls[p]++
        restock(w, p)
        return true
    }

    private fun restock(w: World, p: Int) {
        var n = 0
        for (u in 0 until Upgrades.count) {
            if (w.players.levels[p][u] < Upgrades.of(u).maxLevel) pool[n++] = u
        }
        // Partial shuffle: each slot takes a random pick from what is left.
        for (slot in 0 until SHOP_SLOTS) {
            if (slot >= n) {
                stock[p][slot] = -1
                continue
            }
            val pick = w.rng.int(slot, n - 1)
            val held = pool[slot]
            pool[slot] = pool[pick]
            pool[pick] = held
            stock[p][slot] = pool[slot]
        }
    }
}
