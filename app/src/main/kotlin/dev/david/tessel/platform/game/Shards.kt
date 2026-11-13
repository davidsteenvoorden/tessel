package dev.david.tessel.platform.game

import android.graphics.Canvas
import android.graphics.Paint
import dev.david.tessel.core.content.Shape
import dev.david.tessel.core.rng.Rng
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The pieces a body breaks into when it dies. Purely visual, so it lives
 * outside the sim, runs on frame time and draws from its own random source.
 * Positions are in arena units.
 */
class Shards(private val capacity: Int = 500) {
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val rot = FloatArray(capacity)
    private val spin = FloatArray(capacity)
    private val age = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val size = FloatArray(capacity)
    private val color = IntArray(capacity)
    private val piece = IntArray(capacity)
    private var count = 0

    private val rng = Rng(0x5EED_5A4DL)

    fun burst(shape: Shape, x: Float, y: Float, radius: Float, angle: Float, dirX: Float, dirY: Float, body: Int, facet: Int) {
        // Round bodies are drawn unrotated, so their pieces start unrotated too.
        val a = if (shape == Shape.CIRCLE) 0f else angle
        val c = cos(a)
        val s = sin(a)
        val degrees = a * 180f / PI.toFloat()
        val first = Bodies.firstPiece[shape.ordinal]
        for (k in 0 until Bodies.pieceCount[shape.ordinal]) {
            val i = first + k
            val ox = (Bodies.pieceX[i] * c - Bodies.pieceY[i] * s) * radius
            val oy = (Bodies.pieceX[i] * s + Bodies.pieceY[i] * c) * radius
            emit(i, x, y, ox, oy, degrees, radius, if (Bodies.pieceLit[i]) facet else body, dirX, dirY)
        }
    }

    private fun emit(kind: Int, cx: Float, cy: Float, ox: Float, oy: Float, degrees: Float, scale: Float, tint: Int, dirX: Float, dirY: Float) {
        if (count == capacity) return
        val i = count++
        val len = sqrt(ox * ox + oy * oy).coerceAtLeast(0.001f)
        val burst = rng.range(70f, 170f)
        val shove = rng.range(50f, 150f)
        x[i] = cx + ox
        y[i] = cy + oy
        vx[i] = ox / len * burst + dirX * shove
        vy[i] = oy / len * burst + dirY * shove
        rot[i] = degrees
        spin[i] = rng.range(200f, 560f) * rng.sign()
        age[i] = 0f
        life[i] = rng.range(0.38f, 0.55f)
        size[i] = scale / PATH_UNIT
        color[i] = tint
        piece[i] = kind
    }

    fun update(dt: Float) {
        val drag = exp(-4f * dt)
        var i = 0
        while (i < count) {
            age[i] += dt
            if (age[i] >= life[i]) {
                removeAt(i)
                continue
            }
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            vx[i] *= drag
            vy[i] *= drag
            rot[i] += spin[i] * dt
            i++
        }
    }

    fun draw(canvas: Canvas, view: Viewport, paint: Paint) {
        for (i in 0 until count) {
            val t = age[i] / life[i]
            val shrink = 1f - 0.35f * t
            paint.color = color[i]
            if (t > FADE_FROM) paint.alpha = (255 * (1f - (t - FADE_FROM) / (1f - FADE_FROM))).toInt()
            val s = size[i] * view.scale * shrink
            canvas.save()
            canvas.translate(view.screenX(x[i]), view.screenY(y[i]))
            canvas.rotate(rot[i])
            canvas.scale(s, s)
            canvas.drawPath(Bodies.piecePath[piece[i]], paint)
            canvas.restore()
        }
        paint.alpha = 255
    }

    fun clear() {
        count = 0
    }

    private fun removeAt(i: Int) {
        val last = --count
        if (i == last) return
        x[i] = x[last]
        y[i] = y[last]
        vx[i] = vx[last]
        vy[i] = vy[last]
        rot[i] = rot[last]
        spin[i] = spin[last]
        age[i] = age[last]
        life[i] = life[last]
        size[i] = size[last]
        color[i] = color[last]
        piece[i] = piece[last]
    }

    private companion object {
        const val FADE_FROM = 0.55f
    }
}
