package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.CoreRules
import dev.david.tessel.core.content.Elite
import dev.david.tessel.core.content.Perks
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The last boss. Its first form lights up squares of a grid, one half of a
 * checkerboard at a time, and takes a perk from each player; the longer it
 * lives and the less health it has, the quicker it gets. Its second form calls
 * the eight other bosses, sends three out at a time, mends any that get low,
 * and keeps stealing. Beat it and all eight to finish the run.
 *
 * The core's own [EnemyPool.mode] says which form: 0 walking in, 1 first,
 * 2 changing, 3 second.
 */
class CoreFight(private val w: World) {
    var strike = STRIKE_NONE
        private set
    var strikeParity = 0
        private set

    /** Which form the core is in: 0 none yet, 1 first, 2 changing, 3 second. */
    var form = 0
        private set

    /** A joining phone shows what the host says. */
    fun show(form: Int, strike: Int, parity: Int, share: Float) {
        this.form = form
        this.strike = strike
        this.strikeParity = parity
        this.warnShare = share
    }

    /** 0..1 through the warning, for the drawing. */
    var warnShare = 0f
        private set

    private var strikeTicks = 0
    private var gapTicks = 0
    private var elapsed = 0
    private var nextTheft = 0
    private var thefts = 0
    private var halfTaken = false
    private var cursor = 0

    /** What each player has lost to the core, as perk masks. */
    val stolen = IntArray(MAX_PLAYERS)

    /** Enemy id in each bubble and what it is doing; 0 for none. */
    private val slotId = IntArray(SLOTS)
    private val slotState = IntArray(SLOTS) { DEAD }

    /** False once the core itself has fallen. */
    var coreAlive = false
        private set

    /** True from the moment the core appears until the fight is over. */
    var active = false
        private set

    fun reset() {
        strike = STRIKE_NONE
        strikeTicks = 0
        gapTicks = 0
        elapsed = 0
        nextTheft = 0
        thefts = 0
        halfTaken = false
        cursor = 0
        warnShare = 0f
        stolen.fill(0)
        slotId.fill(0)
        slotState.fill(DEAD)
        coreAlive = false
        active = false
        form = 0
    }

    /** The core has walked in. */
    fun begin() {
        reset()
        active = true
        coreAlive = true
        gapTicks = secs(2.6f)
    }

    // ---- The core itself ----

    /** One tick of the core at index [i]. Movement is the caller's. */
    fun think(i: Int, rate: Float) {
        val e = w.enemies
        form = e.mode[i]
        when (e.mode[i]) {
            1 -> {
                elapsed++
                if (elapsed == 1) nextTheft = secs(CoreRules.FIRST_THEFT_SECONDS)
                val share = e.hp[i] / e.maxHp[i]
                val hurry = 1f + CoreRules.HURRY_PER_SECOND * (elapsed / TICK_HZ.toFloat()) + CoreRules.HURRY_BY_HEALTH * (1f - share)
                strikes(hurry * rate, 1f)
                if (nextTheft > 0 && --nextTheft == 0) takeFromEveryone()
                if (!halfTaken && share < 0.5f) {
                    halfTaken = true
                    takeFromEveryone()
                }
            }
            2 -> {
                strike = STRIKE_NONE
                warnShare = 0f
                if (e.modeTicks[i] >= secs(CoreRules.TRANSFORM_SECONDS)) {
                    e.mode[i] = 3
                    e.modeTicks[i] = 0
                    elapsed = 0
                    gapTicks = secs(3f)
                    nextTheft = secs(CoreRules.THEFT_GAP * 0.5f)
                }
            }
            3 -> {
                elapsed++
                strikes(rate, CoreRules.SECOND_FORM_SLOWER)
                if (nextTheft > 0 && --nextTheft == 0) {
                    takeFromEveryone()
                    nextTheft = secs(max(CoreRules.THEFT_GAP_MIN, CoreRules.THEFT_GAP - CoreRules.THEFT_GAP_SHRINK * thefts))
                }
            }
        }
        e.cue[i] = if (strike == STRIKE_WARN) warnShare else 0f
    }

