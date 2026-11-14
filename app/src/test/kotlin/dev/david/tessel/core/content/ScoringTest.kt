package dev.david.tessel.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {
    @Test
    fun aKillIsWorthItsCostAndNeverNothing() {
        assertEquals(3, Scoring.kill(Bestiary.DOT))
        assertEquals(12, Scoring.kill(Bestiary.SLAB))
        // Pieces of a split cost the director nothing, and still count.
        assertEquals(1, Scoring.kill(Bestiary.HALFLING))
        assertEquals(1, Scoring.kill(Bestiary.SLIVER))
        for (type in 0 until Bestiary.size) if (!Bestiary.of(type).boss) assertTrue(Scoring.kill(type) >= 1)
    }

    @Test
    fun aBossGivesNothingForTheKillItself() {
        for (boss in Bestiary.bossOrder) assertEquals(0, Scoring.kill(boss))
    }

    @Test
    fun theTotalIsEveryPartTimesTheDifficulty() {
        val normal = Scoring.total(Difficulty.NORMAL, seconds = 100, stagesCleared = 3, bossesDown = 1, killPoints = 500, upgrades = 4, revives = 2)
        val expected = 500 + 100 * Scoring.PER_SECOND + 3 * Scoring.PER_STAGE + 4 * Scoring.PER_UPGRADE + Scoring.PER_BOSS + 2 * Scoring.PER_REVIVE
        assertEquals(expected, normal)
        val hard = Scoring.total(Difficulty.HARD, 100, 3, 1, 500, 4, 2)
        assertEquals((expected * Difficulty.HARD.score).toInt(), hard)
    }

    @Test
    fun harderIsWorthMoreForTheSameRun() {
        val scores = Difficulty.entries.map { Scoring.total(it, 300, 5, 1, 900, 10, 0) }
        for (i in 1 until scores.size) assertTrue("${Difficulty.entries[i]} should beat ${Difficulty.entries[i - 1]}", scores[i] > scores[i - 1])
    }

    @Test
    fun theBreakdownAddsUpToTheTotal() {
        for (d in Difficulty.entries) {
            val total = Scoring.total(d, 421, 6, 1, 1_234, 9, 3)
            val parts = Scoring.breakdown(d, 421, 6, 1, 1_234, 9, 3)
            assertEquals(total, parts.total)
            assertEquals(parts.kills + parts.time + parts.stages + parts.upgrades + parts.bosses + parts.revives, parts.raw)
        }
    }

    @Test
    fun anEmptyRunScoresNothing() {
        assertEquals(0, Scoring.total(Difficulty.IMPOSSIBLE, 0, 0, 0, 0, 0, 0))
    }
}
