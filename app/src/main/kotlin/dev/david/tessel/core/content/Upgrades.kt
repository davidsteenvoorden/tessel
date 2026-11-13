package dev.david.tessel.core.content

import kotlin.math.pow
import kotlin.math.roundToInt

class UpgradeDef(
    val name: String,
    val blurb: String,
    val basePrice: Int,
    val maxLevel: Int,
    /** What one level adds: a share of the base value, or a whole count for counted upgrades. */
    val step: Float,
)

/** Everything the between-stage shop sells. Ids index into each player's level array. */
object Upgrades {
    const val DAMAGE = 0
    const val FIRE_RATE = 1
    const val MULTISHOT = 2
    const val VELOCITY = 3
    const val PIERCE = 4
    const val RANGE = 5
    const val SPEED = 6
    const val MAX_HEALTH = 7
    const val WEALTH = 8
    const val MAGNET = 9
    const val CALIBER = 10

    private val defs = arrayOf(
        UpgradeDef("Damage", "+15% damage per bullet", basePrice = 15, maxLevel = 25, step = 0.15f),
        UpgradeDef("Fire Rate", "+10% shots per second", basePrice = 18, maxLevel = 20, step = 0.10f),
        UpgradeDef("Multishot", "One more bullet per shot", basePrice = 45, maxLevel = 8, step = 1f),
        UpgradeDef("Velocity", "+10% bullet speed", basePrice = 10, maxLevel = 10, step = 0.10f),
        UpgradeDef("Pierce", "Bullets go through one more enemy", basePrice = 40, maxLevel = 5, step = 1f),
        UpgradeDef("Range", "+12% bullet range", basePrice = 10, maxLevel = 10, step = 0.12f),
        UpgradeDef("Speed", "+6% move speed", basePrice = 15, maxLevel = 10, step = 0.06f),
        UpgradeDef("Max Health", "One more health pip, filled", basePrice = 30, maxLevel = 10, step = 1f),
        UpgradeDef("Wealth", "+15% coin value", basePrice = 25, maxLevel = 10, step = 0.15f),
        UpgradeDef("Magnet", "+25% coin pickup reach", basePrice = 8, maxLevel = 8, step = 0.25f),
        UpgradeDef("Caliber", "+15% bullet size", basePrice = 12, maxLevel = 8, step = 0.15f),
    )

    val count get() = defs.size

    fun of(id: Int) = defs[id]

    /**
     * Each level costs 30% more than the one before, and everything costs
     * more each stage, because coins are worth more each stage too. Buying the
     * same thing again in one visit is allowed; the climbing price is the limit.
     */
    fun price(id: Int, level: Int, stage: Int) =
        (defs[id].basePrice * LEVEL_GROWTH.pow(level) * inflation(stage)).roundToInt()

    fun inflation(stage: Int) = STAGE_GROWTH.pow(stage - 1)

    private const val LEVEL_GROWTH = 1.3
    private const val STAGE_GROWTH = 1.17

    const val HEAL_AMOUNT = 2
    const val HEALS_PER_SHOP = 2

    /** Keeps pace with coin value, so a heal always costs about the same number of kills. */
    fun healPrice(stage: Int) = (14 * Balance.COIN_GROWTH.pow(stage - 1)).roundToInt()

    fun rerollPrice(stage: Int, rerolls: Int) = ((REROLL_BASE + REROLL_STEP * rerolls) * inflation(stage)).roundToInt()

    private const val REROLL_BASE = 5
    private const val REROLL_STEP = 3
}
