package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Attack
import dev.david.tessel.core.content.Balance
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.EnemyDef
import dev.david.tessel.core.content.Move
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private const val TWO_PI = (2 * PI).toFloat()
private const val TURN = 0.12f
private const val KNOCK_DECAY = 0.86f
private val TELL_TICKS = (Balance.TELL_SECONDS * TICK_HZ).toInt()
private val WINDUP_TICKS = (Balance.DASH_WINDUP * TICK_HZ).toInt()
private val DASH_TICKS = (Balance.DASH_SECONDS * TICK_HZ).toInt()
private val RECOVER_TICKS = (Balance.DASH_RECOVER * TICK_HZ).toInt()

// Steps of the charging routine, kept in EnemyPool.mode.
private const val APPROACH = 0
private const val WINDUP = 1
private const val CHARGE = 2
private const val RECOVER = 3

/** Bosses walk down from the top edge to about here before they start. */
private const val BOSS_ENTRY_Y = 170f

/**
 * Movement and attacks for every enemy, one tick at a time. Each type is a
 * mix of a few moves and attacks from its table row; bosses have their own
 * routines below. Nothing here allocates.
 */
internal class Brains(private val w: World) {
    /** Drones alive this tick; the Apiary is shielded while any fly. */
    var escorts = 0
        private set

    // Desired velocity from the last move, read straight after.
    private var dvx = 0f
    private var dvy = 0f

    /** Sets up a body that just appeared. */
    fun prepare(i: Int) {
        val e = w.enemies
        val def = Bestiary.of(e.type[i])
        val rng = w.rng
        if (def.attack != Attack.NONE) e.timer[i] = seconds(def.every * rng.range(0.6f, 1.1f) + 0.8f)
        e.aux[i] = 0f
        when (def.move) {
            Move.HOVER, Move.ORBIT -> e.aux[i] = rng.sign()
            Move.BOUNCE -> {
                // Heads for a random point inside, so it crosses the arena rather than hugging an edge.
                val a = atan2(rng.range(120f, ARENA_H - 120f) - e.y[i], rng.range(200f, ARENA_W - 200f) - e.x[i])
                e.vx[i] = cos(a)
                e.vy[i] = sin(a)
            }
            Move.PARK -> {
                e.vx[i] = rng.range(150f, ARENA_W - 150f)
                e.vy[i] = rng.range(120f, ARENA_H - 120f)
            }
            Move.ESCORT -> e.aux[i] = rng.range(0f, TWO_PI)
            Move.DASH -> e.timer[i] = seconds(rng.range(0.5f, 1.5f))
            Move.BOSS -> {
                e.timer[i] = seconds(1.2f)
                e.modeTicks[i] = 0
            }
            Move.CHASE -> Unit
        }
    }

    fun think() {
        val e = w.enemies
        escorts = 0
        for (i in 0 until e.count) if (e.type[i] == Bestiary.DRONE && e.hp[i] > 0f) escorts++

        val scale = w.enemySpeedScale()
        val rate = w.attackRate()
        // Things released this tick wait for the next one.
        val n = e.count
        for (i in 0 until n) {
            if (e.hp[i] <= 0f) continue
            val def = Bestiary.of(e.type[i])
            if (e.flash[i] > 0) e.flash[i]--
            val target = w.nearestPlayer(e.x[i], e.y[i])
            dvx = 0f
            dvy = 0f
            when (def.move) {
                Move.CHASE -> chase(i, target, def.speed * scale)
                Move.BOUNCE -> bounce(i, def, def.speed * scale)
                Move.HOVER -> hover(i, def, target, def.speed * scale)
                Move.ORBIT -> orbit(i, def, target, def.speed * scale)
                Move.DASH -> dash(i, def, target, def.speed * scale, rate)
                Move.PARK -> park(i, target, def.speed * scale)
                Move.ESCORT -> escort(i, def, target, def.speed * scale)
                Move.BOSS -> boss(i, def, target, scale, rate)
            }
            e.x[i] += (dvx + e.kx[i]) * TICK_SECONDS
            e.y[i] += (dvy + e.ky[i]) * TICK_SECONDS
            e.kx[i] *= KNOCK_DECAY
            e.ky[i] *= KNOCK_DECAY

            val locked = def.move == Move.DASH && e.mode[i] != APPROACH
            if (def.faces && !locked && def.move != Move.BOSS && target >= 0) {
                e.angle[i] = turn(e.angle[i], angleTo(i, target), TURN)
            } else if (def.spin != 0f) {
                e.angle[i] = wrap(e.angle[i] + def.spin * TICK_SECONDS)
            }

            if (def.attack != Attack.NONE && def.move != Move.BOSS) attack(i, def, target, rate)
        }
    }

