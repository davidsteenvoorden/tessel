package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Balance
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.rng.Rng

private const val HEAD_START = 2.5f
private const val FIRST_AT = 1f
private const val SAFE_DISTANCE = 380f
private const val EDGE_PAD = 8f
private const val PERIMETER = 2 * (ARENA_W + ARENA_H)

/** Pips join partway through the first stage. */
private const val PIPS_FROM = 20f

/** In the stage a new kind first turns up, it turns up a lot, so it gets noticed. */
private const val DEBUT_BOOST = 1.8f

/**
 * Decides what walks in and when. It earns a threat budget every tick, picks
 * the next group, and lets it in once the budget covers it. Groups enter from
 * an edge away from the players so nothing appears on top of anyone.
 *
 * The rate starts gentle and climbs twice over: a little across each stage,
 * and a lot from one stage to the next. New kinds join as the stages go by;
 * see [EnemyDef.from] in the bestiary.
 */
class Director {
    private var budget = 0f
    private var type = -1
    private var size = 0
    private var cost = 0f

    // Last edge point picked. Fields rather than a returned pair so picking allocates nothing.
    private var ex = 0f
    private var ey = 0f
    private var alongX = 0f
    private var alongY = 0f
    private var outX = 0f
    private var outY = 0f

    fun startStage() {
        budget = HEAD_START
        type = -1
    }

    fun update(w: World) {
        val t = w.stageTicks * TICK_SECONDS
        if (w.stage == 1 && t < FIRST_AT) return
        val bossScale = if (w.isBossStage()) Balance.BOSS_STAGE_SPAWNS else 1f
        budget += ratePerSecond(w.stage, w.stageProgress()) * w.difficulty.spawn * w.crowd() * bossScale * TICK_SECONDS
        if (type < 0) choose(w.rng, w.stage, t)
        val cap = Balance.SOFT_CAP + Balance.SOFT_CAP_PER_PLAYER * (w.presentCount() - 1)
        if (budget < cost || w.enemies.count + size > cap) return
        place(w)
        budget -= cost
        type = -1
    }

    /** Threat per second; one Dot costs one. */
    fun ratePerSecond(stage: Int, progress: Float) = (0.6f + 0.3f * (stage - 1)) * (0.8f + 0.4f * progress)

    /** How likely [type] is to be picked at [seconds] into [stage]; 0 when it is not on offer yet. */
    fun weight(type: Int, stage: Int, seconds: Float): Float {
        val def = Bestiary.of(type)
        return when {
            def.boss || def.from == 0 || def.from > stage -> 0f
            type == Bestiary.PIP && stage == 1 && seconds < PIPS_FROM -> 0f
            def.from == stage && stage > 1 -> def.weight * DEBUT_BOOST
            else -> def.weight
        }
    }

    private fun choose(rng: Rng, stage: Int, t: Float) {
        var total = 0f
        for (k in 0 until Bestiary.size) total += weight(k, stage, t)
        var roll = rng.nextFloat() * total
        type = Bestiary.DOT
        for (k in 0 until Bestiary.size) {
            val wk = weight(k, stage, t)
            if (wk <= 0f) continue
            if (roll < wk) {
                type = k
                break
            }
            roll -= wk
        }
        val def = Bestiary.of(type)
        // Kinds that have been around a while come in larger groups; cheap ones grow faster.
        val age = stage - def.from
        val growth = if (def.cost >= 4f) age / 6 else age / 3
        size = def.group + rng.int(0, def.groupSpread) + if (def.pack) growth * 2 else growth
        cost = size * def.cost
    }

    private fun place(w: World) {
        val def = Bestiary.of(type)
        val r = def.radius
        if (def.pack) {
            // A pack comes through one gap in a loose cluster.
            edgePoint(w, r)
            val bx = ex
            val by = ey
            repeat(size) {
                val along = w.rng.range(-70f, 70f)
                val out = w.rng.range(0f, 40f)
                w.spawnEnemy(type, bx + alongX * along + outX * out, by + alongY * along + outY * out)
            }
        } else {
            repeat(size) {
                edgePoint(w, r)
                w.spawnEnemy(type, ex, ey)
            }
        }
    }

    /** Picks a point just outside the arena edge, preferring ones far from every player. */
    private fun edgePoint(w: World, r: Float) {
        val out = r + EDGE_PAD
        for (attempt in 0 until 6) {
            val s = w.rng.nextFloat() * PERIMETER
            when {
                s < ARENA_W -> at(s, -out, 1f, 0f, 0f, -1f)
                s < ARENA_W + ARENA_H -> at(ARENA_W + out, s - ARENA_W, 0f, 1f, 1f, 0f)
                s < 2 * ARENA_W + ARENA_H -> at(s - ARENA_W - ARENA_H, ARENA_H + out, 1f, 0f, 0f, 1f)
                else -> at(-out, s - 2 * ARENA_W - ARENA_H, 0f, 1f, -1f, 0f)
            }
            if (w.nearestPlayer(ex, ey, SAFE_DISTANCE) < 0) return
        }
    }

    private fun at(x: Float, y: Float, ax: Float, ay: Float, ox: Float, oy: Float) {
        ex = x
        ey = y
        alongX = ax
        alongY = ay
        outX = ox
        outY = oy
    }
}
