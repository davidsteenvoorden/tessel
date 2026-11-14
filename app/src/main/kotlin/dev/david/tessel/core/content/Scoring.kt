package dev.david.tessel.core.content

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How a run turns into a score. Time alive, stages cleared and bosses beaten
 * are the team's; kills, upgrades bought and revives are each player's own.
 * The difficulty multiplies the lot, so a short run on Impossible can beat
 * a long one on Easy. Every figure lives here.
 */
object Scoring {
    const val PER_SECOND = 2
    const val PER_STAGE = 120
    const val PER_UPGRADE = 40
    const val PER_BOSS = 800
    const val PER_REVIVE = 150

    /** Points a kill is worth for each point of spawn cost. The pieces of a split cost nothing and still give one. */
    const val PER_COST = 3f

    /** What one kill of [type] is worth. A boss gives nothing here; beating it is [PER_BOSS]. */
    fun kill(type: Int): Int {
        val def = Bestiary.of(type)
        return if (def.boss) 0 else max(1, (def.cost * PER_COST).roundToInt())
    }

    fun total(
        difficulty: Difficulty,
        seconds: Int,
        stagesCleared: Int,
        bossesDown: Int,
        killPoints: Int,
        upgrades: Int,
        revives: Int,
    ): Int {
        val raw = killPoints + seconds * PER_SECOND + stagesCleared * PER_STAGE +
            upgrades * PER_UPGRADE + bossesDown * PER_BOSS + revives * PER_REVIVE
        return (raw * difficulty.score).roundToInt()
    }

    /** The same sum laid out so the end screen can show where the points came from. */
    fun breakdown(
        difficulty: Difficulty,
        seconds: Int,
        stagesCleared: Int,
        bossesDown: Int,
        killPoints: Int,
        upgrades: Int,
        revives: Int,
    ) = Breakdown(
        kills = killPoints,
        time = seconds * PER_SECOND,
        stages = stagesCleared * PER_STAGE,
        upgrades = upgrades * PER_UPGRADE,
        bosses = bossesDown * PER_BOSS,
        revives = revives * PER_REVIVE,
        multiplier = difficulty.score,
    )

    class Breakdown(
        val kills: Int,
        val time: Int,
        val stages: Int,
        val upgrades: Int,
        val bosses: Int,
        val revives: Int,
        val multiplier: Float,
    ) {
        val raw get() = kills + time + stages + upgrades + bosses + revives
        val total get() = (raw * multiplier).roundToInt()
    }
}