    /** What a body leaves behind when it breaks. */
    fun died(j: Int) {
        val e = w.enemies
        val def = Bestiary.of(e.type[j])
        val x = e.x[j]
        val y = e.y[j]
        if (def.child >= 0 && def.children > 0 && def.attack != Attack.SPAWN && !def.boss) {
            val start = w.rng.range(0f, TWO_PI)
            for (k in 0 until def.children) {
                val a = start + k * TWO_PI / def.children
                val c = w.spawnEnemy(def.child, x + cos(a) * def.radius * 0.5f, y + sin(a) * def.radius * 0.5f)
                if (c >= 0) {
                    e.kx[c] = cos(a) * 200f
                    e.ky[c] = sin(a) * 200f
                }
            }
        }
        if (def.deathRing > 0) ring(x, y, def.deathRing, w.rng.range(0f, TWO_PI), 160f, def.radius)
    }

    private fun chase(i: Int, target: Int, speed: Float) {
        if (target < 0) return
        val e = w.enemies
        val dx = w.players.x[target] - e.x[i]
        val dy = w.players.y[target] - e.y[i]
        val d = sqrt(dx * dx + dy * dy)
        if (d < 0.001f) return
        dvx = dx / d * speed
        dvy = dy / d * speed
    }

    private fun bounce(i: Int, def: EnemyDef, speed: Float) {
        val e = w.enemies
        val r = def.radius
        if (e.mode[i] == 0 && inside(i, r)) e.mode[i] = 1
        if (e.mode[i] == 1) {
            if (e.x[i] < r && e.vx[i] < 0f || e.x[i] > ARENA_W - r && e.vx[i] > 0f) e.vx[i] = -e.vx[i]
            if (e.y[i] < r && e.vy[i] < 0f || e.y[i] > ARENA_H - r && e.vy[i] > 0f) e.vy[i] = -e.vy[i]
        }
        dvx = e.vx[i] * speed
        dvy = e.vy[i] * speed
    }

    private fun hover(i: Int, def: EnemyDef, target: Int, speed: Float) {
        if (target < 0) return
        val e = w.enemies
        val dx = w.players.x[target] - e.x[i]
        val dy = w.players.y[target] - e.y[i]
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val ux = dx / d
        val uy = dy / d
        if (!inside(i, def.radius)) {
            dvx = ux * speed
            dvy = uy * speed
            return
        }
        when {
            d > def.range * 1.1f -> {
                dvx = ux * speed
                dvy = uy * speed
            }
            d < def.range * 0.75f -> {
                dvx = -ux * speed * 0.9f
                dvy = -uy * speed * 0.9f
            }
            else -> {
                // Sidestep around the player, turning back at the walls.
                val side = e.aux[i]
                dvx = -uy * side * speed * 0.55f
                dvy = ux * side * speed * 0.55f
                val nx = e.x[i] + dvx * TICK_SECONDS
                val ny = e.y[i] + dvy * TICK_SECONDS
                val r = def.radius
                if (nx < r || nx > ARENA_W - r || ny < r || ny > ARENA_H - r) {
                    e.aux[i] = -side
                    dvx = -dvx
                    dvy = -dvy
                }
            }
        }
        keepIn(i, def.radius)
    }

