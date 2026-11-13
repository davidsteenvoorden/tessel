package dev.david.tessel.core.net

import dev.david.tessel.core.sim.Events
import dev.david.tessel.core.sim.MAX_PLAYERS
import dev.david.tessel.core.sim.PlayerInput
import dev.david.tessel.core.sim.World

/** The host is always player 0; phones that joined are 1 and up, in the order the lobby gave them. */
const val HOST_PLAYER = 0

/**
 * The host's side of a co-op game. The host runs the only real simulation;
 * this feeds it the other players' input and what their phones saw them hit
 * or get hit by, applies their shop choices and sends each of them what to
 * draw, 30 times a second.
 *
 * [send] returns false when a latest-state message was skipped because the
 * previous one to that player is still on its way; the events it carried are
 * kept for the next.
 */
class HostSession(private val world: World, private val send: (player: Int, bytes: ByteArray, latest: Boolean) -> Boolean) {
    private val remote = Array(MAX_PLAYERS) { PlayerInput() }
    private val quiet = PlayerInput()
    private val lastInputNanos = LongArray(MAX_PLAYERS)
    private val lastSentNanos = LongArray(MAX_PLAYERS)
    private val outbox = Array(MAX_PLAYERS) { Events(512) }
    private val born = Array(MAX_PLAYERS) { Events(SNAPSHOT_SHOTS) }
    private val gone = Array(MAX_PLAYERS) { Events(SNAPSHOT_SHOTS) }
    private val reports = Reports()
    private val echoIn = Echo()
    private val echoOut = Echo()

    // Per player: their last input number and clock reading, when it came, and the measured round trip.
    private val lastSeq = IntArray(MAX_PLAYERS)
    private val lastStamp = IntArray(MAX_PLAYERS)
    private val stampAt = IntArray(MAX_PLAYERS)
    private val roundTrip = FloatArray(MAX_PLAYERS) { -1f }
    private val buf = Wire.buffer(MAX_MESSAGE * 2)

    /** Bit p set once player p pressed Ready in the shop. */
    var ready = 0
        private set

    /** Bit p set while player p's phone is linked. The host's own bit is always set. */
    var linked = 1
        private set

    /** Pause, resume and restart asks from other players; the game loop decides what they mean. */
    var onRequest: ((Int) -> Unit)? = null

    fun connect(player: Int) {
        linked = linked or (1 shl player)
        lastSeq[player] = 0
        lastStamp[player] = 0
        roundTrip[player] = -1f
        outbox[player].clear()
        born[player].clear()
        gone[player].clear()
        lastSentNanos[player] = 0L
    }

    fun disconnect(player: Int) {
        linked = linked and (1 shl player).inv()
        ready = ready and (1 shl player).inv()
    }

    fun receive(player: Int, bytes: ByteArray, nowNanos: Long) {
        if (player !in 1 until world.playerCount) return
        val buf = Wire.open(bytes)
        when (Wire.typeOf(bytes)) {
            Wire.INPUT -> {
                Protocol.readInput(buf, remote[player], reports, echoIn)
                lastInputNanos[player] = nowNanos
                val now = millis(nowNanos)
                lastSeq[player] = echoIn.seq
                lastStamp[player] = echoIn.stamp
                stampAt[player] = now
                val sample = echoIn.roundTrip(now)
                if (sample >= 0) roundTrip[player] = smooth(roundTrip[player], sample)
                for (k in 0 until reports.pickups) world.remotePickup(player, reports.pickupId[k])
                for (k in 0 until reports.hits) world.remoteHit(player, reports.hitId[k], reports.hitX[k], reports.hitY[k])
                for (k in 0 until reports.hurts) world.remoteHurt(player, reports.hurtBy[k])
            }
            Wire.ACTION -> {
                val kind = buf.get().toInt()
                val arg = buf.get().toInt()
                when (kind) {
                    Wire.BUY -> world.shop.buy(world, player, arg)
                    Wire.HEAL -> world.shop.heal(world, player)
                    Wire.REROLL -> world.shop.reroll(world, player)
                    Wire.READY -> markReady(player)
                    else -> onRequest?.invoke(kind)
                }
            }
        }
    }

