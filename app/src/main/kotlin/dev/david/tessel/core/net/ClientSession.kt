package dev.david.tessel.core.net

import dev.david.tessel.core.content.Balance
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.sim.ARENA_H
import dev.david.tessel.core.sim.ARENA_W
import dev.david.tessel.core.sim.BulletPool
import dev.david.tessel.core.sim.Events
import dev.david.tessel.core.sim.GRAZE
import dev.david.tessel.core.sim.HOSTILE_TICKS
import dev.david.tessel.core.sim.INVULN_TICKS
import dev.david.tessel.core.sim.MAX_BULLETS
import dev.david.tessel.core.sim.MAX_HOSTILE
import dev.david.tessel.core.sim.Phase
import dev.david.tessel.core.sim.PlayerInput
import dev.david.tessel.core.sim.REVIVE_TICKS
import dev.david.tessel.core.sim.TICK_HZ
import dev.david.tessel.core.sim.TICK_SECONDS
import dev.david.tessel.core.sim.World
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private const val RING = 16
private const val STEP_NANOS = 1_000_000_000L / TICK_HZ

/**
 * Drawing runs this far behind the host so there is usually a snapshot on each
 * side of "now". It follows how often snapshots actually arrive: about one and
 * a half gaps, never under 50 ms or over 220 ms.
 */
private const val MIN_DELAY_NANOS = 50_000_000L
private const val MAX_DELAY_NANOS = 220_000_000L
private const val FIRST_GAP_NANOS = 33_000_000L

/** Lets the clock estimate drift upward when the link gets slower. */
private const val CREEP_NANOS = 250_000L

/** Snapshots this much later than expected, several in a row, mean the host clock jumped (pause, restart). */
private const val RESYNC_NANOS = 250_000_000L
private const val SNAP_DISTANCE = 120f
private const val TWO_PI = (2 * PI).toFloat()
private const val HURT_TICKS = 8
private const val FLASHES = 32
private const val FLASH_STEPS = 3
private const val MARGIN = 24f

/**
 * A joining phone's side of a co-op game. It never simulates enemies: it
 * keeps the last few host snapshots and draws the world a moment in the past
 * so movement blends smoothly between them.
 *
 * Its own player lives in the present. Movement and shots are run here, and
 * so is the judgement of what those shots hit and what hits the player: this
 * phone checks against exactly what it draws and tells the host, which takes
 * its word. So nothing the player sees is overruled by the link's delay.
 * Everyone else's shots are made here from their shot events, and enemy
 * shots are flown here from the moment the host says they were fired.
 */
class ClientSession(val me: Int, private var players: Int, private val send: (ByteArray, Boolean) -> Boolean) {
    /** What gets drawn: a world filled from snapshots each frame, never stepped. */
    val view = World(seed = 0).apply { spawning = false }

    private val ring = Array(RING) { Snapshot() }
    private var newest = -1
    private var stored = 0
    private var offsetNanos = Long.MAX_VALUE
    private var lateCount = 0
    private var arrivals = 0L
    private var eventsThrough = 0L
    private var lastArrival = 0L
    private var gapNanos = FIRST_GAP_NANOS
    private val pilot = Pilot()
    private val buf = Wire.buffer(1024)
    private var inputCountdown = 0
    private val reports = Reports()
    private val drift = Drift(MAX_HOSTILE)
    private val ghosts = BulletPool(MAX_BULLETS)
    private var ghostId = -1_000_000
    private var renderTick = 0f
    private var localInvuln = 0
    private var localHurt = 0

    // Enemies this phone's shots just hit flash at once, before the host has heard.
    private val flashId = IntArray(FLASHES)
    private val flashUntil = IntArray(FLASHES)
    private var flashNext = 0
    private var steps = 0

    /** Most recent snapshot, for menus: phase, pause, shop, scores. */
    val latest: Snapshot? get() = if (newest < 0) null else ring[newest]

    /** How far behind the host the drawing runs right now, in nanoseconds. */
    val delayNanos: Long get() = (gapNanos * 3 / 2 + 12_000_000L).coerceIn(MIN_DELAY_NANOS, MAX_DELAY_NANOS)

    fun reset() {
        newest = -1
        stored = 0
        offsetNanos = Long.MAX_VALUE
        lateCount = 0
        eventsThrough = arrivals
        lastArrival = 0L
        pilot.reset()
        drift.clear()
        ghosts.clear()
        reports.clear()
        localInvuln = 0
        localHurt = 0
        view.reset(players = players, seed = 0)
        view.events.clear()
    }

