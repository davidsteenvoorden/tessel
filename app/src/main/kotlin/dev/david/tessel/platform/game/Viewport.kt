package dev.david.tessel.platform.game

import dev.david.tessel.core.sim.ARENA_H
import dev.david.tessel.core.sim.ARENA_W
import kotlin.math.min

/**
 * Fits the fixed-size arena to the screen, centred, never stretched. On a
 * 20:9 phone it fills the screen exactly; on other shapes thin bars remain,
 * so both players in co-op always see the same arena.
 */
class Viewport {
    var scale = 1f
        private set
    var left = 0f
        private set
    var top = 0f
        private set

    val right get() = left + ARENA_W * scale
    val bottom get() = top + ARENA_H * scale

    fun fit(width: Int, height: Int) {
        scale = min(width / ARENA_W, height / ARENA_H)
        left = (width - ARENA_W * scale) / 2f
        top = (height - ARENA_H * scale) / 2f
    }

    fun screenX(worldX: Float) = left + worldX * scale
    fun screenY(worldY: Float) = top + worldY * scale
}
