package dev.david.tessel.platform.input

import android.view.InputDevice
import android.view.MotionEvent
import dev.david.tessel.core.sim.PlayerInput
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A game controller, Bluetooth or USB. Left stick (or the d-pad) moves, right
 * stick aims and fires while it is pushed, and the right trigger fires where
 * you last aimed. It works next to the touch sticks: whichever is in use wins.
 *
 * Axis events arrive on the main thread and the game thread reads once per
 * frame; a torn read between the two halves of a stick costs nothing.
 */
class Gamepad {
    @Volatile private var moveX = 0f
    @Volatile private var moveY = 0f
    @Volatile private var aimX = 0f
    @Volatile private var aimY = 0f
    @Volatile private var trigger = 0f
    private var lastAimX = 1f
    private var lastAimY = 0f

    /** Takes axis events from a controller. Returns false for anything else. */
    fun onMotion(e: MotionEvent): Boolean {
        if (!isController(e.source) || e.actionMasked != MotionEvent.ACTION_MOVE) return false
        val device = e.device

        var mx = axis(e, device, MotionEvent.AXIS_X)
        var my = axis(e, device, MotionEvent.AXIS_Y)
        // Some pads report the d-pad as a hat axis rather than as keys.
        if (mx == 0f && my == 0f) {
            mx = axis(e, device, MotionEvent.AXIS_HAT_X)
            my = axis(e, device, MotionEvent.AXIS_HAT_Y)
        }
        moveX = mx
        moveY = my

        // Most pads put the right stick on Z and RZ; some use RX and RY. Take whichever moved.
        val zx = axis(e, device, MotionEvent.AXIS_Z)
        val zy = axis(e, device, MotionEvent.AXIS_RZ)
        val rx = axis(e, device, MotionEvent.AXIS_RX)
        val ry = axis(e, device, MotionEvent.AXIS_RY)
        if (zx * zx + zy * zy >= rx * rx + ry * ry) {
            aimX = zx
            aimY = zy
        } else {
            aimX = rx
            aimY = ry
        }
        trigger = maxOf(axis(e, device, MotionEvent.AXIS_RTRIGGER), axis(e, device, MotionEvent.AXIS_GAS))
        return true
    }

    /** Lays the controller over what the touch sticks already wrote, where the controller is in use. */
    fun apply(out: PlayerInput) {
        val mx = moveX
        val my = moveY
        if (mx != 0f || my != 0f) {
            out.moveX = mx
            out.moveY = my
        }
        val ax = aimX
        val ay = aimY
        val pushed = sqrt(ax * ax + ay * ay)
        if (pushed > FIRE_AT) {
            lastAimX = ax / pushed
            lastAimY = ay / pushed
            out.aimX = lastAimX
            out.aimY = lastAimY
            out.fire = true
        } else if (trigger > TRIGGER_AT) {
            out.aimX = lastAimX
            out.aimY = lastAimY
            out.fire = true
        }
    }

    private fun axis(e: MotionEvent, device: InputDevice?, axis: Int): Float {
        val v = e.getAxisValue(axis)
        // The controller says how much of each axis is noise around the centre; at least 15%.
        val flat = maxOf(device?.getMotionRange(axis, e.source)?.flat ?: 0f, DEAD_ZONE)
        if (abs(v) <= flat) return 0f
        // Rescaled so the stick still starts from zero just past the dead zone.
        return (abs(v) - flat) / (1f - flat) * (if (v < 0f) -1f else 1f)
    }

    companion object {
        private const val DEAD_ZONE = 0.15f
        private const val FIRE_AT = 0.35f
        private const val TRIGGER_AT = 0.3f

        fun isController(source: Int) =
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
                source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
    }
}
