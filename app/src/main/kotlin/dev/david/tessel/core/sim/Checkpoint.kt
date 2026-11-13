package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.content.Upgrades
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A run between stages, kept so a game can stop and carry on another day.
 * Only what lasts from one stage to the next is in it: who plays, their
 * health, coins, upgrades and statistics, and what the shop has on offer.
 * Enemies and shots are not; a run picks up in the shop before the stage it
 * stopped in. One taken at the very start of a run ([inShop] false) starts
 * that run over with the same players.
 */
class Checkpoint(
    val difficulty: Difficulty,
    val stage: Int,
    val tick: Int,
    val players: Int,
    val inShop: Boolean,
    val bossesDown: Int,
) {
    val present = BooleanArray(MAX_PLAYERS)
    val hp = IntArray(MAX_PLAYERS)
    val maxHp = IntArray(MAX_PLAYERS)
    val coins = IntArray(MAX_PLAYERS)
    val earned = IntArray(MAX_PLAYERS)
    val kills = IntArray(MAX_PLAYERS)
    val fired = IntArray(MAX_PLAYERS)
    val dealt = FloatArray(MAX_PLAYERS)
    val taken = IntArray(MAX_PLAYERS)
    val downs = IntArray(MAX_PLAYERS)
    val revived = IntArray(MAX_PLAYERS)
    val levels = Array(MAX_PLAYERS) { IntArray(Upgrades.count) }
    val stock = Array(MAX_PLAYERS) { IntArray(SHOP_SLOTS) { -1 } }
    val rerolls = IntArray(MAX_PLAYERS)
    val heals = IntArray(MAX_PLAYERS)

    /** The stage played next when this run carries on. */
    val nextStage: Int get() = if (inShop) stage + 1 else 1

    /** Who sat in each seat: an id that stays with the phone, and the name it showed. The lobby fills these in. */
    val ids = LongArray(MAX_PLAYERS)
    val names = Array(MAX_PLAYERS) { "" }

    fun write(): ByteArray {
        val buf = ByteBuffer.allocate(64 + players * (120 + Upgrades.count + SHOP_SLOTS)).order(ByteOrder.LITTLE_ENDIAN)
        buf.put('T'.code.toByte())
        buf.put('C'.code.toByte())
        buf.put(FORMAT.toByte())
        buf.put(difficulty.ordinal.toByte())
        buf.putShort(stage.toShort())
        buf.putInt(tick)
        buf.put(players.toByte())
        buf.put((if (inShop) 1 else 0).toByte())
        buf.putShort(bossesDown.toShort())
        buf.put(Upgrades.count.toByte())
        buf.put(SHOP_SLOTS.toByte())
        for (p in 0 until players) {
            buf.put((if (present[p]) 1 else 0).toByte())
            buf.putLong(ids[p])
            val name = names[p].encodeToByteArray().let { it.copyOf(minOf(it.size, 60)) }
            buf.put(name.size.toByte())
            buf.put(name)
            buf.putShort(hp[p].toShort())
            buf.putShort(maxHp[p].toShort())
            buf.putInt(coins[p])
            buf.putInt(earned[p])
            buf.putInt(kills[p])
            buf.putInt(fired[p])
            buf.putFloat(dealt[p])
            buf.putInt(taken[p])
            buf.putInt(downs[p])
            buf.putInt(revived[p])
            for (u in 0 until Upgrades.count) buf.put(levels[p][u].toByte())
            for (slot in 0 until SHOP_SLOTS) buf.put(stock[p][slot].toByte())
            buf.put(rerolls[p].toByte())
            buf.put(heals[p].toByte())
        }
        val out = ByteArray(buf.position())
        buf.flip()
        buf.get(out)
        return out
    }

    companion object {
        /** Bump when the layout changes; older saves are then dropped rather than misread. */
        const val FORMAT = 1

        /** Null when [bytes] are not a save this version can read. */
        fun read(bytes: ByteArray): Checkpoint? {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return try {
                if (buf.get() != 'T'.code.toByte() || buf.get() != 'C'.code.toByte() || buf.get().toInt() != FORMAT) return null
                val difficulty = Difficulty.entries.getOrNull(buf.get().toInt()) ?: return null
                val stage = buf.short.toInt()
                val tick = buf.int
                val players = buf.get().toInt()
                if (players !in 1..MAX_PLAYERS || stage < 1) return null
                val inShop = buf.get().toInt() != 0
                val bosses = buf.short.toInt()
                val upgrades = buf.get().toInt() and 0xFF
                val slots = buf.get().toInt() and 0xFF
                val cp = Checkpoint(difficulty, stage, tick, players, inShop, bosses)
                for (p in 0 until players) {
                    cp.present[p] = buf.get().toInt() != 0
                    cp.ids[p] = buf.long
                    val name = ByteArray(buf.get().toInt() and 0xFF)
                    buf.get(name)
                    cp.names[p] = name.decodeToString()
                    cp.hp[p] = buf.short.toInt()
                    cp.maxHp[p] = buf.short.toInt()
                    cp.coins[p] = buf.int
                    cp.earned[p] = buf.int
                    cp.kills[p] = buf.int
                    cp.fired[p] = buf.int
                    cp.dealt[p] = buf.float
                    cp.taken[p] = buf.int
                    cp.downs[p] = buf.int
                    cp.revived[p] = buf.int
                    // A save from a version with more or fewer kinds of upgrade keeps the ones both know.
                    for (u in 0 until upgrades) {
                        val level = buf.get().toInt()
                        if (u < Upgrades.count) cp.levels[p][u] = level.coerceIn(0, Upgrades.of(u).maxLevel)
                    }
                    for (slot in 0 until slots) {
                        val item = buf.get().toInt()
                        if (slot < SHOP_SLOTS) cp.stock[p][slot] = if (item < Upgrades.count) item else -1
                    }
                    cp.rerolls[p] = buf.get().toInt()
                    cp.heals[p] = buf.get().toInt()
                }
                cp
            } catch (_: BufferUnderflowException) {
                null
            }
        }
    }
}
