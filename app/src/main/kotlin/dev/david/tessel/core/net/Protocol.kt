package dev.david.tessel.core.net

import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.sim.Events
import dev.david.tessel.core.sim.MAX_BULLETS
import dev.david.tessel.core.sim.MAX_COINS
import dev.david.tessel.core.sim.MAX_ENEMIES
import dev.david.tessel.core.sim.MAX_HOSTILE
import dev.david.tessel.core.sim.MAX_PLAYERS
import dev.david.tessel.core.sim.PlayerInput
import dev.david.tessel.core.sim.Phase
import dev.david.tessel.core.sim.REVIVE_TICKS
import dev.david.tessel.core.sim.SHOP_SLOTS
import dev.david.tessel.core.sim.World
import java.nio.ByteBuffer
import kotlin.math.PI

private const val ANGLE_SCALE = (127 / PI).toFloat()
private const val RADIUS_SCALE = 16f

/**
 * Kills and hits beyond this many in one snapshot are dropped. They only make
 * shards, and the cap keeps the worst case under the radio's message limit.
 */
const val SNAPSHOT_EVENTS = 200

/** Largest message Nearby Connections sends as a single bytes payload. */
const val MAX_MESSAGE = 32 * 1024

/** Encoding and decoding of the two messages that carry the game itself. */
object Protocol {
    /**
     * The world as player [forPlayer] needs it. Their own bullets are left
     * out because their phone already draws its own predicted copies.
     * Entity ids travel as their low 16 bits; they only pair up the same body
     * between two snapshots a moment apart, and ids that far apart never meet.
     */
    fun writeSnapshot(buf: ByteBuffer, w: World, paused: Boolean, waiting: Boolean, ready: Int, outbox: Events, forPlayer: Int) {
        Wire.begin(buf, Wire.SNAPSHOT)
        buf.putInt(w.tick)
        buf.put(w.stage.coerceAtMost(255).toByte())
        buf.put(w.phase.ordinal.toByte())
        buf.putInt(w.stageTicks)
        buf.put(((if (paused) 1 else 0) or (if (waiting) 2 else 0)).toByte())
        buf.put(w.difficulty.ordinal.toByte())
        buf.put(w.playerCount.toByte())
        buf.put(ready.toByte())
        val pl = w.players
        var present = 0
        for (p in 0 until w.playerCount) if (pl.present[p]) present = present or (1 shl p)
        buf.put(present.toByte())
        buf.put(w.bossType().toByte())
        buf.put((w.bossHealth() * 255).toInt().coerceIn(0, 255).toByte())

        for (p in 0 until w.playerCount) {
            Wire.putPos(buf, pl.x[p])
            Wire.putPos(buf, pl.y[p])
            Wire.putUnit(buf, pl.aimX[p])
            Wire.putUnit(buf, pl.aimY[p])
            buf.put(pl.hp[p].toByte())
            buf.put(pl.maxHp[p].toByte())
            var flags = 0
            if (pl.hurt[p] > 0) flags = flags or 1
            if (pl.invuln[p] > 0) flags = flags or 2
            buf.put(flags.toByte())
            buf.put((pl.revive[p] * 255 / REVIVE_TICKS).coerceIn(0, 255).toByte())
            buf.putInt(pl.coins[p])
            buf.putInt(pl.earned[p])
            buf.putInt(pl.kills[p])
            buf.putFloat(pl.moveSpeed(p))
            buf.putFloat(pl.fireRate(p))
            buf.putFloat(pl.bulletSpeed(p))
            buf.putFloat(pl.range(p))
            buf.putFloat(pl.bulletRadius(p))
            buf.put(pl.volley(p).toByte())
            buf.put(pl.pierce(p).toByte())
        }

        val e = w.enemies
        buf.putShort(e.count.toShort())
        for (i in 0 until e.count) {
            buf.putShort(e.id[i].toShort())
            buf.put(e.type[i].toByte())
            Wire.putPos(buf, e.x[i])
            Wire.putPos(buf, e.y[i])
            // Top bit: hit flash. The rest: how far along its next attack is.
            val cue = (e.cue[i] * 127).toInt().coerceIn(0, 127)
            buf.put((cue or if (e.flash[i] > 0) 0x80 else 0).toByte())
            buf.put((e.angle[i] * ANGLE_SCALE).toInt().coerceIn(-127, 127).toByte())
        }

        val b = w.bullets
        var shown = 0
        for (i in 0 until b.count) if (b.owner[i] != forPlayer) shown++
        buf.putShort(shown.toShort())
        for (i in 0 until b.count) {
            if (b.owner[i] == forPlayer) continue
            Wire.putPos(buf, b.x[i])
            Wire.putPos(buf, b.y[i])
            buf.putShort(b.vx[i].toInt().toShort())
            buf.putShort(b.vy[i].toInt().toShort())
            buf.put((b.radius[i] * RADIUS_SCALE).toInt().coerceIn(0, 255).toByte())
        }

        val h = w.hostile
        buf.putShort(h.count.toShort())
        for (i in 0 until h.count) {
            Wire.putPos(buf, h.x[i])
            Wire.putPos(buf, h.y[i])
            buf.putShort(h.vx[i].toInt().toShort())
            buf.putShort(h.vy[i].toInt().toShort())
        }

        val c = w.coins
        buf.putShort(c.count.toShort())
        for (i in 0 until c.count) {
            buf.putShort(c.id[i].toShort())
            Wire.putPos(buf, c.x[i])
            Wire.putPos(buf, c.y[i])
        }

        val events = minOf(outbox.count, SNAPSHOT_EVENTS)
        buf.putShort(events.toShort())
        for (i in 0 until events) {
            buf.put(outbox.kind[i].toByte())
            buf.put(outbox.who[i].toByte())
            Wire.putPos(buf, outbox.x[i])
            Wire.putPos(buf, outbox.y[i])
            Wire.putUnit(buf, outbox.dx[i])
            Wire.putUnit(buf, outbox.dy[i])
            buf.put((outbox.angle[i] * ANGLE_SCALE).toInt().coerceIn(-127, 127).toByte())
        }

        val inShop = w.phase == Phase.SHOP
        buf.put((if (inShop) 1 else 0).toByte())
        if (inShop) {
            val s = w.shop
            for (slot in 0 until SHOP_SLOTS) {
                val u = s.item(forPlayer, slot)
                buf.put(u.toByte())
                buf.put((if (u >= 0) pl.levels[forPlayer][u] else 0).toByte())
                buf.putInt(s.price(w, forPlayer, slot))
                buf.put((if (s.maxed(w, forPlayer, slot)) 1 else 0).toByte())
                buf.put((if (s.canBuy(w, forPlayer, slot)) 1 else 0).toByte())
            }
            buf.putInt(s.healPrice(w))
            buf.put(s.healsLeft(forPlayer).toByte())
            buf.put((if (s.canHeal(w, forPlayer)) 1 else 0).toByte())
            buf.putInt(s.rerollPrice(w, forPlayer))
            buf.put((if (s.canReroll(w, forPlayer)) 1 else 0).toByte())
        }
    }

