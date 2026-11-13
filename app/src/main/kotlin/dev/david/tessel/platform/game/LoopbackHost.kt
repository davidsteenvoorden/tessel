package dev.david.tessel.platform.game

import android.os.Handler
import dev.david.tessel.core.net.HOST_PLAYER
import dev.david.tessel.core.net.HostSession
import dev.david.tessel.core.net.Wire
import dev.david.tessel.core.sim.Bot
import dev.david.tessel.core.sim.Phase
import dev.david.tessel.core.sim.PlayerInput
import dev.david.tessel.core.sim.TICK_HZ
import dev.david.tessel.core.sim.World
import dev.david.tessel.platform.net.Link

/**
 * Debug builds only: a pretend host phone running inside this one, with a
 * bot as its player and an artificial delay on every message. It lets the
 * joining side of co-op be played and checked with a single phone.
 * Start with: adb shell am start -n dev.david.tessel.debug/dev.david.tessel.MainActivity --ez loopback true
 */
class LoopbackHost(
    private val handler: Handler,
    private val delayMillis: Long,
    private val toClient: (ByteArray) -> Unit,
) {
    private val world = World(seed = 4242).apply {
        reset(players = PLAYERS, seed = 4242)
        remote[YOU] = true
    }
    private val bot = Bot()
    private val inputs = Array(PLAYERS) { PlayerInput() }
    private var paused = false
    private var pending = 0L

    private val session = HostSession(world) { _, bytes, _ ->
        handler.postDelayed({ toClient(bytes) }, delayMillis)
        true
    }.apply {
        connect(YOU)
        onRequest = { kind ->
            when (kind) {
                Wire.PAUSE -> paused = true
                Wire.RESUME -> paused = false
                Wire.RESTART -> if (world.over) restart()
            }
        }
    }

    /** The link the client side sends through: it lands here after the same delay. */
    val clientLink = object : Link {
        override fun send(bytes: ByteArray, latest: Boolean): Boolean {
            handler.postDelayed({ session.receive(YOU, bytes, System.nanoTime()) }, delayMillis)
            return true
        }
    }

    fun begin() {
        session.announceStart()
    }

    fun frame(elapsedNanos: Long, nowNanos: Long) {
        if (!paused && world.phase != Phase.SHOP && !world.over) {
            pending += elapsedNanos
            while (pending >= STEP_NANOS) {
                bot.drive(world, HOST_PLAYER, inputs[HOST_PLAYER])
                inputs[YOU].copyFrom(session.remoteInput(YOU, nowNanos))
                world.step(inputs)
                pending -= STEP_NANOS
            }
        } else {
            pending = 0
        }
        if (world.phase == Phase.SHOP) {
            // The bot never shops; it is ready as soon as the shop opens.
            session.markReady(HOST_PLAYER)
            if (session.allReady()) {
                session.clearReady()
                world.nextStage()
            }
        }
        session.collect(world.events)
        world.events.clear()
        session.pump(nowNanos, paused, false)
    }

    private fun restart() {
        world.reset(players = PLAYERS, seed = System.nanoTime())
        world.remote[YOU] = true
        paused = false
        session.announceStart()
    }

    companion object {
        const val PLAYERS = 2
        const val YOU = 1
        private const val STEP_NANOS = 1_000_000_000L / TICK_HZ
    }
}
