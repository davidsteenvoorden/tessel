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
import kotlin.math.sqrt

private const val RING = 16
private const val STEP_NANOS = 1_000_000_000L / TICK_HZ

/**
 * Drawing runs this far behind the host so the next snapshot is nearly always
 * here already: one snapshot gap, plus how late the late ones get ([Jitter]),
 * plus a little. Never under 40 ms or over 220 ms.
 */
private const val MIN_DELAY_NANOS = 40_000_000L
private const val MAX_DELAY_NANOS = 220_000_000L
private const val FIRST_DELAY_NANOS = 80_000_000L
private const val DELAY_MARGIN_NANOS = 5_000_000L

/**
 * The delay eases toward its target by bending time: the world runs up to
 * 15% slow while it grows and 4% fast while it shrinks, instead of jumping.
 */
private const val GROW_PERCENT = 15L
private const val SHRINK_PERCENT = 4L

/** About two seconds of snapshots. */
private const val JITTER_WINDOW = 64
/**
 * This phone moves its own player and the host follows, so it never pulls the
 * player back just because the host's copy is a round trip behind. Only a gap
 * this large that lasts this long means the host really put the player
 * somewhere else.
 */
private const val FAR_DISTANCE = 300f
private const val FAR_STEPS = 45
private const val TWO_PI = (2 * PI).toFloat()
private const val HURT_TICKS = 8
private const val FLASHES = 32
private const val FLASH_STEPS = 3
private const val MY_HITS = 256
private const val UNSENT = Int.MAX_VALUE

