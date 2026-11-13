package dev.david.tessel.core.sim

import kotlin.math.ceil

/**
 * Buckets entity indices by position so a collision check only looks at
 * neighbours. Rebuilt every tick; each cell is a linked list threaded
 * through [next], so nothing is allocated.
 */
class Grid(
    private val cell: Float,
    private val minX: Float,
    private val minY: Float,
    maxX: Float,
    maxY: Float,
    capacity: Int,
) {
    val cols = ceil((maxX - minX) / cell).toInt()
    val rows = ceil((maxY - minY) / cell).toInt()
    val head = IntArray(cols * rows)
    val next = IntArray(capacity)

    fun clear() = head.fill(-1)

    fun insert(index: Int, x: Float, y: Float) {
        val c = row(y) * cols + col(x)
        next[index] = head[c]
        head[c] = index
    }

    fun col(x: Float) = ((x - minX) / cell).toInt().coerceIn(0, cols - 1)
    fun row(y: Float) = ((y - minY) / cell).toInt().coerceIn(0, rows - 1)
}
