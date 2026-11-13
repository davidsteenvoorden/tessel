package dev.david.tessel.core.sim

/**
 * What one player wants this tick. The sim only ever sees this, never touch
 * events, so a remote player or a bot can drive it the same way.
 */
class PlayerInput {
    /** Length 0..1. */
    var moveX = 0f
    var moveY = 0f

    /** Unit vector while aiming, zero otherwise. */
    var aimX = 0f
    var aimY = 0f

    var fire = false
    var ability = false

    /**
     * Set for a player on the other phone: where that phone says it is. The
     * host follows the report instead of integrating the stick, so the remote
     * player sees their own movement with no delay at all.
     */
    var hasPosition = false
    var posX = 0f
    var posY = 0f

    fun clear() {
        moveX = 0f
        moveY = 0f
        aimX = 0f
        aimY = 0f
        fire = false
        ability = false
        hasPosition = false
    }

    fun copyFrom(other: PlayerInput) {
        moveX = other.moveX
        moveY = other.moveY
        aimX = other.aimX
        aimY = other.aimY
        fire = other.fire
        ability = other.ability
        hasPosition = other.hasPosition
        posX = other.posX
        posY = other.posY
    }
}
