package dev.david.tessel.core.net

import dev.david.tessel.core.content.Balance
import dev.david.tessel.core.sim.Events
import dev.david.tessel.core.sim.PlayerInput
import dev.david.tessel.core.sim.REVIVE_TICKS
import dev.david.tessel.core.sim.TICK_HZ
import dev.david.tessel.core.sim.World
import kotlin.math.PI
import kotlin.math.floor

private const val RING = 16
private const val STEP_NANOS = 1_000_000_000L / TICK_HZ

/** Drawing runs this far behind the host so there is always a snapshot on each side of "now". */
private const val DELAY_NANOS = 100_000_000L

/** Lets the clock estimate drift upward when the link gets slower. */
private const val CREEP_NANOS = 250_000L

/** Snapshots this much later than expected, several in a row, mean the host clock jumped (pause, restart). */
private const val RESYNC_NANOS = 250_000_000L
private const val SNAP_DISTANCE = 120f
private const val TWO_PI = (2 * PI).toFloat()

/**
 * A joining phone's side of a co-op game. It never simulates enemies:
 * it keeps the last few host snapshots, draws the world a moment in the past
 * so movement can be blended smoothly between them, and runs only its own
 * player in the present.
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
    private val pilot = Pilot()
    private val buf = Wire.buffer(64)
    private var inputCountdown = 0

    /** Most recent snapshot, for menus: phase, pause, shop, scores. */
    val latest: Snapshot? get() = if (newest < 0) null else ring[newest]

    fun reset() {
        newest = -1
        stored = 0
        offsetNanos = Long.MAX_VALUE
        lateCount = 0
        eventsThrough = arrivals
        pilot.reset()
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
        }
        newest = slot
        if (stored < RING) stored++
        players = s.playerCount

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

    /** One 60 Hz step of the local player; sends input every other step. */
    fun tick(input: PlayerInput) {
        val s = latest ?: return
        val hostX = s.px[me]
        val hostY = s.py[me]
        if (!pilot.placed) pilot.place(hostX, hostY)
        val dx = hostX - pilot.x
        val dy = hostY - pilot.y
        // The host only lags a little behind us; a big gap means it moved us (new run, new stage).
        if (dx * dx + dy * dy > SNAP_DISTANCE * SNAP_DISTANCE) pilot.place(hostX, hostY)

        if (pilot.step(input, s, me)) view.events.add(Events.SHOT, me)

        if (--inputCountdown <= 0) {
            inputCountdown = 2
            Protocol.writeInput(buf, input, pilot.x, pilot.y)
            send(Wire.finish(buf), true)
        }
    }

    fun action(kind: Int, arg: Int = 0) {
        send(Wire.action(kind, arg), false)
    }

    /** Fills [view] for drawing at [nowNanos]. */
    fun frame(nowNanos: Long) {
        val top = latest ?: return
        val renderTick = if (offsetNanos == Long.MAX_VALUE) top.tick.toFloat()
        else (nowNanos - DELAY_NANOS - offsetNanos).toFloat() / STEP_NANOS

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
                e.flash[k] = if (to.enemyFlash[i]) 1 else 0
                e.angle[k] = angle
                e.cue[k] = to.enemyCue[i]
            }
        }

        // Bullets fly straight, so the older snapshot carried forward is exact enough.
        val b2 = v.bullets
        b2.clear()
        val ahead = (renderTick - from.tick) / TICK_HZ
        for (i in 0 until from.bulletCount) {
            val x = from.bulletX[i] + from.bulletVx[i] * ahead
            val y = from.bulletY[i] + from.bulletVy[i] * ahead
            b2.add(0, x, y, from.bulletVx[i], from.bulletVy[i], 1, 0f, from.bulletRadiusOf[i], 0, HOST_PLAYER)
        }
        val h = v.hostile
        h.clear()
        for (i in 0 until from.hostileCount) {
            val x = from.hostileX[i] + from.hostileVx[i] * ahead
            val y = from.hostileY[i] + from.hostileVy[i] * ahead
            h.add(0, x, y, from.hostileVx[i], from.hostileVy[i], 1, 0f, Balance.HOSTILE_RADIUS, 0, -1)
        }
        pilot.cull(v)
        val shots = pilot.shots
        for (i in 0 until shots.count) {
            b2.add(shots.id[i], shots.x[i], shots.y[i], shots.vx[i], shots.vy[i], 1, 0f, shots.radius[i], 0, me)
        }

        val c = v.coins
        c.clear()
        for (i in 0 until to.coinCount) {
            var x = to.coinX[i]
            var y = to.coinY[i]
            val j = from.coinIndex[to.coinId[i]]
            if (j >= 0 && from !== to) {
                x = from.coinX[j] + (x - from.coinX[j]) * t
                y = from.coinY[j] + (y - from.coinY[j]) * t
            }
            c.add(to.coinId[i], x, y, 0f, 0f, 1)
        }

        // Kills and hits are shown when the drawing reaches the moment they happened,
        // or once they have waited a full delay (the host clock stands still while paused).
        for (k in stored - 1 downTo 0) {
            val s = ring[(newest - k + RING) % RING]
            if (s.seq <= eventsThrough) continue
            if (s.tick > renderTick && nowNanos - s.arrivalNanos < DELAY_NANOS + STEP_NANOS * 3) break
            val ev = s.events
            for (i in 0 until ev.count) {
                v.events.add(ev.kind[i], ev.who[i], ev.x[i], ev.y[i], ev.dx[i], ev.dy[i], ev.angle[i])
            }
            eventsThrough = s.seq
        }
    }
}
