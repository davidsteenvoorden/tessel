package dev.david.tessel.platform.game

import dev.david.tessel.core.content.Difficulty

/** What the screen is doing, as the menus see it. */
enum class Mode { HOME, PLAYING, PAUSED, SHOP, OVER }

/** One shop slot, copied out of the sim for the menu to draw. */
class ShopOffer(
    val slot: Int,
    val name: String,
    val blurb: String,
    val level: Int,
    val maxLevel: Int,
    val price: Int,
    /** Bought to its last level. */
    val maxed: Boolean,
    val affordable: Boolean,
)

class ShopView(
    val stage: Int,
    val coins: Int,
    val hp: Int,
    val maxHp: Int,
    val offers: List<ShopOffer>,
    val healPrice: Int,
    val healsLeft: Int,
    val canHeal: Boolean,
    val rerollPrice: Int,
    val canReroll: Boolean,
    /** Co-op: the next stage starts when everyone is ready. */
    val coop: Boolean = false,
    val selfReady: Boolean = false,
    val readyCount: Int = 0,
    val teamSize: Int = 1,
)

/** One player's column in the run's statistics. */
class PlayerStats(
    val name: String,
    val you: Boolean,
    /** False once their phone left the game for good. */
    val present: Boolean,
    val kills: Int,
    val damage: Int,
    val shots: Int,
    val coins: Int,
    val hitsTaken: Int,
    val downed: Int,
    val revives: Int,
    val upgrades: Int,
)

/** A run so far, or as it ended. [kills] and [coins] are this phone's player's; [team] has everyone. */
class RunSummary(
    val seconds: Int,
    val stage: Int,
    val kills: Int,
    val coins: Int,
    val difficulty: Difficulty,
    val players: Int = 1,
    val bosses: Int = 0,
    val team: List<PlayerStats> = emptyList(),
)
