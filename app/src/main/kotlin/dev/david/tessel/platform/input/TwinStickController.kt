package dev.david.tessel.platform.input

import android.view.MotionEvent
import dev.david.tessel.core.input.Stick
import dev.david.tessel.core.sim.PlayerInput

/** What the renderer needs to draw one stick, copied out under the lock. */
class StickView {
    var held = false
    var originX = 0f
    var originY = 0f
    var knobX = 0f
    var knobY = 0f

    internal fun copy(stick: Stick, restX: Float, restY: Float) {
        held = stick.held
        if (held) {
            originX = stick.originX
            originY = stick.originY
            knobX = stick.knobX
            knobY = stick.knobY
        } else {
            originX = restX
            originY = restY
            knobX = restX
            knobY = restY
        }
    }
}

/**
 * Left part of the screen moves, right part aims and fires. Touch events come
 * in on the main thread and the game thread reads once per frame, so both
 * sides go through one lock.
 */
class TwinStickController(private val density: Float) {
    private val lock = Any()
    private val move = Stick()
    private val aim = Stick()

    private var width = 0f
    private var height = 0f
    private var minX = 0f
    private var minY = 0f
    private var maxX = 0f
    private var maxY = 0f
    private var lastAimAt = Long.MIN_VALUE / 2

    @Volatile var leftHanded = false
    var stickRadiusDp = 56f
        set(value) {
            field = value
            synchronized(lock) { applyRadius() }
        }

    val stickRadius get() = stickRadiusDp * density

    init {
        applyRadius()
    }

    fun layout(w: Int, h: Int, insetLeft: Int, insetTop: Int, insetRight: Int, insetBottom: Int) {
        synchronized(lock) {
            width = w.toFloat()
            height = h.toFloat()
            val pad = EDGE_PAD_DP * density
            minX = insetLeft + pad
            minY = insetTop + pad
            maxX = w - insetRight - pad
            maxY = h - insetBottom - pad
        }
    }

    fun onTouch(e: MotionEvent) {
        synchronized(lock) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> press(e, e.actionIndex)
                MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                    when (e.getPointerId(i)) {
                        move.pointer -> move.drag(e.getX(i), e.getY(i))
                        aim.pointer -> aim.drag(e.getX(i), e.getY(i))
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> when (e.getPointerId(e.actionIndex)) {
                    move.pointer -> move.release()
                    aim.pointer -> aim.release()
                }
                MotionEvent.ACTION_CANCEL -> {
                    move.release()
                    aim.release()
                }
            }
        }
    }

    private fun press(e: MotionEvent, index: Int) {
        val x = e.getX(index)
        val y = e.getY(index)
        // The strip down the middle belongs to neither thumb.
        val onLeft = x < width * 0.45f
        val onRight = x > width * 0.55f
        val stick = when {
            onLeft -> if (leftHanded) aim else move
            onRight -> if (leftHanded) move else aim
            else -> return
        }
        if (stick.held) return
        stick.press(e.getPointerId(index), x.coerceIn(minX, maxX), y.coerceIn(minY, maxY))
        stick.drag(x, y)
    }

    /** Called once per frame from the game thread. */
    fun read(out: PlayerInput, moveView: StickView, aimView: StickView, nowMillis: Long) {
        synchronized(lock) {
            out.moveX = move.x
            out.moveY = move.y

            // Lifting the thumb to re-centre must not cut the stream of shots.
            if (aim.engaged) lastAimAt = nowMillis
            val firing = nowMillis - lastAimAt <= FIRE_HOLD_MILLIS
            out.fire = firing
            out.aimX = if (firing) aim.dirX else 0f
            out.aimY = if (firing) aim.dirY else 0f

            val restY = height - stickRadius - REST_LIFT_DP * density
            val leftRest = width * 0.15f
            val rightRest = width * 0.85f
            moveView.copy(move, if (leftHanded) rightRest else leftRest, restY)
            aimView.copy(aim, if (leftHanded) leftRest else rightRest, restY)
        }
    }

    private fun applyRadius() {
        move.radius = stickRadius
        aim.radius = stickRadius
    }

    private companion object {
        const val EDGE_PAD_DP = 28f
        const val REST_LIFT_DP = 40f
        const val FIRE_HOLD_MILLIS = 200L
    }
}