    fun receive(bytes: ByteArray, nowNanos: Long) {
        if (Wire.typeOf(bytes) != Wire.SNAPSHOT) return
        val slot = (newest + 1) % RING
        val s = ring[slot]
        val previous = latest
        val previousTick = previous?.tick ?: -1
        Protocol.readSnapshot(Wire.open(bytes), s)
        s.arrivalNanos = nowNanos
        s.seq = ++arrivals
        if (previous != null && s.tick < previousTick) {
            // A new run on the host: the old snapshots belong to a different game.
            stored = 0
            eventsThrough = s.seq - 1
            offsetNanos = Long.MAX_VALUE
            pilot.reset()
            drift.clear()
            ghosts.clear()
        }
        newest = slot
        if (stored < RING) stored++
        players = s.playerCount

        for (i in 0 until s.bornCount) drift.add(s.bornId[i], s.bornX[i], s.bornY[i], s.bornVx[i], s.bornVy[i], s.bornTick[i])
        for (i in 0 until s.goneCount) drift.remove(s.goneId[i])
        // The host sweeps every enemy shot away when the shop opens.
        if (s.phase == Phase.SHOP || s.phase == Phase.OVER) drift.clear()

        if (lastArrival != 0L) {
            val gap = (nowNanos - lastArrival).coerceAtMost(MAX_DELAY_NANOS)
            gapNanos = (gapNanos * 7 + gap) / 8
        }
        lastArrival = nowNanos

        val sample = nowNanos - s.tick * STEP_NANOS
        if (offsetNanos == Long.MAX_VALUE) {
            offsetNanos = sample
        } else if (sample - offsetNanos > RESYNC_NANOS) {
            if (++lateCount >= 5) {
                offsetNanos = sample
                lateCount = 0
            }
        } else {
            lateCount = 0
            offsetNanos = minOf(sample, offsetNanos + CREEP_NANOS)
        }
    }

    /** One 60 Hz step of the local player; sends input every other step, and at once when there is news. */
    fun tick(input: PlayerInput) {
        val s = latest ?: return
        val hostX = s.px[me]
        val hostY = s.py[me]
        if (!pilot.placed) pilot.place(hostX, hostY)
        val dx = hostX - pilot.x
        val dy = hostY - pilot.y
        // The host only lags a little behind us; a big gap means it moved us (new run, new stage).
        if (dx * dx + dy * dy > SNAP_DISTANCE * SNAP_DISTANCE) pilot.place(hostX, hostY)

        steps++
        if (pilot.step(input, s, me)) view.events.add(Events.SHOT, me)
        val before = reports.hits
        pilot.cull(view, reports)
        for (k in before until reports.hits) {
            flashId[flashNext] = reports.hitId[k]
            flashUntil[flashNext] = steps + FLASH_STEPS
            flashNext = (flashNext + 1) % FLASHES
        }
        flyGhosts()
        if (localInvuln > 0) localInvuln--
        if (localHurt > 0) localHurt--
        if (s.hp[me] > 0 && !s.invuln[me] && localInvuln == 0) checkHurt()

        val news = !reports.isEmpty
        if (--inputCountdown <= 0 || news) {
            inputCountdown = 2
            Protocol.writeInput(buf, input, pilot.x, pilot.y, reports)
            // Hits and hurts must arrive, so they skip the queue for latest-only messages.
            if (send(Wire.finish(buf), !news)) reports.clear()
        }
    }

    fun action(kind: Int, arg: Int = 0) {
        send(Wire.action(kind, arg), false)
    }