    private fun orbit(i: Int, def: EnemyDef, target: Int, speed: Float) {
        if (target < 0) return
        val e = w.enemies
        val tx = w.players.x[target]
        val ty = w.players.y[target]
        val a = atan2(e.y[i] - ty, e.x[i] - tx) + e.aux[i] * 0.45f
        seek(i, tx + cos(a) * def.range, ty + sin(a) * def.range, speed)
        if (inside(i, def.radius)) keepIn(i, def.radius)
    }

    private fun dash(i: Int, def: EnemyDef, target: Int, speed: Float, rate: Float) {
        val e = w.enemies
        e.modeTicks[i]++
        when (e.mode[i]) {
            APPROACH -> {
                chase(i, target, speed)
                e.cue[i] = 0f
                if (e.timer[i] > 0) e.timer[i]--
                if (target < 0 || e.timer[i] > 0 || !inside(i, def.radius)) return
                val dx = w.players.x[target] - e.x[i]
                val dy = w.players.y[target] - e.y[i]
                if (dx * dx + dy * dy > def.range * def.range) return
                e.angle[i] = atan2(dy, dx)
                enter(i, WINDUP)
            }
            WINDUP -> {
                e.cue[i] = e.modeTicks[i] / WINDUP_TICKS.toFloat()
                if (e.modeTicks[i] >= WINDUP_TICKS) enter(i, CHARGE)
            }
            CHARGE -> {
                e.cue[i] = 0f
                val s = Balance.DASH_SPEED * w.enemySpeedScale()
                dvx = cos(e.angle[i]) * s
                dvy = sin(e.angle[i]) * s
                if (e.modeTicks[i] >= DASH_TICKS || !inside(i, -def.radius)) enter(i, RECOVER)
            }
            RECOVER -> {
                chase(i, target, speed * 0.35f)
                if (e.modeTicks[i] >= RECOVER_TICKS) {
                    enter(i, APPROACH)
                    e.timer[i] = seconds(1.3f / rate)
                }
            }
        }
    }

    private fun park(i: Int, target: Int, speed: Float) {
        val e = w.enemies
        if (e.mode[i] == 0) {
            seek(i, e.vx[i], e.vy[i], speed)
            val dx = e.vx[i] - e.x[i]
            val dy = e.vy[i] - e.y[i]
            if (dx * dx + dy * dy < 36f) e.mode[i] = 1
        } else {
            // Settled, but still creeping toward the nearest player so it cannot be left behind forever.
            chase(i, target, speed * 0.15f)
        }
    }

    private fun escort(i: Int, def: EnemyDef, target: Int, speed: Float) {
        val b = w.boss
        if (b < 0) {
            chase(i, target, speed)
            return
        }
        val e = w.enemies
        val bx = e.x[b]
        val by = e.y[b]
        val a = atan2(e.y[i] - by, e.x[i] - bx) + 0.5f
        val reach = Bestiary.of(e.type[b]).radius + def.range * 0.5f + 30f * sin(e.aux[i] + w.tick * 0.03f)
        seek(i, bx + cos(a) * reach, by + sin(a) * reach, speed)
    }

    private fun attack(i: Int, def: EnemyDef, target: Int, rate: Float) {
        val e = w.enemies
        if (target < 0 || !inside(i, 0f) || def.move == Move.PARK && e.mode[i] == 0) {
            e.cue[i] = 0f
            return
        }
        if (e.timer[i] > 0) e.timer[i]--
        val tells = def.attack != Attack.SPIRAL
        e.cue[i] = if (tells && e.timer[i] < TELL_TICKS) 1f - e.timer[i] / TELL_TICKS.toFloat() else 0f
        if (e.timer[i] > 0) return
        e.timer[i] = seconds(def.every / rate * w.rng.range(0.85f, 1.15f))
        e.cue[i] = 0f
        val x = e.x[i]
        val y = e.y[i]
        val r = def.radius
        when (def.attack) {
            Attack.AIMED, Attack.FAN -> fan(x, y, angleTo(i, target), def.shots, def.spread, def.shotSpeed, r)
            Attack.RING -> {
                ring(x, y, def.shots, e.aux[i], def.shotSpeed, r)
                e.aux[i] += 0.31f
            }
            Attack.SPIRAL -> {
                ring(x, y, def.shots, e.angle[i], def.shotSpeed, r)
            }
            Attack.SPAWN -> {
                if (e.aux[i] < def.children && w.enemies.count < MAX_ENEMIES - 20) {
                    val a = w.rng.range(0f, TWO_PI)
                    val c = w.spawnEnemy(def.child, x + cos(a) * r, y + sin(a) * r)
                    if (c >= 0) {
                        e.kx[c] = cos(a) * 160f
                        e.ky[c] = sin(a) * 160f
                    }
                    e.aux[i] += 1f
                }
            }
            Attack.NONE -> Unit
        }
    }

