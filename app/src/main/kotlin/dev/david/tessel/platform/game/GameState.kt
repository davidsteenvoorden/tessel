package dev.david.tessel.platform.game

import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.content.Scoring
import dev.david.tessel.core.sim.Cause
import dev.david.tessel.core.tutorial.TutorialStep

/** What the screen is doing, as the menus see it. */
enum class Mode { HOME, PLAYING, PAUSED, SHOP, OVER }

/** Where the tutorial is, for the card over the arena. [progress] is in twentieths. */
data class TutorialView(
    val step: TutorialStep,
    val progress: Int,
    val hit: Boolean,
    val passed: Boolean,
)

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
    /** A rare upgrade: [levels] levels in one buy. */
    val rare: Boolean = false,
    val levels: Int = 1,
)

/** One perk on offer after a boss. */
class PerkOffer(val slot: Int, val name: String, val blurb: String)

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
    /** Perks to choose from; the shop cannot be left until one is taken. */
    val perkOffers: List<PerkOffer> = emptyList(),
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
    val score: Int = 0,
)

/** How a duel ended: who won, and the rounds each player took. */
class DuelResult(
    val won: Boolean,
    /** Name of the winner, or empty when nobody was left. */
    val champion: String,
    val rounds: Int,
    val wins: List<Pair<String, Int>>,
)

/** A run so far, or as it ended. [kills], [coins] and [score] are this phone's player's; [team] has everyone. */
class RunSummary(
    val seconds: Int,
    val stage: Int,
    val kills: Int,
    val coins: Int,
    val difficulty: Difficulty,
    val players: Int = 1,
    val bosses: Int = 0,
    val team: List<PlayerStats> = emptyList(),
    /** This phone's player's score, and where it came from. */
    val score: Int = 0,
    val breakdown: Scoring.Breakdown? = null,
    /** A [Cause] code: what took this phone's player's last health. -1 while the run goes on. */
    val killedBy: Int = Cause.NONE,
    /** The best solo score on this difficulty so far. */
    val newBest: Boolean = false,
    /** Names of the perks taken so far. */
    val perks: List<String> = emptyList(),
    /** Set when the match was a duel instead of a run. */
    val duel: DuelResult? = null,
    /** The Core and its eight bosses fell: the run is completed. */
    val won: Boolean = false,
) {
    val you get() = team.firstOrNull { it.you }

    fun withBest(best: Boolean) = RunSummary(seconds, stage, kills, coins, difficulty, players, bosses, team, score, breakdown, killedBy, best, perks, duel, won)
}
