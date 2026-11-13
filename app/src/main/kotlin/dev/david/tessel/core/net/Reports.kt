package dev.david.tessel.core.net

/**
 * What a joining phone saw happen to its own player since it last told the
 * host: enemies its shots hit, and what hit it. The host takes its word for
 * both, so the player is judged by what their own screen showed.
 */
class Reports {
    val hitId = IntArray(MAX_HITS)
    val hitX = FloatArray(MAX_HITS)
    val hitY = FloatArray(MAX_HITS)
    var hits = 0
        private set

    /** Low 16 bits of the enemy shot that hit, or -1 for touching an enemy. */
    val hurtBy = IntArray(MAX_HURTS)
    var hurts = 0
        private set

    /** Low 16 bits of coins the player walked into. */
    val pickupId = IntArray(MAX_PICKUPS)
    var pickups = 0
        private set

    val isEmpty get() = hits == 0 && hurts == 0 && pickups == 0

    fun hit(enemy16: Int, ux: Float, uy: Float) {
        if (hits == MAX_HITS) return
        hitId[hits] = enemy16
        hitX[hits] = ux
        hitY[hits] = uy
        hits++
    }

    fun hurt(shot16: Int) {
        if (hurts == MAX_HURTS) return
        hurtBy[hurts++] = shot16
    }

    fun pickup(coin16: Int) {
        if (pickups == MAX_PICKUPS) return
        pickupId[pickups++] = coin16
    }

    fun clear() {
        hits = 0
        hurts = 0
        pickups = 0
    }

    companion object {
        const val MAX_HITS = 96
        const val MAX_HURTS = 8
        const val MAX_PICKUPS = 32
    }
}