    /** Fills [view] for drawing at [nowNanos]. */
    fun frame(nowNanos: Long) {
        val top = latest ?: return
        renderTick = if (offsetNanos == Long.MAX_VALUE) top.tick.toFloat()
        else (nowNanos - delayNanos - offsetNanos).toFloat() / STEP_NANOS

        // a: newest snapshot at or before the render time. b: the one after it, if any.
        var a: Snapshot? = null
        var b: Snapshot? = null
        for (k in 0 until stored) {
            val s = ring[(newest - k + RING) % RING]
            if (s.tick <= renderTick) {
                a = s
                break
            }
            b = s
        }
        if (a == null) {
            a = b ?: top
            b = null
        }
        val t = if (b == null || b.tick == a.tick) 0f else ((renderTick - a.tick) / (b.tick - a.tick)).coerceIn(0f, 1f)
        val from = a
        val to = b ?: a

        val v = view
        val stageTicks = from.stageTicks + (to.stageTicks - from.stageTicks) * t
        v.adopt(floor(renderTick).toInt().coerceAtLeast(0), top.stage, top.phase, stageTicks.toInt(), top.playerCount, top.difficulty, top.bossType, top.bossHealth)

        val pl = v.players
        for (p in 0 until top.playerCount) {
            pl.present[p] = top.present and (1 shl p) != 0
            if (p == me) {
                pl.x[p] = if (pilot.placed) pilot.x else top.px[p]
                pl.y[p] = if (pilot.placed) pilot.y else top.py[p]
                pl.aimX[p] = pilot.aimX
                pl.aimY[p] = pilot.aimY
            } else {
                pl.x[p] = from.px[p] + (to.px[p] - from.px[p]) * t
                pl.y[p] = from.py[p] + (to.py[p] - from.py[p]) * t
                pl.aimX[p] = to.aimX[p]
                pl.aimY[p] = to.aimY[p]
            }
            pl.prevX[p] = pl.x[p]
            pl.prevY[p] = pl.y[p]
            // Scores and health come from the newest snapshot: menus and pips should not lag.
            pl.hp[p] = top.hp[p]
            pl.maxHp[p] = top.maxHp[p]
            pl.hurt[p] = if (top.hurt[p]) 1 else 0
            pl.invuln[p] = if (top.invuln[p]) 1 else 0
            pl.revive[p] = (top.revive[p] * REVIVE_TICKS).toInt()
            pl.coins[p] = top.coins[p]
            pl.earned[p] = top.earned[p]
            pl.kills[p] = top.kills[p]
        }
        // This phone knew about its own hits first; show them now rather than when the host agrees.
        if (localHurt > 0) pl.hurt[me] = localHurt
        if (localInvuln > 0) pl.invuln[me] = localInvuln

        val e = v.enemies
        e.clear()
        for (i in 0 until to.enemyCount) {
            var x = to.enemyX[i]
            var y = to.enemyY[i]
            var angle = to.enemyAngle[i]
            val j = from.enemyIndex[to.enemyId[i]]
            if (j >= 0 && from !== to) {
                x = from.enemyX[j] + (x - from.enemyX[j]) * t
                y = from.enemyY[j] + (y - from.enemyY[j]) * t
                var turn = angle - from.enemyAngle[j]
                if (turn > PI) turn -= TWO_PI else if (turn < -PI) turn += TWO_PI
                angle = from.enemyAngle[j] + turn * t
            }
            val k = e.add(to.enemyId[i], to.enemyType[i], x, y, 1f)
            if (k >= 0) {
                e.flash[k] = if (to.enemyFlash[i] || justHit(to.enemyId[i])) 1 else 0
                e.angle[k] = angle
                e.cue[k] = to.enemyCue[i]
            }
        }

        val bullets = v.bullets
        bullets.clear()
        for (i in 0 until ghosts.count) {
            bullets.add(ghosts.id[i], ghosts.x[i], ghosts.y[i], ghosts.vx[i], ghosts.vy[i], 1, 0f, ghosts.radius[i], 0, ghosts.owner[i])
        }
        val shots = pilot.shots
        for (i in 0 until shots.count) {
            bullets.add(shots.id[i], shots.x[i], shots.y[i], shots.vx[i], shots.vy[i], 1, 0f, shots.radius[i], 0, me)
        }

        val h = v.hostile
        h.clear()
        var i = 0
        while (i < drift.count) {
            val age = renderTick - drift.born[i]
            val x = drift.x(i, renderTick)
            val y = drift.y(i, renderTick)
            if (age > HOSTILE_TICKS || x < -MARGIN || x > ARENA_W + MARGIN || y < -MARGIN || y > ARENA_H + MARGIN) {
                drift.removeAt(i)
                continue
            }
            if (age >= 0f) h.add(drift.id[i], x, y, drift.vx[i], drift.vy[i], 1, 0f, Balance.HOSTILE_RADIUS, 0, -1)
            i++
        }

        val c = v.coins
        c.clear()
        for (k in 0 until to.coinCount) {
            var x = to.coinX[k]
            var y = to.coinY[k]
            val j = from.coinIndex[to.coinId[k]]
            if (j >= 0 && from !== to) {
                x = from.coinX[j] + (x - from.coinX[j]) * t
                y = from.coinY[j] + (y - from.coinY[j]) * t
            }
            c.add(to.coinId[k], x, y, 0f, 0f, 1)
        }

        // Kills and hits are shown when the drawing reaches the moment they happened,
        // or once they have waited a full delay (the host clock stands still while paused).
        for (k in stored - 1 downTo 0) {
            val s = ring[(newest - k + RING) % RING]
            if (s.seq <= eventsThrough) continue
            if (s.tick > renderTick && nowNanos - s.arrivalNanos < delayNanos + STEP_NANOS * 3) break
            val ev = s.events
            for (n in 0 until ev.count) {
                when {
                    // Another player fired: fly a copy of their volley here.
                    ev.kind[n] == Events.SHOT -> ghostVolley(top, ev.who[n], ev.x[n], ev.y[n], ev.dx[n], ev.dy[n])
                    // This phone already knew it was hit, and buzzed then.
                    ev.kind[n] == Events.PLAYER_HIT && ev.who[n] == me -> Unit
                    else -> v.events.add(ev.kind[n], ev.who[n], ev.x[n], ev.y[n], ev.dx[n], ev.dy[n], ev.angle[n])
                }
            }
            eventsThrough = s.seq
        }
    }

