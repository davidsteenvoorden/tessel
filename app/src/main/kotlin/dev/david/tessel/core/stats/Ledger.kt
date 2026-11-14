package dev.david.tessel.core.stats

import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Difficulty
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One finished run, as this phone's player had it. */
class RunEntry(
    /** Wall clock when it ended, in milliseconds. */
    val endedAt: Long,
    val difficulty: Difficulty,
    val stage: Int,
    val seconds: Int,
    val score: Int,
    val kills: Int,
    val coins: Int,
    val bosses: Int,
    val players: Int,
    /** A [dev.david.tessel.core.sim.Cause] code for what ended the run, or -1 when that is not known. */
    val killedBy: Int,
    /** The player walked away from it instead of losing. */
    val left: Boolean,
    val damage: Int,
    val shots: Int,
    val hitsTaken: Int,
    val downed: Int,
    val revives: Int,
    val upgrades: Int,
)

/** Everything added up over every run. */
class Totals {
    var runs = 0
    var seconds = 0
    var kills = 0
    var damage = 0
    var shots = 0
    var coins = 0
    var hitsTaken = 0
    var downed = 0
    var revives = 0
    var upgrades = 0
    var bosses = 0
    var deepest = 0

    fun add(e: RunEntry) {
        runs++
        seconds += e.seconds
        kills += e.kills
        damage += e.damage
        shots += e.shots
        coins += e.coins
        hitsTaken += e.hitsTaken
        downed += e.downed
        revives += e.revives
        upgrades += e.upgrades
        bosses += e.bosses
        if (e.stage > deepest) deepest = e.stage
    }

    fun copy() = Totals().also {
        it.runs = runs; it.seconds = seconds; it.kills = kills; it.damage = damage; it.shots = shots
        it.coins = coins; it.hitsTaken = hitsTaken; it.downed = downed; it.revives = revives
        it.upgrades = upgrades; it.bosses = bosses; it.deepest = deepest
    }
}

/** The best solo run on one difficulty. */
class Best(val score: Int, val stage: Int, val seconds: Int)

/**
 * What a phone is playing right now: which kinds of enemy it has met, beaten
 * and been beaten by. Filled on the game thread and handed to the [Ledger]
 * whenever the run reaches a safe point, so a long run is never lost whole.
 */
class Tally {
    val seen = BooleanArray(Bestiary.size)
    val firstStage = IntArray(Bestiary.size)
    val defeated = IntArray(Bestiary.size)
    val killedYou = IntArray(Bestiary.size)
    private var dirty = false

    fun see(type: Int, stage: Int) {
        if (type !in 0 until Bestiary.size || seen[type]) return
        seen[type] = true
        firstStage[type] = stage
        dirty = true
    }

    fun defeat(type: Int, stage: Int) {
        if (type !in 0 until Bestiary.size) return
        see(type, stage)
        defeated[type]++
        dirty = true
    }

    fun slain(type: Int, stage: Int) {
        if (type !in 0 until Bestiary.size) return
        see(type, stage)
        killedYou[type]++
        dirty = true
    }

    val isEmpty get() = !dirty

    fun clear() {
        seen.fill(false)
        firstStage.fill(0)
        defeated.fill(0)
        killedYou.fill(0)
        dirty = false
    }
}

/**
 * The player's history on this phone: finished runs, lifetime totals, the best
 * score per difficulty and the logbook of enemies. Kept in one small file.
 */
class Ledger {
    /** Newest first, at most [MAX_RUNS]. */
    val runs = ArrayList<RunEntry>()
    val totals = Totals()
    val best = arrayOfNulls<Best>(Difficulty.entries.size)

    // The logbook, indexed by enemy type.
    val seen = BooleanArray(Bestiary.size)
    val firstStage = IntArray(Bestiary.size)
    val defeated = IntArray(Bestiary.size)
    val killedYou = IntArray(Bestiary.size)

    fun bestOn(difficulty: Difficulty) = best[difficulty.ordinal]

    /** Adds a finished run. True when it is the best solo score on its difficulty so far. */
    fun record(e: RunEntry): Boolean {
        runs.add(0, e)
        while (runs.size > MAX_RUNS) runs.removeAt(runs.size - 1)
        totals.add(e)
        if (e.players > 1 || e.left) return false
        val old = best[e.difficulty.ordinal]
        if (old != null && old.score >= e.score) return false
        best[e.difficulty.ordinal] = Best(e.score, e.stage, e.seconds)
        return true
    }

    /** Folds what a run met and beat into the logbook. */
    fun merge(t: Tally) {
        for (type in 0 until Bestiary.size) {
            if (t.seen[type]) {
                if (!seen[type] || t.firstStage[type] < firstStage[type]) firstStage[type] = t.firstStage[type]
                seen[type] = true
            }
            defeated[type] += t.defeated[type]
            killedYou[type] += t.killedYou[type]
        }
    }