    // Bosses. Each walks in from the top, then runs its routine in three
    // phases: above two thirds of its health, above one third, and the rest.

    private fun boss(i: Int, def: EnemyDef, target: Int, scale: Float, rate: Float) {
        val e = w.enemies
        if (e.mode[i] == 0) {
            dvy = 90f
            if (e.y[i] >= BOSS_ENTRY_Y) enter(i, 1)
            return
        }
        e.modeTicks[i]++
        val health = e.hp[i] / e.maxHp[i]
        val phase = if (health > 0.66f) 1 else if (health > 0.33f) 2 else 3
        when (e.type[i]) {
            Bestiary.HEXARCH -> hexarch(i, def, target, scale, rate, phase)
            Bestiary.LANCER -> lancer(i, def, target, scale, rate, phase)
            Bestiary.APIARY -> apiary(i, def, target, scale, rate, phase)
            Bestiary.GYRE -> gyre(i, def, target, scale, rate, phase)
        }
        val charging = e.type[i] == Bestiary.LANCER && e.mode[i] == LANCE_CHARGE
        if (!charging) keepIn(i, def.radius)
    }

    /** Rings of shots with a gap to slip through; calls in help from the second phase. */
    private fun hexarch(i: Int, def: EnemyDef, target: Int, scale: Float, rate: Float, phase: Int) {
        val e = w.enemies
        chase(i, target, def.speed * scale * 0.6f)
        if (e.timer[i] > 0) e.timer[i]--
        e.cue[i] = if (e.timer[i] < TELL_TICKS) 1f - e.timer[i] / TELL_TICKS.toFloat() else 0f
        if (e.timer[i] <= 0) {
            val every = when (phase) {
                1 -> 1.7f
                2 -> 1.45f
                else -> 1.1f
            }
            e.timer[i] = seconds(every / rate)
            val count = 22
            val gap = 4
            val skip = w.rng.int(0, count - 1)
            val turnBy = if (phase == 3 && e.modeTicks[i] / 120 % 2 == 1) -0.4f else 0.4f
            e.aux[i] += turnBy
            val speed = if (phase == 3) 170f else 150f
            for (k in 0 until count) {
                if ((k - skip + count) % count < gap) continue
                shoot(e.x[i], e.y[i], e.aux[i] + k * TWO_PI / count, speed, def.radius)
            }
        }
        if (phase >= 2 && e.modeTicks[i] % seconds(6f) == 0) {
            val helpers = if (phase == 3) 4 else 3
            for (k in 0 until helpers) {
                val a = w.rng.range(0f, TWO_PI)
                w.spawnEnemy(Bestiary.DOT, e.x[i] + cos(a) * def.radius, e.y[i] + sin(a) * def.radius)
            }
            if (phase == 3) w.spawnEnemy(Bestiary.SLAB, e.x[i], e.y[i] + def.radius)
        }
    }

