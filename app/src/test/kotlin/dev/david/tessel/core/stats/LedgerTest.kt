package dev.david.tessel.core.stats

import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.sim.Cause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTest {
    private fun run(
        score: Int,
        difficulty: Difficulty = Difficulty.NORMAL,
        stage: Int = 5,
        players: Int = 1,
        left: Boolean = false,
        at: Long = score.toLong(),
    ) = RunEntry(
        endedAt = at, difficulty = difficulty, stage = stage, seconds = 300, score = score, kills = 100, coins = 200, bosses = 1,
        players = players, killedBy = if (left) Cause.NONE else Cause.shot(Bestiary.TICK), left = left,
        damage = 1_000, shots = 500, hitsTaken = 6, downed = 1, revives = 2, upgrades = 7,
    )

    @Test
    fun aRunGoesInTheHistoryNewestFirst() {
        val l = Ledger()
        l.record(run(100))
        l.record(run(300))
        l.record(run(200))
        assertEquals(listOf(200, 300, 100), l.runs.map { it.score })
    }

    @Test
    fun totalsAddEveryRunUp() {
        val l = Ledger()
        l.record(run(100, stage = 3))
        l.record(run(200, stage = 8))
        val t = l.totals
        assertEquals(2, t.runs)
        assertEquals(600, t.seconds)
        assertEquals(200, t.kills)
        assertEquals(14, t.upgrades)
        assertEquals(8, t.deepest)
    }

    @Test
    fun theBestScoreIsKeptPerDifficulty() {
        val l = Ledger()
        assertTrue(l.record(run(100, Difficulty.NORMAL)))
        assertFalse(l.record(run(90, Difficulty.NORMAL)))
        assertTrue(l.record(run(150, Difficulty.NORMAL, stage = 9)))
        assertTrue(l.record(run(40, Difficulty.EASY)))
        assertEquals(150, l.bestOn(Difficulty.NORMAL)!!.score)
        assertEquals(9, l.bestOn(Difficulty.NORMAL)!!.stage)
        assertEquals(40, l.bestOn(Difficulty.EASY)!!.score)
        assertNull(l.bestOn(Difficulty.HARD))
    }

    @Test
    fun teamRunsAndWalkedAwayRunsNeverSetARecord() {
        val l = Ledger()
        assertFalse(l.record(run(9_999, players = 3)))
        assertFalse(l.record(run(8_888, left = true)))
        assertNull(l.bestOn(Difficulty.NORMAL))
        // They still count as played.
        assertEquals(2, l.totals.runs)
    }

    @Test
    fun theHistoryKeepsTheLatestTwoHundred() {
        val l = Ledger()
        for (i in 1..Ledger.MAX_RUNS + 30) l.record(run(i))
        assertEquals(Ledger.MAX_RUNS, l.runs.size)
        assertEquals(Ledger.MAX_RUNS + 30, l.runs.first().score)
        // The totals remember the ones that dropped off.
        assertEquals(Ledger.MAX_RUNS + 30, l.totals.runs)
    }

    @Test
    fun theLogbookFillsInFromATally() {
        val l = Ledger()
        val t = Tally()
        t.see(Bestiary.DOT, 1)
        t.defeat(Bestiary.DOT, 1)
        t.defeat(Bestiary.DOT, 1)
        t.defeat(Bestiary.SLAB, 2)
        t.slain(Bestiary.SLAB, 2)
        l.merge(t)
        assertTrue(l.seen[Bestiary.DOT])
        assertEquals(2, l.defeated[Bestiary.DOT])
        // Beating something means it was met, even if the scan never caught it.
        assertTrue(l.seen[Bestiary.SLAB])
        assertEquals(1, l.defeated[Bestiary.SLAB])
        assertEquals(1, l.killedYou[Bestiary.SLAB])
        assertFalse(l.seen[Bestiary.TICK])

        val again = Tally()
        again.defeat(Bestiary.DOT, 1)
        again.see(Bestiary.SLAB, 1)
        l.merge(again)
        assertEquals(3, l.defeated[Bestiary.DOT])
        // The earliest stage it was met on stays.
        assertEquals(1, l.firstStage[Bestiary.SLAB])
    }

    @Test
    fun aTallyEmptiesOnceMerged() {
        val t = Tally()
        assertTrue(t.isEmpty)
        t.defeat(Bestiary.PIP, 1)
        assertFalse(t.isEmpty)
        t.clear()
        assertTrue(t.isEmpty)
        assertEquals(0, t.defeated[Bestiary.PIP])
    }

    @Test
    fun everythingSurvivesBeingWritten() {
        val l = Ledger()
        l.record(run(700, Difficulty.HARD, stage = 11))
        l.record(run(310, Difficulty.EASY, players = 4, left = true))
        val t = Tally()
        t.defeat(Bestiary.CINDER, 14)
        t.slain(Bestiary.HEXARCH, 5)
        l.merge(t)

        val back = Ledger.read(l.write())
        assertNotNull(back)
        assertEquals(2, back!!.runs.size)
        val newest = back.runs[0]
        assertEquals(310, newest.score)
        assertTrue(newest.left)
        assertEquals(4, newest.players)
        assertEquals(Difficulty.EASY, newest.difficulty)
        val older = back.runs[1]
        assertEquals(700, older.score)
        assertEquals(Cause.shot(Bestiary.TICK), older.killedBy)
        assertEquals(1_000, older.damage)
        assertEquals(2, back.totals.runs)
        assertEquals(11, back.totals.deepest)
        assertEquals(700, back.bestOn(Difficulty.HARD)!!.score)
        assertEquals(11, back.bestOn(Difficulty.HARD)!!.stage)
        assertNull(back.bestOn(Difficulty.NORMAL))
        assertEquals(1, back.defeated[Bestiary.CINDER])
        assertEquals(14, back.firstStage[Bestiary.CINDER])
        assertEquals(1, back.killedYou[Bestiary.HEXARCH])
        assertTrue(back.seen[Bestiary.HEXARCH])
    }

    @Test
    fun aFullHistorySurvivesBeingWritten() {
        val l = Ledger()
        for (i in 1..Ledger.MAX_RUNS) l.record(run(i))
        assertEquals(Ledger.MAX_RUNS, Ledger.read(l.write())!!.runs.size)
    }

    @Test
    fun aCopyDoesNotFollowTheOriginal() {
        val l = Ledger()
        l.record(run(100))
        val c = l.copy()
        l.record(run(200))
        assertEquals(1, c.runs.size)
        assertEquals(1, c.totals.runs)
    }

    @Test
    fun junkAndCutOffFilesAreRefused() {
        assertNull(Ledger.read(ByteArray(0)))
        assertNull(Ledger.read(byteArrayOf(1, 2, 3, 4)))
        val good = Ledger().also { it.record(run(5)) }.write()
        assertNull(Ledger.read(good.copyOf(good.size - 9)))
        val wrongVersion = good.copyOf().also { it[2] = 99 }
        assertNull(Ledger.read(wrongVersion))
    }
}