    fun copy(): Ledger {
        val c = Ledger()
        c.runs.addAll(runs)
        c.totals.also { t ->
            val o = totals
            t.runs = o.runs; t.seconds = o.seconds; t.kills = o.kills; t.damage = o.damage; t.shots = o.shots
            t.coins = o.coins; t.hitsTaken = o.hitsTaken; t.downed = o.downed; t.revives = o.revives
            t.upgrades = o.upgrades; t.bosses = o.bosses; t.deepest = o.deepest
        }
        best.copyInto(c.best)
        seen.copyInto(c.seen)
        firstStage.copyInto(c.firstStage)
        defeated.copyInto(c.defeated)
        killedYou.copyInto(c.killedYou)
        return c
    }

    fun write(): ByteArray {
        val buf = ByteBuffer.allocate(256 + Bestiary.size * 16 + runs.size * RUN_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buf.put('T'.code.toByte())
        buf.put('L'.code.toByte())
        buf.put(FORMAT.toByte())
        val t = totals
        for (v in intArrayOf(t.runs, t.seconds, t.kills, t.damage, t.shots, t.coins, t.hitsTaken, t.downed, t.revives, t.upgrades, t.bosses, t.deepest)) {
            buf.putInt(v)
        }
        buf.put(Difficulty.entries.size.toByte())
        for (b in best) {
            buf.put((if (b == null) 0 else 1).toByte())
            buf.putInt(b?.score ?: 0)
            buf.putShort((b?.stage ?: 0).toShort())
            buf.putInt(b?.seconds ?: 0)
        }
        buf.put(Bestiary.size.toByte())
        for (i in 0 until Bestiary.size) {
            buf.put((if (seen[i]) 1 else 0).toByte())
            buf.putShort(firstStage[i].toShort())
            buf.putInt(defeated[i])
            buf.putInt(killedYou[i])
        }
        buf.putShort(runs.size.toShort())
        for (e in runs) {
            buf.putLong(e.endedAt)
            buf.put(e.difficulty.ordinal.toByte())
            buf.putShort(e.stage.toShort())
            buf.putInt(e.seconds)
            buf.putInt(e.score)
            buf.putInt(e.kills)
            buf.putInt(e.coins)
            buf.putShort(e.bosses.toShort())
            buf.put(e.players.toByte())
            buf.put(e.killedBy.toByte())
            buf.put((if (e.left) 1 else 0).toByte())
            buf.putInt(e.damage)
            buf.putInt(e.shots)
            buf.putInt(e.hitsTaken)
            buf.putShort(e.downed.toShort())
            buf.putShort(e.revives.toShort())
            buf.putShort(e.upgrades.toShort())
        }
        val out = ByteArray(buf.position())
        buf.flip()
        buf.get(out)
        return out
    }

    companion object {
        const val FORMAT = 1
        const val MAX_RUNS = 200

        /** Room for one entry in [write]. */
        private const val RUN_BYTES = 56

        /** Null when [bytes] are not a ledger this version can read. */
        fun read(bytes: ByteArray): Ledger? {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return try {
                if (buf.get() != 'T'.code.toByte() || buf.get() != 'L'.code.toByte() || buf.get().toInt() != FORMAT) return null
                val l = Ledger()
                val t = l.totals
                t.runs = buf.int; t.seconds = buf.int; t.kills = buf.int; t.damage = buf.int; t.shots = buf.int
                t.coins = buf.int; t.hitsTaken = buf.int; t.downed = buf.int; t.revives = buf.int
                t.upgrades = buf.int; t.bosses = buf.int; t.deepest = buf.int
                val difficulties = buf.get().toInt()
                for (d in 0 until difficulties) {
                    val has = buf.get().toInt() != 0
                    val score = buf.int
                    val stage = buf.short.toInt()
                    val seconds = buf.int
                    if (has && d < l.best.size) l.best[d] = Best(score, stage, seconds)
                }
                val kinds = buf.get().toInt() and 0xFF
                for (i in 0 until kinds) {
                    val s = buf.get().toInt() != 0
                    val first = buf.short.toInt()
                    val d = buf.int
                    val k = buf.int
                    // A version with more or fewer kinds of enemy keeps the ones both know.
                    if (i < Bestiary.size) {
                        l.seen[i] = s
                        l.firstStage[i] = first
                        l.defeated[i] = d
                        l.killedYou[i] = k
                    }
                }
                val n = buf.short.toInt()
                for (i in 0 until n) {
                    val endedAt = buf.long
                    val difficulty = Difficulty.entries.getOrNull(buf.get().toInt()) ?: return null
                    val stage = buf.short.toInt()
                    val seconds = buf.int
                    val score = buf.int
                    val kills = buf.int
                    val coins = buf.int
                    val bosses = buf.short.toInt()
                    val players = buf.get().toInt()
                    val killedBy = buf.get().toInt()
                    val left = buf.get().toInt() != 0
                    val damage = buf.int
                    val shots = buf.int
                    val hits = buf.int
                    val downed = buf.short.toInt()
                    val revives = buf.short.toInt()
                    val upgrades = buf.short.toInt()
                    l.runs.add(RunEntry(endedAt, difficulty, stage, seconds, score, kills, coins, bosses, players, killedBy, left, damage, shots, hits, downed, revives, upgrades))
                }
                l
            } catch (_: BufferUnderflowException) {
                null
            }
        }
    }
}