    /** A player's input for this tick. If their phone has gone quiet, they stand still and stop firing. */
    fun remoteInput(player: Int, nowNanos: Long): PlayerInput {
        if (nowNanos - lastInputNanos[player] > STALE_NANOS) {
            quiet.clear()
            return quiet
        }
        return remote[player]
    }

    fun markReady(player: Int) {
        ready = ready or (1 shl player)
    }

    /** Everyone still in the game has pressed Ready. */
    fun allReady(): Boolean {
        var needed = 0
        for (p in 0 until world.playerCount) if (world.players.present[p]) needed = needed or (1 shl p)
        return ready and needed == needed
    }

    fun readyCount() = Integer.bitCount(ready)

    fun clearReady() {
        ready = 0
    }

    /** Keeps the events the other phones need; call before the world's events are cleared. */
    fun collect(events: Events) {
        for (i in 0 until events.count) {
            val kind = events.kind[i]
            // Coin pickups need no show, and hit flashes travel with each enemy.
            if (kind == Events.COIN || kind == Events.ENEMY_HIT) continue
            for (p in 1 until world.playerCount) {
                if (linked and (1 shl p) == 0) continue
                // A phone flies its own shots already.
                if (kind == Events.SHOT && events.who[i] == p) continue
                val to = when (kind) {
                    Events.HOSTILE_SPAWN -> born[p]
                    Events.HOSTILE_GONE -> gone[p]
                    else -> outbox[p]
                }
                to.add(kind, events.who[i], events.x[i], events.y[i], events.dx[i], events.dy[i], events.angle[i])
            }
        }
    }

    /** Sends each linked phone a snapshot if one is due. Keeps sending while paused or shopping so menus stay current. */
    fun pump(nowNanos: Long, paused: Boolean, waiting: Boolean) {
        for (p in 1 until world.playerCount) {
            if (linked and (1 shl p) == 0) continue
            if (nowNanos - lastSentNanos[p] < SNAPSHOT_NANOS) continue
            val now = millis(nowNanos)
            echoOut.seq = lastSeq[p]
            echoOut.stamp = now
            echoOut.echo = lastStamp[p]
            echoOut.held = if (lastStamp[p] == 0) 0 else now - stampAt[p]
            Protocol.writeSnapshot(buf, world, paused, waiting, ready, outbox[p], born[p], gone[p], p, echoOut)
            if (send(p, Wire.finish(buf), true)) {
                outbox[p].clear()
                born[p].clear()
                gone[p].clear()
                lastSentNanos[p] = nowNanos
            }
        }
    }

    /** Round trip to [player]'s phone in milliseconds, smoothed, or -1 before it is known. */
    fun roundTripMillis(player: Int): Int = roundTrip[player].toInt()

    /** Tells every other phone a fresh run has begun, and which player it is. */
    fun announceStart() {
        for (p in 1 until world.playerCount) {
            outbox[p].clear()
            born[p].clear()
            gone[p].clear()
            lastSentNanos[p] = 0L
            if (linked and (1 shl p) != 0) send(p, Wire.start(world.difficulty.ordinal, p, world.playerCount), false)
        }
        clearReady()
    }

    companion object {
        /** A clock reading in milliseconds; never 0, which means "none yet" on the wire. */
        fun millis(nanos: Long): Int = (nanos / 1_000_000L).toInt().let { if (it == 0) 1 else it }

        /** Follows a measured round trip closely, but not every single bump. */
        fun smooth(old: Float, sample: Int): Float = if (old < 0f) sample.toFloat() else old + (sample - old) * 0.2f

        /** 30 snapshots a second: small enough now that bullets do not travel. */
        const val SNAPSHOT_NANOS = 33_000_000L
        const val STALE_NANOS = 400_000_000L
    }
}
