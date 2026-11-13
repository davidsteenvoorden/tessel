package dev.david.tessel.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridTest {
    private val grid = Grid(cell = 50f, minX = -100f, minY = -100f, maxX = 400f, maxY = 300f, capacity = 16)

    private fun inCell(x: Float, y: Float): List<Int> {
        val out = ArrayList<Int>()
        var j = grid.head[grid.row(y) * grid.cols + grid.col(x)]
        while (j >= 0) {
            out += j
            j = grid.next[j]
        }
        return out
    }

    @Test
    fun itemsLandInTheirCell() {
        grid.clear()
        grid.insert(0, 10f, 10f)
        grid.insert(1, 20f, 30f)
        grid.insert(2, 120f, 10f)
        assertEquals(setOf(0, 1), inCell(5f, 5f).toSet())
        assertEquals(listOf(2), inCell(110f, 40f))
    }

    @Test
    fun farOutsideClampsToTheBorder() {
        grid.clear()
        grid.insert(3, -5000f, -5000f)
        assertTrue(3 in inCell(-100f, -100f))
    }

    @Test
    fun clearEmptiesEveryCell() {
        grid.insert(4, 0f, 0f)
        grid.clear()
        assertTrue(grid.head.all { it == -1 })
    }
}
