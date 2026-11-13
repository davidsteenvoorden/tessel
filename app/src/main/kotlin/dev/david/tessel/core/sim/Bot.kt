package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Balance
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A plain teammate: drifts in loose loops, backs away from whatever is
 * closest and shoots at it. Down, it heads for a teammate. Good enough to
 * stand in for another phone while testing co-op alone. Bots given different
 * starting [heading]s wander apart instead of moving as one.
 */
class Bot(private var heading: Float = 0f) {

    fun drive(w: World, p: Int, out: PlayerInput) {
        out.clear()
        val pl = w.players
        val px = pl.x[p]
        val py = pl.y[p]
        heading += 0.012f
        var mx = cos(heading)
        var my = sin(heading) * 0.6f

        if (!pl.alive(p)) {
            val q = w.nearestPlayer(px, py)
            if (q >= 0) {
                mx = pl.x[q] - px
                my = pl.y[q] - py
            }
        } else {
            val e = w.enemies
            var nearest = -1
            var bestD2 = Float.MAX_VALUE
            for (i in 0 until e.count) {
                val dx = e.x[i] - px
                val dy = e.y[i] - py
                val d2 = dx * dx + dy * dy
                if (d2 < bestD2) {
                    bestD2 = d2
                    nearest = i
                }
            }
            if (nearest >= 0) {
                val d = sqrt(bestD2).coerceAtLeast(0.001f)
                val ux = (e.x[nearest] - px) / d
                val uy = (e.y[nearest] - py) / d
                out.aimX = ux
                out.aimY = uy
                out.fire = d < Balance.BULLET_RANGE
                if (d < BACK_OFF) {
                    mx = -ux
                    my = -uy
                }
            }
        }

        // Walls are where bots go to die.
        if (px < WALL) mx += 1f
        if (px > ARENA_W - WALL) mx -= 1f
        if (py < WALL) my += 1f
        if (py > ARENA_H - WALL) my -= 1f
        val len = sqrt(mx * mx + my * my)
        if (len > 1f) {
            mx /= len
            my /= len
        }
        out.moveX = mx
        out.moveY = my
    }

    private companion object {
        const val BACK_OFF = 220f
        const val WALL = 120f
    }
}
