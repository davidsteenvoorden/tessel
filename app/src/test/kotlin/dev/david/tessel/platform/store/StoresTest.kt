package dev.david.tessel.platform.store

import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.sim.Cause
import dev.david.tessel.core.sim.Checkpoint
import dev.david.tessel.core.stats.RunEntry
import dev.david.tessel.core.stats.Tally
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StoresTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("tessel-stores").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun checkpoint(stage: Int, points: Int = 0) =
        Checkpoint(Difficulty.NORMAL, stage, stage * 3900, 1, true, 0).also { it.points[0] = points }

    private fun entry(score: Int) = RunEntry(
        endedAt = 1L, difficulty = Difficulty.NORMAL, stage = 4, seconds = 200, score = score, kills = 50, coins = 90, bosses = 0,
        players = 1, killedBy = Cause.touch(Bestiary.SLAB), left = false, damage = 400, shots = 300, hitsTaken = 3,
        downed = 0, revives = 0, upgrades = 4,
    )

    @Test
    fun aSavedRunTakesTheLowestFreeSlot() {
        val saves = SoloSaves(dir)
        assertEquals(1, saves.free())
        saves.write(1, checkpoint(2))
        saves.write(2, checkpoint(3))
        assertEquals(3, saves.free())
        saves.delete(1)
        assertEquals(1, saves.free())
    }

    @Test
    fun withEverySlotTakenThereIsNoneFree() {
        val saves = SoloSaves(dir)
        for (slot in 1..SoloSaves.SLOTS) saves.write(slot, checkpoint(slot))
        assertEquals(-1, saves.free())
        assertEquals(SoloSaves.SLOTS, saves.saves.value.size)
    }

    @Test
    fun theNewestSaveComesFirst() {
        val saves = SoloSaves(dir)
        saves.write(1, checkpoint(2), now = 1_000)
        saves.write(2, checkpoint(3), now = 3_000)
        saves.write(3, checkpoint(4), now = 2_000)
        assertEquals(listOf(2, 3, 1), saves.saves.value.map { it.slot })
        // Writing a slot again moves it to the front: that is how a run keeps itself current.
        saves.write(1, checkpoint(5), now = 9_000)
        assertEquals(1, saves.saves.value.first().slot)
        assertEquals(5, saves.get(1)!!.checkpoint.stage)
    }

    @Test
    fun savesComeBackAfterTheAppRestarts() {
        val first = SoloSaves(dir)
        first.write(2, checkpoint(6, points = 345), now = 5_000)
        first.write(4, checkpoint(9), now = 6_000)
        first.delete(4)
        first.settle()

        val second = SoloSaves(dir)
        assertEquals(listOf(2), second.saves.value.map { it.slot })
        val back = second.get(2)!!
        assertEquals(5_000L, back.savedAt)
        assertEquals(6, back.checkpoint.stage)
        assertEquals(345, back.checkpoint.points[0])
        assertNull(second.get(4))
    }

    @Test
    fun aDamagedSaveIsIgnoredNotFatal() {
        File(dir, "solo-1.save").writeBytes(byteArrayOf(1, 2, 3))
        File(dir, "solo-2.save").writeBytes(ByteArray(40) { 7 })
        val saves = SoloSaves(dir)
        assertTrue(saves.saves.value.isEmpty())
        assertEquals(1, saves.free())
    }

    @Test
    fun theLedgerComesBackAfterTheAppRestarts() {
        val file = File(dir, "ledger.dat")
        val first = LedgerStore(file)
        assertTrue(first.record(entry(900)))
        assertFalse(first.record(entry(100)))
        val tally = Tally()
        tally.defeat(Bestiary.DOT, 1)
        tally.slain(Bestiary.SLAB, 4)
        first.merge(tally)
        assertTrue(tally.isEmpty)
        first.settle()

        val second = LedgerStore(file)
        val l = second.state.value
        assertEquals(2, l.runs.size)
        assertEquals(900, l.bestOn(Difficulty.NORMAL)!!.score)
        assertEquals(1, l.defeated[Bestiary.DOT])
        assertEquals(1, l.killedYou[Bestiary.SLAB])
    }

    @Test
    fun theLedgerFlowFollowsEveryChange() {
        val store = LedgerStore(File(dir, "ledger.dat"))
        assertEquals(0, store.state.value.runs.size)
        store.record(entry(10))
        assertEquals(1, store.state.value.runs.size)
        store.erase()
        assertEquals(0, store.state.value.runs.size)
        assertNull(store.state.value.bestOn(Difficulty.NORMAL))
    }

    @Test
    fun aDamagedLedgerStartsEmpty() {
        val file = File(dir, "ledger.dat")
        file.writeBytes(byteArrayOf(9, 9, 9))
        val store = LedgerStore(file)
        assertNotNull(store.state.value)
        assertEquals(0, store.state.value.runs.size)
    }
}
