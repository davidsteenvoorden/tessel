package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Balance
import kotlin.math.sqrt

/**
 * Standing players are solid. Each phone moves only its own player, so each
 * one steps back out of the other by [Balance.PUSH_SHARE] of the overlap.
 * Walk into someone and you both give way, which reads as a shove.
 */
class Bump {
    var dx = 0f
        private set
    var dy = 0f
        private set

    /** True when a player at ([x], [y]) overlaps one at ([ox], [oy]); [dx] and [dy] then hold the step out. */
    fun from(x: Float, y: Float, ox: Float, oy: Float): Boolean {
        val reach = 2f * Balance.PLAYER_RADIUS
        val ex = x - ox
        val ey = y - oy
        val d2 = ex * ex + ey * ey
        if (d2 >= reach * reach) return false
        if (d2 < 0.0001f) {
            dx = Balance.PUSH_SHARE * reach
            dy = 0f
            return true
        }
        val d = sqrt(d2)
        val move = (reach - d) * Balance.PUSH_SHARE
        dx = ex / d * move
        dy = ey / d * move
        return true
    }
}