    /**
     * A blow that would kill the core: in its first form it changes instead,
     * with a fresh, smaller store of health. True when the blow was absorbed.
     */
    fun intercept(j: Int): Boolean {
        val e = w.enemies
        if (e.type[j] != Bestiary.CORE || e.mode[j] != 1) return false
        e.mode[j] = 2
        e.modeTicks[j] = 0
        val hp = CoreRules.HP_SECOND * w.bossHpScale() * w.devHealth[Bestiary.CORE]
        e.hp[j] = hp
        e.maxHp[j] = hp
        strike = STRIKE_NONE
        w.events.add(Events.CORE_CHANGE, Bestiary.CORE, e.x[j], e.y[j])
        for (k in 0 until SLOTS) {
            val type = Bestiary.bossOrder[k]
            val i = w.spawnEnemy(type, slotX(k), slotY(k))
            if (i < 0) continue
            e.elite[i] = Elite.TETHERED
            e.mode[i] = 1
            val scaled = e.hp[i] * CoreRules.ESCORT_HP
            e.hp[i] = scaled
            e.maxHp[i] = scaled
            slotId[k] = e.id[i]
            slotState[k] = DOCKED
        }
        return true
    }

    /** The core, or one of its eight, has been beaten. */
    fun died(j: Int) {
        val e = w.enemies
        if (e.type[j] == Bestiary.CORE) {
            coreAlive = false
            strike = STRIKE_NONE
            form = 0
            giveBack()
        } else if (e.elite[j] == Elite.TETHERED) {
            val k = slotOf(e.id[j])
            if (k >= 0) slotState[k] = DEAD
        }
    }

    // ---- Strikes ----

    private fun strikes(speed: Float, slower: Float) {
        when (strike) {
            STRIKE_NONE -> {
                if (--gapTicks <= 0) {
                    strike = STRIKE_WARN
                    strikeTicks = secs(CoreRules.WARN_SECONDS)
                    strikeParity = strikeParity xor 1
                    warnShare = 0f
                }
            }
            STRIKE_WARN -> {
                val total = secs(CoreRules.WARN_SECONDS)
                warnShare = 1f - strikeTicks / total.toFloat()
                if (--strikeTicks <= 0) {
                    strike = STRIKE_HIT
                    strikeTicks = secs(CoreRules.HIT_SECONDS)
                    warnShare = 1f
                    land()
                }
            }
            STRIKE_HIT -> {
                // Fades out over the flash.
                warnShare = strikeTicks / secs(CoreRules.HIT_SECONDS).toFloat()
                if (--strikeTicks <= 0) {
                    strike = STRIKE_NONE
                    warnShare = 0f
                    gapTicks = secs(max(CoreRules.STRIKE_GAP_MIN, CoreRules.STRIKE_GAP * slower / speed))
                }
            }
        }
    }

    /** The flash: anyone standing on a lit square takes the hit and is stunned. */
    private fun land() {
        val pl = w.players
        val loss = CoreRules.strikeDamage(w.difficulty)
        for (p in 0 until w.playerCount) {
            if (!pl.alive(p) || pl.invuln[p] > 0) continue
            if (cellParity(pl.x[p], pl.y[p]) != strikeParity) continue
            pl.stun[p] = secs(CoreRules.STUN_SECONDS)
            w.hurtPlayer(p, Cause.shot(Bestiary.CORE), loss)
        }
    }

    // ---- Theft ----

    private fun takeFromEveryone() {
        thefts++
        val pl = w.players
        for (p in 0 until w.playerCount) {
            if (!pl.alive(p)) continue
            val mask = pl.perks[p]
            if (mask == 0) continue
            var pick = w.rng.int(0, Integer.bitCount(mask) - 1)
            var perk = 0
            for (k in 0 until Perks.count) {
                if (mask shr k and 1 == 0) continue
                if (pick-- == 0) {
                    perk = k
                    break
                }
            }
            pl.perks[p] = mask and (1 shl perk).inv()
            stolen[p] = stolen[p] or (1 shl perk)
            if (perk == Perks.THICK_SKIN) {
                pl.maxHp[p] = max(1, pl.maxHp[p] - 2)
                pl.hp[p] = min(pl.hp[p], pl.maxHp[p])
            }
            w.events.add(Events.PERK_STOLEN, p, pl.x[p], pl.y[p], 0f, 0f, 0f, perk)
        }
    }

    private fun giveBack() {
        val pl = w.players
        for (p in 0 until w.playerCount) {
            val mask = stolen[p]
            if (mask == 0) continue
            for (k in 0 until Perks.count) {
                if (mask shr k and 1 == 0) continue
                pl.perks[p] = pl.perks[p] or (1 shl k)
                if (k == Perks.THICK_SKIN) {
                    pl.maxHp[p] += 2
                    if (pl.hp[p] > 0) pl.hp[p] = min(pl.maxHp[p], pl.hp[p] + 2)
                }
                w.events.add(Events.PERK_BACK, p, pl.x[p], pl.y[p], 0f, 0f, 0f, k)
            }
            stolen[p] = 0
        }
    }