    /** Touching a drawn enemy, or a drawn enemy shot reaching the player, costs a pip. Told to the host. */
    private fun checkHurt() {
        val px = pilot.x
        val py = pilot.y
        val shotReach = Balance.HOSTILE_RADIUS + Balance.PLAYER_RADIUS * GRAZE
        for (i in 0 until drift.count) {
            if (renderTick < drift.born[i]) continue
            val dx = drift.x(i, renderTick) - px
            val dy = drift.y(i, renderTick) - py
            if (dx * dx + dy * dy >= shotReach * shotReach) continue
            reports.hurt(drift.id[i])
            drift.removeAt(i)
            hurt()
            return
        }
        val e = view.enemies
        for (j in 0 until e.count) {
            val reach = Balance.PLAYER_RADIUS + Bestiary.of(e.type[j]).radius
            val dx = e.x[j] - px
            val dy = e.y[j] - py
            if (dx * dx + dy * dy >= reach * reach) continue
            reports.hurt(-1)
            hurt()
            return
        }
    }

    private fun justHit(id16: Int): Boolean {
        for (k in 0 until FLASHES) if (flashId[k] == id16 && flashUntil[k] > steps) return true
        return false
    }

    private fun hurt() {
        localInvuln = INVULN_TICKS
        localHurt = HURT_TICKS
        view.events.add(Events.PLAYER_HIT, me, pilot.x, pilot.y)
    }

    private fun ghostVolley(s: Snapshot, who: Int, x: Float, y: Float, aimX: Float, aimY: Float) {
        if (who == me || who !in 0 until s.playerCount) return
        val speed = s.bulletSpeed[who]
        if (speed <= 0f) return
        val life = (s.range[who] / speed * TICK_HZ).toInt()
        val muzzle = Balance.PLAYER_RADIUS * 1.3f
        val n = s.volley[who].coerceAtLeast(1)
        val aim = atan2(aimY, aimX)
        for (k in 0 until n) {
            val a = aim + (k - (n - 1) / 2f) * Balance.MULTISHOT_SPREAD
            val dx = cos(a)
            val dy = sin(a)
            ghosts.add(ghostId--, x + dx * muzzle, y + dy * muzzle, dx * speed, dy * speed, life, 0f, s.bulletRadius[who], s.pierce[who], who)
        }
    }

    private fun flyGhosts() {
        val g = ghosts
        g.remember()
        for (i in 0 until g.count) {
            g.x[i] += g.vx[i] * TICK_SECONDS
            g.y[i] += g.vy[i] * TICK_SECONDS
            g.life[i] -= 1
            if (g.x[i] < -MARGIN || g.x[i] > ARENA_W + MARGIN || g.y[i] < -MARGIN || g.y[i] > ARENA_H + MARGIN) g.life[i] = 0
        }
        Pilot.cullShots(g, view, null)
    }
}