    /** Hangs back firing fans, then aims a line and charges the length of the arena. */
    private fun lancer(i: Int, def: EnemyDef, target: Int, scale: Float, rate: Float, phase: Int) {
        val e = w.enemies
        when (e.mode[i]) {
            LANCE_HOVER -> {
                e.cue[i] = 0f
                if (target >= 0) {
                    val dx = w.players.x[target] - e.x[i]
                    val dy = w.players.y[target] - e.y[i]
                    val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                    val speed = def.speed * scale
                    val toward = if (d > 400f) 1f else if (d < 300f) -1f else 0f
                    dvx = dx / d * speed * toward - dy / d * speed * 0.5f
                    dvy = dy / d * speed * toward + dx / d * speed * 0.5f
                    e.angle[i] = turn(e.angle[i], atan2(dy, dx), TURN)
                }
                if (e.timer[i] > 0) e.timer[i]--
                if (e.timer[i] <= 0 && target >= 0) {
                    e.timer[i] = seconds(1f / rate)
                    fan(e.x[i], e.y[i], e.angle[i], 3, 0.22f, 200f, def.radius)
                }
                if (e.modeTicks[i] >= seconds(2.6f) && target >= 0) {
                    e.angle[i] = angleTo(i, target)
                    enter(i, LANCE_WINDUP)
                }
            }
            LANCE_WINDUP -> {
                val windup = seconds(if (phase == 3) 0.55f else 0.8f)
                e.cue[i] = e.modeTicks[i] / windup.toFloat()
                if (e.modeTicks[i] >= windup) enter(i, LANCE_CHARGE)
            }
            LANCE_CHARGE -> {
                e.cue[i] = 0f
                dvx = cos(e.angle[i]) * 800f
                dvy = sin(e.angle[i]) * 800f
                val r = def.radius
                val hitWall = e.x[i] <= r || e.x[i] >= ARENA_W - r || e.y[i] <= r || e.y[i] >= ARENA_H - r
                if ((hitWall && e.modeTicks[i] > 8) || e.modeTicks[i] >= seconds(1.4f)) {
                    dvx = 0f
                    dvy = 0f
                    keepIn(i, r)
                    if (phase >= 2) ring(e.x[i], e.y[i], 10, w.rng.range(0f, TWO_PI), 170f, r)
                    e.aux[i] += 1f
                    if (phase == 3 && e.aux[i] < 3f && target >= 0) {
                        e.angle[i] = angleTo(i, target)
                        enter(i, LANCE_WINDUP)
                    } else {
                        e.aux[i] = 0f
                        e.timer[i] = seconds(0.6f)
                        enter(i, LANCE_HOVER)
                    }
                }
            }
        }
    }

    /** Releases drones that shield it, and fires fans; rings as well at the end. */
    private fun apiary(i: Int, def: EnemyDef, target: Int, scale: Float, rate: Float, phase: Int) {
        val e = w.enemies
        chase(i, target, def.speed * scale)
        val wave = seconds(if (phase == 1) 7f else 5.5f)
        if (e.modeTicks[i] % wave == seconds(1f) && escorts < 10) {
            val n = if (phase == 1) 5 else 6
            for (k in 0 until n) {
                val a = k * TWO_PI / n
                w.spawnEnemy(Bestiary.DRONE, e.x[i] + cos(a) * def.radius, e.y[i] + sin(a) * def.radius)
            }
        }
        if (e.timer[i] > 0) e.timer[i]--
        e.cue[i] = if (e.timer[i] < TELL_TICKS) 1f - e.timer[i] / TELL_TICKS.toFloat() else 0f
        if (e.timer[i] <= 0 && target >= 0) {
            e.timer[i] = seconds(2.4f / rate)
            if (phase == 3 && e.aux[i] >= 1f) {
                ring(e.x[i], e.y[i], 14, w.rng.range(0f, TWO_PI), 165f, def.radius)
                e.aux[i] = 0f
            } else {
                fan(e.x[i], e.y[i], angleTo(i, target), 5, 0.28f, 180f, def.radius)
                e.aux[i] += 1f
            }
        }
    }

