package dev.david.tessel.core.sim

/**
 * What took a player's last health, in one small number: the type of enemy,
 * plus a flag for whether its shot or its body did it. -1 is nothing.
 */
object Cause {
    const val NONE = -1
    private const val SHOT = 0x40

    fun touch(type: Int) = if (type < 0) NONE else type
    fun shot(type: Int) = if (type < 0) NONE else type or SHOT

    fun type(code: Int) = if (code < 0) -1 else code and 0x3F
    fun isShot(code: Int) = code >= 0 && code and SHOT != 0
}
