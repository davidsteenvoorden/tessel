package dev.david.tessel.platform.game

import android.graphics.Path
import android.graphics.RectF
import dev.david.tessel.core.content.Shape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where a round body splits into facets, in degrees, clockwise on screen.
 * Same split lines as the app icon; the first sector is the lit one.
 */
val ROUND_SPLITS = floatArrayOf(171f, 294f, 385f, 531f)

/** Half the side of a square body as a share of its collision radius. */
const val SQUARE_HALF = 0.86f

/** Paths are built at this size and scaled down, which keeps their curves smooth. */
const val PATH_UNIT = 32f

/** cos/sin of the round split angles, for the thin gaps drawn on the player. */
val SPLIT_COS = FloatArray(3) { cos(ROUND_SPLITS[it] * PI / 180).toFloat() }
val SPLIT_SIN = FloatArray(3) { sin(ROUND_SPLITS[it] * PI / 180).toFloat() }

/**
 * Drawing data for every [Shape], built once: the outline, the lit facet,
 * and the pieces it shatters into. A polygon's pieces are the triangles of
 * its fan from the centre; the lit ones face the upper left, where the
 * light comes from. A circle splits into three sectors along [ROUND_SPLITS].
 */
object Bodies {
    private val shapes = Shape.entries
    val outline = Array(shapes.size) { Path() }
    val lit = Array(shapes.size) { Path() }

    /** First piece of each shape in the flat piece arrays below, and how many it has. */
    val firstPiece = IntArray(shapes.size)
    val pieceCount = IntArray(shapes.size)

    // Flat over all shapes. Each piece path is centred on its own centroid so it spins in place.
    val piecePath: Array<Path>
    val pieceX: FloatArray
    val pieceY: FloatArray
    val pieceLit: BooleanArray

    init {
        var total = 0
        for (s in shapes) {
            firstPiece[s.ordinal] = total
            pieceCount[s.ordinal] = if (s == Shape.CIRCLE) 3 else s.sides
            total += pieceCount[s.ordinal]
        }
        piecePath = Array(total) { Path() }
        pieceX = FloatArray(total)
        pieceY = FloatArray(total)
        pieceLit = BooleanArray(total)

        val oval = RectF(-PATH_UNIT, -PATH_UNIT, PATH_UNIT, PATH_UNIT)
        for (s in shapes) {
            val first = firstPiece[s.ordinal]
            if (s == Shape.CIRCLE) {
                outline[s.ordinal].addCircle(0f, 0f, PATH_UNIT, Path.Direction.CW)
                lit[s.ordinal].apply {
                    moveTo(0f, 0f)
                    arcTo(oval, ROUND_SPLITS[0], ROUND_SPLITS[1] - ROUND_SPLITS[0], false)
                    close()
                }
                for (k in 0..2) {
                    val start = ROUND_SPLITS[k]
                    val sweep = ROUND_SPLITS[k + 1] - start
                    val theta = sweep * PI.toFloat() / 180f
                    val mid = (start + sweep / 2f) * PI.toFloat() / 180f
                    // Centroid of a circular sector sits on its bisector.
                    val reach = 4f * sin(theta / 2f) / (3f * theta)
                    val i = first + k
                    pieceX[i] = cos(mid) * reach
                    pieceY[i] = sin(mid) * reach
                    pieceLit[i] = k == 0
                    piecePath[i].apply {
                        moveTo(0f, 0f)
                        arcTo(oval, start, sweep, false)
                        close()
                        offset(-pieceX[i] * PATH_UNIT, -pieceY[i] * PATH_UNIT)
                    }
                }
                continue
            }
            val p = s.points
            val n = s.sides
            outline[s.ordinal].apply {
                moveTo(p[0] * PATH_UNIT, p[1] * PATH_UNIT)
                for (k in 1 until n) lineTo(p[2 * k] * PATH_UNIT, p[2 * k + 1] * PATH_UNIT)
                close()
            }
            for (k in 0 until n) {
                val ax = p[2 * k]
                val ay = p[2 * k + 1]
                val bx = p[2 * ((k + 1) % n)]
                val by = p[2 * ((k + 1) % n) + 1]
                val i = first + k
                pieceX[i] = (ax + bx) / 3f
                pieceY[i] = (ay + by) / 3f
                pieceLit[i] = pieceX[i] <= 0.001f && pieceY[i] <= 0.001f
                if (pieceLit[i]) {
                    lit[s.ordinal].apply {
                        moveTo(0f, 0f)
                        lineTo(ax * PATH_UNIT, ay * PATH_UNIT)
                        lineTo(bx * PATH_UNIT, by * PATH_UNIT)
                        close()
                    }
                }
                piecePath[i].apply {
                    moveTo(0f, 0f)
                    lineTo(ax * PATH_UNIT, ay * PATH_UNIT)
                    lineTo(bx * PATH_UNIT, by * PATH_UNIT)
                    close()
                    offset(-pieceX[i] * PATH_UNIT, -pieceY[i] * PATH_UNIT)
                }
            }
        }
    }
}
