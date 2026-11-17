package dev.david.tessel.core.stats

import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Difficulty

enum class Tier(val label: String) { EASY("Easy"), MEDIUM("Medium"), HARD("Hard"), LEGENDARY("Legendary") }

/** One goal. Its progress is worked out from the [Ledger], so earlier runs count too. */
class Achievement(
    val name: String,
    val tier: Tier,
    val text: String,
    val target: Int,
    val progress: (Ledger) -> Int,
) {
    fun done(l: Ledger) = progress(l) >= target
}

/** Every goal, grouped by how far off it is. Counters step up so there is always a next one. */
object Achievements {
    private fun best(l: Ledger, d: Difficulty) = l.bestOn(d)?.stage ?: 0
    private fun coop(l: Ledger) = l.runs.filter { it.players > 1 }
    private fun bossesMet(l: Ledger) = Bestiary.bossOrder.count { l.defeated[it] > 0 }

    private fun a(name: String, tier: Tier, text: String, target: Int, progress: (Ledger) -> Int) =
        Achievement(name, tier, text, target, progress)

    val all: List<Achievement> = listOf(
        // Enemies defeated
        a("Warm Up", Tier.EASY, "Defeat 100 enemies.", 100) { it.totals.kills },
        a("Pest Control", Tier.EASY, "Defeat 500 enemies.", 500) { it.totals.kills },
        a("Crowd Control", Tier.MEDIUM, "Defeat 5,000 enemies.", 5_000) { it.totals.kills },
        a("Swarm Breaker", Tier.HARD, "Defeat 25,000 enemies.", 25_000) { it.totals.kills },
        a("Clean Sweep", Tier.LEGENDARY, "Defeat 100,000 enemies.", 100_000) { it.totals.kills },

        // Bosses
        a("First Down", Tier.EASY, "Beat a boss.", 1) { it.totals.bosses },
        a("Giant Slayer", Tier.MEDIUM, "Beat 10 bosses.", 10) { it.totals.bosses },
        a("Whole Roster", Tier.HARD, "Beat every kind of boss at least once.", Bestiary.bossOrder.size) { bossesMet(it) },
        a("Boss Hunter", Tier.HARD, "Beat 50 bosses.", 50) { it.totals.bosses },
        a("Last One Standing", Tier.LEGENDARY, "Beat 250 bosses.", 250) { it.totals.bosses },

        // Logbook
        a("Field Notes", Tier.EASY, "Meet 10 kinds of enemy.", 10) { l -> (0 until Bestiary.size).count { l.seen[it] } },
        a("Naturalist", Tier.HARD, "Meet every kind of enemy.", Bestiary.size) { l -> (0 until Bestiary.size).count { l.seen[it] } },

        // Depth
        a("Getting Somewhere", Tier.EASY, "Reach stage 5.", 5) { it.totals.deepest },
        a("Double Digits", Tier.MEDIUM, "Reach stage 10.", 10) { it.totals.deepest },
        a("Deep Cut", Tier.HARD, "Reach stage 20.", 20) { it.totals.deepest },
        a("Bottomless", Tier.LEGENDARY, "Reach stage 40.", 40) { it.totals.deepest },

        // Difficulty
        a("Not Easy", Tier.MEDIUM, "Reach stage 10 on Hard.", 10) { best(it, Difficulty.HARD) },
        a("Long Odds", Tier.HARD, "Reach stage 10 on Impossible.", 10) { best(it, Difficulty.IMPOSSIBLE) },
        a("Against the Odds", Tier.LEGENDARY, "Reach stage 25 on Impossible.", 25) { best(it, Difficulty.IMPOSSIBLE) },

        // Habit
        a("Regular", Tier.EASY, "Play 10 runs.", 10) { it.totals.runs },
        a("Dedicated", Tier.MEDIUM, "Play 50 runs.", 50) { it.totals.runs },
        a("Lifer", Tier.HARD, "Play 250 runs.", 250) { it.totals.runs },
        a("An Hour In", Tier.EASY, "Play for 1 hour in total.", 3_600) { it.totals.seconds },
        a("Ten Hours", Tier.MEDIUM, "Play for 10 hours in total.", 36_000) { it.totals.seconds },
        a("Full Time", Tier.HARD, "Play for 50 hours in total.", 180_000) { it.totals.seconds },

        // Spending and shooting
        a("Pocket Money", Tier.EASY, "Collect 1,000 coins.", 1_000) { it.totals.coins },
        a("Hoarder", Tier.MEDIUM, "Collect 100,000 coins.", 100_000) { it.totals.coins },
        a("Shopper", Tier.EASY, "Buy 25 upgrades.", 25) { it.totals.upgrades },
        a("Gear Head", Tier.MEDIUM, "Buy 250 upgrades.", 250) { it.totals.upgrades },
        a("Fully Stocked", Tier.HARD, "Buy 1,500 upgrades.", 1_500) { it.totals.upgrades },
        a("Trigger Happy", Tier.EASY, "Fire 10,000 shots.", 10_000) { it.totals.shots },
        a("Belt Fed", Tier.HARD, "Fire 1,000,000 shots.", 1_000_000) { it.totals.shots },

        // Together
        a("Pull Up", Tier.EASY, "Revive a teammate.", 1) { it.totals.revives },
        a("Field Medic", Tier.MEDIUM, "Revive 25 teammates.", 25) { it.totals.revives },
        a("Guardian", Tier.HARD, "Revive 100 teammates.", 100) { it.totals.revives },
        a("Better Together", Tier.EASY, "Finish a co-op run.", 1) { coop(it).size },
        a("Regular Squad", Tier.MEDIUM, "Finish 10 co-op runs.", 10) { coop(it).size },
        a("Deep Together", Tier.HARD, "Reach stage 15 in co-op.", 15) { l -> coop(l).maxOfOrNull { it.stage } ?: 0 },

        // The last boss
        a("Core Breaker", Tier.LEGENDARY, "Beat the Core and complete a run.", 1) { it.totals.completed },
        a("Hard Finish", Tier.LEGENDARY, "Complete a run on Hard.", 1) { l -> l.runs.count { it.won && it.difficulty == Difficulty.HARD } },
        a("Impossible Finish", Tier.LEGENDARY, "Complete a run on Impossible.", 1) { l -> l.runs.count { it.won && it.difficulty == Difficulty.IMPOSSIBLE } },
        a("Repeat Customer", Tier.LEGENDARY, "Complete 5 runs.", 5) { it.totals.completed },

        // Duels
        a("First Duel", Tier.EASY, "Win a duel.", 1) { it.totals.pvpWins },
        a("Rival", Tier.MEDIUM, "Win 10 duels.", 10) { it.totals.pvpWins },
        a("Undisputed", Tier.HARD, "Win 50 duels.", 50) { it.totals.pvpWins },
    )

    fun doneCount(l: Ledger) = all.count { it.done(l) }
}
