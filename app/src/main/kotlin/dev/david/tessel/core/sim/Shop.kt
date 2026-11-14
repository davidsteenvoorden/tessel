package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Perks
import dev.david.tessel.core.content.Upgrades
import kotlin.math.min
import kotlin.math.roundToInt

const val SHOP_SLOTS = 5

/** Set on a stock entry for a rare upgrade: three levels in one buy, at a higher price. */
const val RARE_BIT = 0x40

/** The shop a few stages in starts offering rare upgrades. */
private const val RARE_FROM_STAGE = 4
private const val RARE_CHANCE = 0.22f
private const val RARE_LEVELS = 3
private const val RARE_PREMIUM = 1.15


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
    private val offers = Array(MAX_PLAYERS) { IntArray(Perks.OFFERED) { -1 } }

    fun open(w: World) {
        for (p in 0 until w.playerCount) {
            rerolls[p] = 0
            heals[p] = 0
            restock(w, p)
            offerPerks(w, p)
        }
    }

    /** After a boss stage a player is offered a choice of perks to take. */
    private fun offerPerks(w: World, p: Int) {
        offers[p].fill(-1)
        if (w.stage % Bestiary.BOSS_EVERY != 0 && w.devBoss < 0) return
        var n = 0
        val free = IntArray(Perks.count)
        for (k in 0 until Perks.count) if (!w.players.has(p, k)) free[n++] = k
        for (slot in 0 until minOf(Perks.OFFERED, n)) {
            val pick = w.rng.int(slot, n - 1)
            val held = free[slot]
            free[slot] = free[pick]
            free[pick] = held
            offers[p][slot] = free[slot]
        }
    }

    /** Perk id offered in [slot], or -1. */
    fun perkOffer(p: Int, slot: Int) = offers[p][slot]

    fun perkPending(p: Int) = offers[p].any { it >= 0 }

    fun pickPerk(w: World, p: Int, slot: Int): Boolean {
        val k = offers.getOrNull(p)?.getOrNull(slot) ?: return false
        if (k < 0) return false
        val pl = w.players
        pl.perks[p] = pl.perks[p] or (1 shl k)
        if (k == Perks.THICK_SKIN) {
            pl.maxHp[p] += 2
            pl.hp[p] = min(pl.hp[p] + 2, pl.maxHp[p])
        }
        offers[p].fill(-1)
        return true
    }

    /** Upgrade id in this slot, or -1 when the player has maxed nearly everything. */
    fun item(p: Int, slot: Int): Int {
        val v = stock[p][slot]
        return if (v < 0) -1 else v and RARE_BIT.inv()
    }

    /** The stock entry itself, with the rare mark, as it travels to a joining phone. */
    fun raw(p: Int, slot: Int) = stock[p][slot]

    fun isRare(p: Int, slot: Int) = stock[p][slot] >= 0 && stock[p][slot] and RARE_BIT != 0

    /** Levels one buy of this slot adds. */
    fun levelsBought(w: World, p: Int, slot: Int): Int {
        val u = item(p, slot)
        if (u < 0) return 0
        val room = Upgrades.of(u).maxLevel - w.players.levels[p][u]
        return if (isRare(p, slot)) minOf(RARE_LEVELS, room) else minOf(1, room)
    }

    fun maxed(w: World, p: Int, slot: Int): Boolean {
        val u = item(p, slot)
        return u < 0 || w.players.levels[p][u] >= Upgrades.of(u).maxLevel
    }

    fun price(w: World, p: Int, slot: Int): Int {
        val u = item(p, slot)
        if (u < 0) return 0
        val level = w.players.levels[p][u]
        var total = 0.0
        for (k in 0 until levelsBought(w, p, slot).coerceAtLeast(1)) total += Upgrades.price(u, level + k, w.stage)
        if (isRare(p, slot)) total *= RARE_PREMIUM
        if (w.players.has(p, Perks.HAGGLER)) total *= 0.9
        return total.roundToInt()
    }

    fun canBuy(w: World, p: Int, slot: Int) = !maxed(w, p, slot) && w.players.coins[p] >= price(w, p, slot)

    fun buy(w: World, p: Int, slot: Int): Boolean {
        if (!canBuy(w, p, slot)) return false
        val u = item(p, slot)
        val pl = w.players
        val n = levelsBought(w, p, slot)
        pl.coins[p] -= price(w, p, slot)
        pl.levels[p][u] += n
        if (u == Upgrades.MAX_HEALTH) {
            pl.maxHp[p] += n
            pl.hp[p] = min(pl.hp[p] + n, pl.maxHp[p])
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

    internal fun keep(cp: Checkpoint) {
        for (p in 0 until cp.players) {
            stock[p].copyInto(cp.stock[p])
            offers[p].copyInto(cp.perkOffer[p])
            cp.rerolls[p] = rerolls[p]
            cp.heals[p] = heals[p]
        }
    }

    /** Puts back the stock a [Checkpoint] kept; a seat with none gets a fresh one. */
    internal fun bringBack(w: World, cp: Checkpoint) {
        for (p in 0 until cp.players) {
            rerolls[p] = cp.rerolls[p]
            heals[p] = cp.heals[p]
            if (cp.stock[p].all { it < 0 }) restock(w, p) else cp.stock[p].copyInto(stock[p])
            cp.perkOffer[p].copyInto(offers[p])
        }
    }

    private fun restock(w: World, p: Int) {
        var n = 0
        for (u in 0 until Upgrades.count) {
            if (w.players.levels[p][u] < Upgrades.of(u).maxLevel) pool[n++] = u
        }
        val rareOk = w.stage >= RARE_FROM_STAGE
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
            val u = pool[slot]
            val room = Upgrades.of(u).maxLevel - w.players.levels[p][u]
            stock[p][slot] = if (rareOk && room >= 2 && w.rng.nextFloat() < RARE_CHANCE) u or RARE_BIT else u
        }
    }
}
