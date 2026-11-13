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

class RunSummary(
    val seconds: Int,
    val stage: Int,
    val kills: Int,
    val coins: Int,
    val difficulty: Difficulty,
    val players: Int = 1,
)