    /** Reads a snapshot written by [writeSnapshot]; [buf] must sit just past the header. */
    fun readSnapshot(buf: ByteBuffer, s: Snapshot) {
        s.tick = buf.int
        s.stage = buf.get().toInt() and 0xFF
        s.phase = Phase.entries[buf.get().toInt()]
        s.stageTicks = buf.int
        val flags = buf.get().toInt()
        s.paused = flags and 1 != 0
        s.waiting = flags and 2 != 0
        s.difficulty = Difficulty.entries[buf.get().toInt()]
        s.playerCount = buf.get().toInt().coerceIn(1, MAX_PLAYERS)
        s.ready = buf.get().toInt() and 0xFF
        s.present = buf.get().toInt() and 0xFF
        s.bossType = buf.get().toInt()
        s.bossHealth = (buf.get().toInt() and 0xFF) / 255f

        for (p in 0 until s.playerCount) {
            s.px[p] = Wire.getPos(buf)
            s.py[p] = Wire.getPos(buf)
            s.aimX[p] = Wire.getUnit(buf)
            s.aimY[p] = Wire.getUnit(buf)
            s.hp[p] = buf.get().toInt()
            s.maxHp[p] = buf.get().toInt()
            val f = buf.get().toInt()
            s.hurt[p] = f and 1 != 0
            s.invuln[p] = f and 2 != 0
            s.revive[p] = (buf.get().toInt() and 0xFF) / 255f
            s.coins[p] = buf.int
            s.earned[p] = buf.int
            s.kills[p] = buf.int
            s.moveSpeed[p] = buf.float
            s.fireRate[p] = buf.float
            s.bulletSpeed[p] = buf.float
            s.range[p] = buf.float
            s.bulletRadius[p] = buf.float
            s.volley[p] = buf.get().toInt()
            s.pierce[p] = buf.get().toInt()
        }

        s.enemyCount = buf.short.toInt().coerceIn(0, MAX_ENEMIES)
        s.enemyIndex.clear()
        for (i in 0 until s.enemyCount) {
            s.enemyId[i] = buf.short.toInt() and 0xFFFF
            s.enemyType[i] = buf.get().toInt()
            s.enemyX[i] = Wire.getPos(buf)
            s.enemyY[i] = Wire.getPos(buf)
            val packed = buf.get().toInt()
            s.enemyFlash[i] = packed and 0x80 != 0
            s.enemyCue[i] = (packed and 0x7F) / 127f
            s.enemyAngle[i] = buf.get() / ANGLE_SCALE
            s.enemyIndex.put(s.enemyId[i], i)
        }

        s.bulletCount = buf.short.toInt().coerceIn(0, MAX_BULLETS)
        for (i in 0 until s.bulletCount) {
            s.bulletX[i] = Wire.getPos(buf)
            s.bulletY[i] = Wire.getPos(buf)
            s.bulletVx[i] = buf.short.toFloat()
            s.bulletVy[i] = buf.short.toFloat()
            s.bulletRadiusOf[i] = (buf.get().toInt() and 0xFF) / RADIUS_SCALE
        }

        s.hostileCount = buf.short.toInt().coerceIn(0, MAX_HOSTILE)
        for (i in 0 until s.hostileCount) {
            s.hostileX[i] = Wire.getPos(buf)
            s.hostileY[i] = Wire.getPos(buf)
            s.hostileVx[i] = buf.short.toFloat()
            s.hostileVy[i] = buf.short.toFloat()
        }

        s.coinCount = buf.short.toInt().coerceIn(0, MAX_COINS)
        s.coinIndex.clear()
        for (i in 0 until s.coinCount) {
            s.coinId[i] = buf.short.toInt() and 0xFFFF
            s.coinX[i] = Wire.getPos(buf)
            s.coinY[i] = Wire.getPos(buf)
            s.coinIndex.put(s.coinId[i], i)
        }

        s.events.clear()
        val events = buf.short.toInt()
        for (i in 0 until events) {
            val kind = buf.get().toInt()
            val who = buf.get().toInt()
            val x = Wire.getPos(buf)
            val y = Wire.getPos(buf)
            val dx = Wire.getUnit(buf)
            val dy = Wire.getUnit(buf)
            val angle = buf.get() / ANGLE_SCALE
            s.events.add(kind, who, x, y, dx, dy, angle)
        }

        s.hasShop = buf.get().toInt() != 0
        if (s.hasShop) {
            for (slot in 0 until SHOP_SLOTS) {
                s.shopItem[slot] = buf.get().toInt()
                s.shopLevel[slot] = buf.get().toInt()
                s.shopPrice[slot] = buf.int
                s.shopMaxed[slot] = buf.get().toInt() != 0
                s.shopAffordable[slot] = buf.get().toInt() != 0
            }
            s.healPrice = buf.int
            s.healsLeft = buf.get().toInt()
            s.canHeal = buf.get().toInt() != 0
            s.rerollPrice = buf.int
            s.canReroll = buf.get().toInt() != 0
        }
    }

    /** What a client sends 30 times a second: its stick, its trigger and where it is. */
    fun writeInput(buf: ByteBuffer, input: PlayerInput, x: Float, y: Float) {
        Wire.begin(buf, Wire.INPUT)
        Wire.putUnit(buf, input.moveX)
        Wire.putUnit(buf, input.moveY)
        Wire.putUnit(buf, input.aimX)
        Wire.putUnit(buf, input.aimY)
        buf.put((if (input.fire) 1 else 0).toByte())
        Wire.putPos(buf, x)
        Wire.putPos(buf, y)
    }

    fun readInput(buf: ByteBuffer, into: PlayerInput) {
        into.moveX = Wire.getUnit(buf)
        into.moveY = Wire.getUnit(buf)
        into.aimX = Wire.getUnit(buf)
        into.aimY = Wire.getUnit(buf)
        into.fire = buf.get().toInt() != 0
        into.hasPosition = true
        into.posX = Wire.getPos(buf)
        into.posY = Wire.getPos(buf)
    }
}
