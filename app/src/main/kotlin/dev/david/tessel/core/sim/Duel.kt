package dev.david.tessel.core.sim

/** What the host picks for a duel. Every number a player can choose is here. */
class DuelSetup(
    /** Rounds a player has to win. */
    val target: Int,
    /** Health pips everyone starts each round with. */
    val hp: Int,
    /** 0 to [POWERS] - 1: how much shooting upgrade everyone starts with. */
    val power: Int,
) {
    companion object {
        val TARGETS = intArrayOf(1, 2, 3, 5)
        val HEALTH = intArrayOf(1, 3, 5, 8)
        val POWER_NAMES = arrayOf("None", "Light", "Medium", "Heavy")
        val POWERS get() = POWER_NAMES.size
        val DEFAULT = DuelSetup(target = 2, hp = 5, power = 0)
    }
}

/** Where each player starts a round: evenly round an ellipse, facing the middle. [rank] counts the players still in. */
object DuelSpawn {
    private const val RING_X = 560f
    private const val RING_Y = 230f

    private fun angle(rank: Int, n: Int) = rank * 2f * Math.PI.toFloat() / n + Math.PI.toFloat()

    fun x(rank: Int, n: Int) = ARENA_W / 2f + kotlin.math.cos(angle(rank, n)) * RING_X
    fun y(rank: Int, n: Int) = ARENA_H / 2f + kotlin.math.sin(angle(rank, n)) * RING_Y
    fun aimX(rank: Int, n: Int) = -kotlin.math.cos(angle(rank, n))
    fun aimY(rank: Int, n: Int) = -kotlin.math.sin(angle(rank, n))
}

/** State of a duel: no enemies, last player standing takes the round. All zero while a normal run is on. */
class Duel {
    var on = false
    var target = 2
    var round = 1

    /** Rounds won, by player. */
    val wins = IntArray(MAX_PLAYERS)

    /** Ticks left of the pause after a round; the next round or the end follows. */
    var pause = 0

    /** Who took the round that just ended, or -1 for a draw. */
    var roundWinner = -1

    /** Who took the match, or -1 while it is still going. */
    var champion = -1

    fun clear() {
        on = false
        round = 1
        wins.fill(0)
        pause = 0
        roundWinner = -1
        champion = -1
    }
}
