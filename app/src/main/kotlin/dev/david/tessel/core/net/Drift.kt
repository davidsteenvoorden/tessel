package dev.david.tessel.core.net

import dev.david.tessel.core.sim.TICK_HZ

/**
 * Enemy shots on a joining phone. Each one is heard about once, when it is
 * fired, and flown here from then on; they never turn, so nothing more needs
 * to cross the link unless one is used up hitting someone.
 */
class Drift(private val capacity: Int) {
    val id = IntArray(capacity)
    val x0 = FloatArray(capacity)
    val y0 = FloatArray(capacity)
    val vx = FloatArray(capacity)
    val vy = FloatArray(capacity)

    /** Host tick it was fired on. */
    val born = IntArray(capacity)

    var count = 0
        private set

    fun add(id16: Int, x: Float, y: Float, vx: Float, vy: Float, born: Int) {
        if (count == capacity) return
        val i = count++
        id[i] = id16
        x0[i] = x
        y0[i] = y
        this.vx[i] = vx
        this.vy[i] = vy
        this.born[i] = born
    }

    fun x(i: Int, tick: Float) = x0[i] + vx[i] * (tick - born[i]) / TICK_HZ

    fun y(i: Int, tick: Float) = y0[i] + vy[i] * (tick - born[i]) / TICK_HZ

    fun remove(id16: Int) {
        for (i in 0 until count) {
            if (id[i] == id16) {
                removeAt(i)
                return
            }
        }
    }

    fun removeAt(i: Int) {
        val last = --count
        if (i == last) return
        id[i] = id[last]
        x0[i] = x0[last]
        y0[i] = y0[last]
        vx[i] = vx[last]
        vy[i] = vy[last]
        born[i] = born[last]
    }

    fun clear() {
        count = 0
    }
}