/** How long a body this phone saw break stays hidden while waiting for the host to agree. */
private const val GUESS_STEPS = 90
private const val GUESSES = 64
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
    private val jitter = Jitter(JITTER_WINDOW)
    private var arrivals = 0L
    private var eventsThrough = 0L
    private var gapTicks = 2f
    private var delay = FIRST_DELAY_NANOS
    private var lastFrameNanos = 0L
    private val pilot = Pilot()
    private val buf = Wire.buffer(1024)
    private val reports = Reports()
    private val echo = Echo()
    private var inputSeq = 0
    private var hostStamp = 0
    private var hostStampAt = 0
    private var roundTrip = -1f
    private var farSteps = 0

    // This phone's hits that the host has not acted on yet: on what, how hard, and in which input.
    private val myHitId = IntArray(MY_HITS)
    private val myHitDamage = FloatArray(MY_HITS)
    private val myHitSeq = IntArray(MY_HITS)
    private var myHits = 0

    // Enemies this phone saw its shots finish, and coins it walked into: hidden at once, until the host agrees.
    private val deadId = IntArray(GUESSES)
    private val deadUntil = IntArray(GUESSES)
    private var deadNext = 0
    private val takenId = IntArray(GUESSES)
    private val takenUntil = IntArray(GUESSES)
    private var takenNext = 0
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

    /** Round trip to the host in milliseconds, smoothed, or -1 before it is known. */
    val roundTripMillis: Int get() = roundTrip.toInt()

    /** How far behind the host the drawing runs right now, in nanoseconds. */
    val delayNanos: Long get() = delay

    /** Where [delayNanos] is heading: one snapshot gap plus how late the late ones get. */
    val targetDelayNanos: Long
        get() = ((gapTicks * STEP_NANOS).toLong() + jitter.spreadNanos + DELAY_MARGIN_NANOS).coerceIn(MIN_DELAY_NANOS, MAX_DELAY_NANOS)

    /** How late the late snapshots get, in milliseconds. */
    val spreadMillis: Int get() = (jitter.spreadNanos / 1_000_000L).toInt()

    /** Frames drawn with no newer snapshot to blend toward, so the world stood still. Counts up. */
    var starvedFrames = 0
        private set

    fun reset() {
        newest = -1
        stored = 0
        jitter.reset()
        eventsThrough = arrivals
        gapTicks = 2f
        pilot.reset()
        drift.clear()
        ghosts.clear()
        reports.clear()
        forgetGuesses()
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
            jitter.reset()
            pilot.reset()
            drift.clear()
            ghosts.clear()
            forgetGuesses()
        }
        newest = slot

        val now = HostSession.millis(nowNanos)
        val trip = s.echo.roundTrip(now)
        if (trip >= 0) roundTrip = HostSession.smooth(roundTrip, trip)
        hostStamp = s.echo.stamp
        hostStampAt = now
        // Hits the host has acted on are in its health numbers now.
        var k = 0
        while (k < myHits) {
            if (myHitSeq[k] != UNSENT && myHitSeq[k] <= s.echo.seq) {
                myHits--
                myHitId[k] = myHitId[myHits]
                myHitDamage[k] = myHitDamage[myHits]
                myHitSeq[k] = myHitSeq[myHits]
            } else {
                k++
            }
        }
        if (stored < RING) stored++
        players = s.playerCount

        for (i in 0 until s.bornCount) drift.add(s.bornId[i], s.bornX[i], s.bornY[i], s.bornVx[i], s.bornVy[i], s.bornTick[i])
        for (i in 0 until s.goneCount) drift.remove(s.goneId[i])
        // The host sweeps every enemy shot away when the shop opens.
        if (s.phase == Phase.SHOP || s.phase == Phase.OVER) drift.clear()

        // How many host ticks apart snapshots are, on average (two at 30 a second).
        if (previous != null && s.tick > previousTick && stored > 1) {
            gapTicks += ((s.tick - previousTick).coerceAtMost(12) - gapTicks) / 8f
        }
        jitter.add(nowNanos, s.tick * STEP_NANOS)
    }

    /** One 60 Hz step of the local player. Sends its input every step. */
    fun tick(input: PlayerInput, nowNanos: Long) {
        val s = latest ?: return
        val hostX = s.px[me]
        val hostY = s.py[me]
        if (!pilot.placed) pilot.place(hostX, hostY)
        val dx = hostX - pilot.x
        val dy = hostY - pilot.y
        if (dx * dx + dy * dy > FAR_DISTANCE * FAR_DISTANCE) {
            if (++farSteps > FAR_STEPS) {
                pilot.place(hostX, hostY)
                farSteps = 0
            }
        } else {
            farSteps = 0
        }

        steps++
        if (pilot.step(input, s, me)) view.events.add(Events.SHOT, me)
        stayOutOfBoss()
        val before = reports.hits
        pilot.cull(view, reports)
        for (k in before until reports.hits) landed(s, reports.hitId[k], reports.hitX[k], reports.hitY[k])
        flyGhosts()
        if (localInvuln > 0) localInvuln--
        if (localHurt > 0) localHurt--
        if (s.hp[me] > 0 && !s.invuln[me] && localInvuln == 0) checkHurt()
        if (s.hp[me] > 0) checkPickups()

        val now = HostSession.millis(nowNanos)
        val news = !reports.isEmpty
        inputSeq++
        echo.seq = inputSeq
        echo.stamp = now
        echo.echo = hostStamp
        echo.held = if (hostStamp == 0) 0 else now - hostStampAt
        Protocol.writeInput(buf, input, pilot.x, pilot.y, reports, echo)
        // Hits, hurts and pickups must arrive, so they skip the queue for latest-only messages.
        if (send(Wire.finish(buf), !news)) {
            for (k in 0 until myHits) if (myHitSeq[k] == UNSENT) myHitSeq[k] = inputSeq
            reports.clear()
        }
    }

    fun action(kind: Int, arg: Int = 0) {
        send(Wire.action(kind, arg), false)
    }

    /** Fills [view] for drawing at [nowNanos]. */
    fun frame(nowNanos: Long) {
        val top = latest ?: return
        val target = targetDelayNanos
        val dt = if (lastFrameNanos == 0L) 0L else (nowNanos - lastFrameNanos).coerceIn(0L, 100_000_000L)
        lastFrameNanos = nowNanos
        delay = if (target > delay) minOf(target, delay + dt * GROW_PERCENT / 100) else maxOf(target, delay - dt * SHRINK_PERCENT / 100)
        val offset = jitter.offsetNanos
        renderTick = if (offset == Long.MAX_VALUE) top.tick.toFloat()
        else (nowNanos - delay - offset).toFloat() / STEP_NANOS

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
        if (b == null && !top.paused && (top.phase == Phase.FIGHT || top.phase == Phase.CLEAR)) starvedFrames++
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
            if (guessed(deadId, deadUntil, to.enemyId[i])) continue
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
            val k = e.add(to.enemyId[i], to.enemyType[i], x, y, to.enemyHp[i])
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
            if (guessed(takenId, takenUntil, to.coinId[k])) continue
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
                    // Already shown breaking here, the moment this phone's shot finished it.
                    (ev.kind[n] == Events.ENEMY_KILLED || ev.kind[n] == Events.ENEMY_GONE) && settle(ev.ref[n]) -> Unit
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

    /**
     * A shot of ours reached enemy [id16]. It flashes at once, and if what it
     * has taken from us since the host last counted finishes it, it breaks
     * now rather than a round trip from now.
     */
    private fun landed(s: Snapshot, id16: Int, ux: Float, uy: Float) {
        flashId[flashNext] = id16
        flashUntil[flashNext] = steps + FLASH_STEPS
        flashNext = (flashNext + 1) % FLASHES
        if (myHits < MY_HITS) {
            myHitId[myHits] = id16
            myHitDamage[myHits] = s.damage[me]
            myHitSeq[myHits] = UNSENT
            myHits++
        }
        val at = s.enemyIndex[id16]
        if (at < 0) return
        var hp = s.enemyHp[at]
        if (hp == Float.POSITIVE_INFINITY) return
        for (k in 0 until myHits) if (myHitId[k] == id16) hp -= myHitDamage[k]
        if (hp > 0.001f) return
        val e = view.enemies
        for (j in 0 until e.count) {
            if (e.id[j] != id16) continue
            // Out of the way for the rest of this step's shots too.
            e.hp[j] = 0f
            view.events.add(Events.ENEMY_KILLED, e.type[j], e.x[j], e.y[j], ux, uy, e.angle[j], id16)
            break
        }
        deadId[deadNext] = id16
        deadUntil[deadNext] = steps + GUESS_STEPS
        deadNext = (deadNext + 1) % GUESSES
    }

    /** Coins the player walks into vanish here at once and go to the host as pickups. */
    private fun checkPickups() {
        val c = view.coins
        val reach = Balance.PLAYER_RADIUS + Balance.COIN_RADIUS + 2f
        for (i in 0 until c.count) {
            if (c.value[i] <= 0) continue
            val dx = c.x[i] - pilot.x
            val dy = c.y[i] - pilot.y
            if (dx * dx + dy * dy >= reach * reach) continue
            c.value[i] = 0
            reports.pickup(c.id[i])
            takenId[takenNext] = c.id[i]
            takenUntil[takenNext] = steps + GUESS_STEPS
            takenNext = (takenNext + 1) % GUESSES
        }
    }

    /** The boss does not budge for anyone; walking into it pushes the player back out, here as on the host. */
    private fun stayOutOfBoss() {
        val e = view.enemies
        for (j in 0 until e.count) {
            val def = Bestiary.of(e.type[j])
            if (!def.boss) continue
            val reach = def.radius + Balance.PLAYER_RADIUS
            val dx = pilot.x - e.x[j]
            val dy = pilot.y - e.y[j]
            val d2 = dx * dx + dy * dy
            if (d2 >= reach * reach || d2 < 0.0001f) return
            val d = sqrt(d2)
            val r = Balance.PLAYER_RADIUS
            pilot.place((e.x[j] + dx / d * reach).coerceIn(r, ARENA_W - r), (e.y[j] + dy / d * reach).coerceIn(r, ARENA_H - r))
            return
        }
    }

    private fun guessed(ids: IntArray, until: IntArray, id16: Int): Boolean {
        for (k in 0 until GUESSES) if (ids[k] == id16 && until[k] > steps) return true
        return false
    }

    /** The host's own word on a kill this phone already showed; true if it was ours to show. */
    private fun settle(id16: Int): Boolean {
        for (k in 0 until GUESSES) {
            if (deadId[k] == id16 && deadUntil[k] > steps) {
                deadUntil[k] = 0
                return true
            }
        }
        return false
    }

    private fun forgetGuesses() {
        myHits = 0
        deadUntil.fill(0)
        takenUntil.fill(0)
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
