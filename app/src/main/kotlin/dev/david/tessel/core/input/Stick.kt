package dev.david.tessel.core.input

import kotlin.math.min
import kotlin.math.sqrt

/**
 * One floating thumb stick. Screen pixels in, a vector of length 0..1 out.
 * The base sits where the thumb first lands and trails behind a thumb that
 * wanders too far, so the player never has to look down to find it again.
 */
class Stick {
    var radius = 1f
    var deadZone = 0.12f

    /** Deflection that already counts as full output; reaching the rim is tiring. */
    var fullAt = 0.85f

    /** Past this many radii the base is dragged along. */
    var followAt = 1.6f

    var pointer = NO_POINTER
        private set
    var originX = 0f
        private set
    var originY = 0f
        private set
    var knobX = 0f
        private set
    var knobY = 0f
        private set

    var x = 0f
        private set
    var y = 0f
        private set

    /** Last direction that left the dead zone. Survives release, so aim does not snap back. */
    var dirX = 1f
        private set
    var dirY = 0f
        private set

    val held get() = pointer != NO_POINTER
    val engaged get() = x != 0f || y != 0f

    fun press(id: Int, px: Float, py: Float) {
        pointer = id
        originX = px
        originY = py
        knobX = px
        knobY = py
        x = 0f
        y = 0f
    }

    fun drag(px: Float, py: Float) {
        var dx = px - originX
        var dy = py - originY
        var dist = sqrt(dx * dx + dy * dy)
        if (dist < 0.001f) {
            knobX = originX
            knobY = originY
            x = 0f
            y = 0f
            return
        }

        val leash = followAt * radius
        if (dist > leash) {
            val pull = (dist - leash) / dist
            originX += dx * pull
            originY += dy * pull
            dx = px - originX
            dy = py - originY
            dist = leash
        }

        val ux = dx / dist
        val uy = dy / dist
        val reach = min(dist, radius)
        knobX = originX + ux * reach
        knobY = originY + uy * reach

        // Rescaled so output starts at zero on the dead zone edge instead of jumping.
        val deflection = reach / radius
        val strength = if (deflection <= deadZone) 0f else min((deflection - deadZone) / (fullAt - deadZone), 1f)
        x = ux * strength
        y = uy * strength
        if (strength > 0f) {
            dirX = ux
            dirY = uy
        }
    }

    fun release() {
        pointer = NO_POINTER
        knobX = originX
        knobY = originY
        x = 0f
        y = 0f
    }

    companion object {
        const val NO_POINTER = -1
    }
}
