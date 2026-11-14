package dev.david.tessel.core.tutorial

import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.sim.ARENA_H
import dev.david.tessel.core.sim.ARENA_W
import dev.david.tessel.core.sim.PlayerInput
import dev.david.tessel.core.sim.TICK_HZ
import dev.david.tessel.core.sim.World
import kotlin.math.sqrt

enum class TutorialStep { MOVE, FIRE, DODGE, DONE }

/**
 * The guided practice: walk, shoot, then dodge. It runs on an ordinary world
 * with [World.practice] on, so nothing costs health and no stage ends, and it
 * brings in just the enemies each step needs. Driven once per tick, after the
 * world has stepped.
 */
class Tutorial {
    var step = TutorialStep.MOVE
        private set

    /** How far through the current step, 0 to 1. */
    var progress = 0f
        private set

    /** Set for a couple of seconds after a hit while dodging: the count started over. */
    var hit = false
        private set

    /** The step is done and the next one follows in a moment. */
    var passed = false
        private set

    private var travelled = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var kills = 0
    private var calm = 0
    private var beat = 0
    private var hitTicks = 0
    private var lastTaken = 0
    private var cooldown = 0
    private var spawned = 0

    fun begin(w: World) {
        step = TutorialStep.MOVE
        progress = 0f
        hit = false
        passed = false
        travelled = 0f
        lastX = w.players.x[0]
        lastY = w.players.y[0]
        kills = w.players.kills[0]
        calm = 0
        beat = 0
        hitTicks = 0
        lastTaken = w.players.taken[0]
        cooldown = 0
        spawned = 0
    }

    @Suppress("UNUSED_PARAMETER")
    fun update(w: World, input: PlayerInput) {
        if (passed) {
            if (--beat <= 0) advance(w)
            return
        }
        when (step) {
            TutorialStep.MOVE -> {
                val dx = w.players.x[0] - lastX
                val dy = w.players.y[0] - lastY
                lastX = w.players.x[0]
                lastY = w.players.y[0]
                travelled += sqrt(dx * dx + dy * dy)
                progress = (travelled / MOVE_DISTANCE).coerceAtMost(1f)
                if (progress >= 1f) finish()
            }
            TutorialStep.FIRE -> {
                val done = w.players.kills[0] - kills
                progress = (done / FIRE_KILLS.toFloat()).coerceAtMost(1f)
                if (progress >= 1f) {
                    finish()
                    return
                }
                if (cooldown > 0) cooldown--
                if (w.enemies.count < FIRE_ALIVE && cooldown == 0) {
                    val at = CORNERS[spawned++ % CORNERS.size]
                    w.spawnEnemy(Bestiary.DOT, at[0], at[1])
                    cooldown = TICK_HZ
                }
            }
            TutorialStep.DODGE -> dodge(w)
            TutorialStep.DONE -> Unit
        }
    }

    private fun dodge(w: World) {
        val taken = w.players.taken[0]
        if (taken != lastTaken) {
            lastTaken = taken
            calm = 0
            hitTicks = HIT_NOTICE_TICKS
        }
        if (hitTicks > 0) hitTicks--
        hit = hitTicks > 0
        calm++
        progress = (calm / (DODGE_SECONDS * TICK_HZ).toFloat()).coerceAtMost(1f)
        if (progress >= 1f) {
            finish()
            return
        }
        // Shooters that get shot down come back, so there is always something to dodge.
        if (cooldown > 0) cooldown--
        if (cooldown == 0) {
            if (count(w, Bestiary.TICK) < 1) {
                w.spawnEnemy(Bestiary.TICK, ARENA_W / 2f, 70f)
                cooldown = TICK_HZ
            } else if (calm > FAN_AT_TICKS && count(w, Bestiary.FANNER) < 1) {
                w.spawnEnemy(Bestiary.FANNER, ARENA_W / 2f, ARENA_H - 70f)
                cooldown = TICK_HZ
            }
        }
    }

    private fun count(w: World, type: Int): Int {
        var n = 0
        for (i in 0 until w.enemies.count) if (w.enemies.type[i] == type) n++
        return n
    }

    private fun finish() {
        passed = true
        beat = BEAT_TICKS
        progress = 1f
    }

    private fun advance(w: World) {
        passed = false
        progress = 0f
        step = TutorialStep.entries[step.ordinal + 1]
        calm = 0
        cooldown = 0
        hit = false
        kills = w.players.kills[0]
        lastTaken = w.players.taken[0]
        if (step == TutorialStep.DONE) {
            w.enemies.clear()
            w.hostile.clear()
        }
    }

    companion object {
        const val MOVE_DISTANCE = 500f
        const val FIRE_KILLS = 5
        const val DODGE_SECONDS = 15
        private const val FIRE_ALIVE = 3
        private const val FAN_AT_TICKS = 6 * TICK_HZ
        private const val BEAT_TICKS = 90
        private const val HIT_NOTICE_TICKS = 2 * TICK_HZ
        private val CORNERS = arrayOf(
            floatArrayOf(40f, 40f),
            floatArrayOf(ARENA_W - 40f, ARENA_H - 40f),
            floatArrayOf(ARENA_W - 40f, 40f),
            floatArrayOf(40f, ARENA_H - 40f),
        )
    }
}