    /** Sits in the middle and turns out spiral arms that reverse every few seconds. */
    private fun gyre(i: Int, def: EnemyDef, target: Int, scale: Float, rate: Float, phase: Int) {
        val e = w.enemies
        if (e.mode[i] == 1) {
            seek(i, ARENA_W / 2f, ARENA_H / 2f, def.speed * scale * 1.5f)
            val dx = ARENA_W / 2f - e.x[i]
            val dy = ARENA_H / 2f - e.y[i]
            if (dx * dx + dy * dy < 100f) enter(i, 2)
            return
        }
        chase(i, target, 8f)
        // Direction flips every 4.5 seconds; the spiral angle lives in aux.
        val dir = if (e.modeTicks[i] / seconds(4.5f) % 2 == 0) 1f else -1f
        e.angle[i] = wrap(e.angle[i] + dir * 0.9f * TICK_SECONDS)
        if (e.timer[i] > 0) e.timer[i]--
        if (e.timer[i] <= 0) {
            e.timer[i] = seconds(0.24f / rate)
            e.aux[i] += dir * 0.19f
            ring(e.x[i], e.y[i], phase + 2, e.aux[i], 140f, def.radius)
        }
        if (phase == 3 && target >= 0 && e.modeTicks[i] % seconds(1.6f) == 0) {
            fan(e.x[i], e.y[i], angleTo(i, target), 3, 0.2f, 190f, def.radius)
        }
    }

    private fun fan(x: Float, y: Float, aim: Float, n: Int, spread: Float, speed: Float, r: Float) {
        for (k in 0 until n) shoot(x, y, aim + (k - (n - 1) / 2f) * spread, speed, r)
    }

    private fun ring(x: Float, y: Float, n: Int, offset: Float, speed: Float, r: Float) {
        for (k in 0 until n) shoot(x, y, offset + k * TWO_PI / n, speed, r)
    }

    private fun shoot(x: Float, y: Float, a: Float, speed: Float, r: Float) {
        w.spawnHostile(x + cos(a) * r * 0.9f, y + sin(a) * r * 0.9f, a, speed)
    }

    private fun seek(i: Int, tx: Float, ty: Float, speed: Float) {
        val e = w.enemies
        val dx = tx - e.x[i]
        val dy = ty - e.y[i]
        val d = sqrt(dx * dx + dy * dy)
        if (d < 0.001f) return
        val s = if (d < speed * TICK_SECONDS) d / TICK_SECONDS else speed
        dvx = dx / d * s
        dvy = dy / d * s
    }

    private fun enter(i: Int, mode: Int) {
        w.enemies.mode[i] = mode
        w.enemies.modeTicks[i] = 0
    }

    /** True when the body is fully inside the arena, shrunk by [margin]. */
    private fun inside(i: Int, margin: Float): Boolean {
        val e = w.enemies
        return e.x[i] >= margin && e.x[i] <= ARENA_W - margin && e.y[i] >= margin && e.y[i] <= ARENA_H - margin
    }

    /** Stops a body that has come in from walking back out. */
    private fun keepIn(i: Int, r: Float) {
        val e = w.enemies
        e.x[i] = e.x[i].coerceIn(r, ARENA_W - r)
        e.y[i] = e.y[i].coerceIn(r, ARENA_H - r)
    }

    private fun angleTo(i: Int, p: Int): Float {
        val e = w.enemies
        return atan2(w.players.y[p] - e.y[i], w.players.x[p] - e.x[i])
    }

    private fun seconds(s: Float) = max(1, (s * TICK_HZ).toInt())

    private fun turn(from: Float, to: Float, rate: Float): Float {
        var delta = to - from
        while (delta > PI) delta -= TWO_PI
        while (delta < -PI) delta += TWO_PI
        return wrap(from + delta * rate)
    }

    private fun wrap(a: Float): Float {
        var out = a
        if (out > PI) out -= TWO_PI else if (out < -PI) out += TWO_PI
        return out
    }

    private companion object {
        const val LANCE_HOVER = 1
        const val LANCE_WINDUP = 2
        const val LANCE_CHARGE = 3
    }
}
