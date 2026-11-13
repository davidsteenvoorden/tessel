package dev.david.tessel.core.sim

/**
 * Things the sim wants the outside world to react to (shards, haptics, sound, menus).
 * Filled during steps, read and cleared once per frame.
 */
class Events(private val capacity: Int = 512) {
    val kind = IntArray(capacity)

    /** Player index for player events, enemy type for enemy events, stage number for stage events. */
    val who = IntArray(capacity)
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val dx = FloatArray(capacity)
    val dy = FloatArray(capacity)
    val angle = FloatArray(capacity)

    var count = 0
        private set

    fun add(kind: Int, who: Int, x: Float = 0f, y: Float = 0f, dx: Float = 0f, dy: Float = 0f, angle: Float = 0f) {
        if (count == capacity) return
        val i = count++
        this.kind[i] = kind
        this.who[i] = who
        this.x[i] = x
        this.y[i] = y
        this.dx[i] = dx
        this.dy[i] = dy
        this.angle[i] = angle
    }

    fun clear() {
        count = 0
    }

    fun has(kind: Int): Boolean {
        for (i in 0 until count) if (this.kind[i] == kind) return true
        return false
    }

    companion object {
        const val SHOT = 1
        const val ENEMY_HIT = 2
        const val ENEMY_KILLED = 3
        const val PLAYER_HIT = 4
        const val PLAYER_DOWN = 5
        const val COIN = 6
        const val RUN_OVER = 7

        /** Spawning has stopped; the stragglers are all that is left. */
        const val STAGE_CLEAR = 8
        const val SHOP_OPEN = 9

        /** An enemy removed without a kill, when the shop sweeps the arena. */
        const val ENEMY_GONE = 10
        const val STAGE_START = 11
        const val REVIVED = 12

        /** A boss walked in; [who] is its type. */
        const val BOSS_IN = 13
        const val BOSS_DOWN = 14

        /** An enemy shot left [who] = its id, at x, y with velocity dx, dy, on the tick in angle. Only for other phones. */
        const val HOSTILE_SPAWN = 15

        /** The enemy shot with id [who] was used up hitting someone. Only for other phones. */
        const val HOSTILE_GONE = 16
    }
}