    // ---- The eight ----

    /** Where bubble [k] hangs. */
    fun slotX(k: Int) = ARENA_W / 2f + cos(angle(k)) * CoreRules.BUBBLE_RX
    fun slotY(k: Int) = ARENA_H / 2f + sin(angle(k)) * CoreRules.BUBBLE_RY
    private fun angle(k: Int) = k * TWO_PI / SLOTS - HALF_PI

    private fun slotOf(id: Int): Int {
        for (k in 0 until SLOTS) if (slotId[k] == id && slotState[k] != DEAD) return k
        return -1
    }

    private fun indexOf(id: Int): Int {
        val e = w.enemies
        for (i in 0 until e.count) if (e.id[i] == id) return i
        return -1
    }

    /** True while enemy [i] sits in its bubble, out of reach and out of the fight. */
    fun docked(i: Int): Boolean {
        val e = w.enemies
        if (e.elite[i] != Elite.TETHERED) return false
        val k = slotOf(e.id[i])
        return k >= 0 && slotState[k] == DOCKED
    }

    /**
     * Runs before a borrowed boss thinks. Returns true when it should skip
     * its own routine: it is in its bubble, or on its way back to one.
     */
    fun tend(i: Int): Boolean {
        val e = w.enemies
        val k = slotOf(e.id[i])
        if (k < 0) return false
        when (slotState[k]) {
            DOCKED -> {
                e.x[i] = slotX(k)
                e.y[i] = slotY(k)
                e.kx[i] = 0f
                e.ky[i] = 0f
                if (coreAlive && e.hp[i] < e.maxHp[i]) {
                    e.hp[i] = min(e.maxHp[i], e.hp[i] + e.maxHp[i] * CoreRules.MEND_PER_SECOND * TICK_SECONDS)
                }
                return true
            }
            RETURN -> {
                val dx = slotX(k) - e.x[i]
                val dy = slotY(k) - e.y[i]
                val d = sqrt(dx * dx + dy * dy)
                val step = CoreRules.RECALL_SPEED * TICK_SECONDS
                if (d <= step) {
                    e.x[i] = slotX(k)
                    e.y[i] = slotY(k)
                    slotState[k] = DOCKED
                } else {
                    e.x[i] += dx / d * step
                    e.y[i] += dy / d * step
                }
                e.kx[i] = 0f
                e.ky[i] = 0f
                return true
            }
        }
        return false
    }

    /** Once a tick, after everyone has thought: sends bosses out, calls the hurt ones home, ends the fight. */
    fun tick() {
        if (!active) return
        val e = w.enemies
        var out = 0
        var alive = 0
        for (k in 0 until SLOTS) {
            if (slotState[k] == DEAD) continue
            val i = indexOf(slotId[k])
            if (i < 0 || e.hp[i] <= 0f) {
                slotState[k] = DEAD
                continue
            }
            alive++
            if (slotState[k] == OUT) {
                out++
                if (coreAlive && e.hp[i] / e.maxHp[i] < CoreRules.RECALL_BELOW) slotState[k] = RETURN
            } else if (slotState[k] == RETURN) {
                out++
            }
        }
        var tries = 0
        while (out < CoreRules.SENT_OUT && tries < SLOTS) {
            val k = (cursor + tries++) % SLOTS
            if (slotState[k] != DOCKED) continue
            val i = indexOf(slotId[k])
            if (i < 0) continue
            if (coreAlive && e.hp[i] / e.maxHp[i] < CoreRules.READY_AT) continue
            slotState[k] = OUT
            e.mode[i] = 1
            e.modeTicks[i] = 0
            out++
            cursor = (k + 1) % SLOTS
        }
        if (!coreAlive && alive == 0 && w.phase == Phase.FIGHT) {
            active = false
            w.win()
        }
    }

    private fun cellParity(x: Float, y: Float): Int =
        ((x / CoreRules.CELL).toInt() + (y / CoreRules.CELL).toInt()) and 1

    private fun secs(s: Float) = max(1, (s * TICK_HZ).toInt())

    companion object {
        const val STRIKE_NONE = 0
        const val STRIKE_WARN = 1
        const val STRIKE_HIT = 2

        const val SLOTS = 8
        private const val DOCKED = 0
        private const val OUT = 1
        private const val RETURN = 2
        private const val DEAD = 3

        private const val TWO_PI = (2.0 * Math.PI).toFloat()
        private const val HALF_PI = (Math.PI / 2.0).toFloat()
    }
}
