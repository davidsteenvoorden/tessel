package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Balance
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class DirectorTest {
    private val idle = Array(MAX_PLAYERS) { PlayerInput() }

    /** Steps with a player who cannot fall, so spawning never stops. */
    private fun World.run(ticks: Int, each: () -> Unit = {}) {
        repeat(ticks) {
            players.hp[0] = Balance.PLAYER_HP
            step(idle)
            each()
        }
    }

    /** Fights through the current stage and leaves the shop. */
    private fun World.finishStage() {
        while (phase != Phase.SHOP) {
            players.hp[0] = Balance.PLAYER_HP
            step(idle)
        }
        nextStage()
    }

    @Test
    fun theFirstGroupArrivesWithinAFewSeconds() {
        val world = World(seed = 5)
        world.run(TICK_HZ * 6)
        assertTrue(world.enemies.count > 0)
    }

    @Test
    fun newcomersEnterFromOutsideTheArena() {
        val world = World(seed = 9)
        val seen = HashSet<Int>()
        world.run(TICK_HZ * 50) {
            for (i in 0 until world.enemies.count) {
                if (!seen.add(world.enemies.id[i])) continue
                val x = world.enemies.x[i]
                val y = world.enemies.y[i]
                val outside = x < 0f || x > ARENA_W || y < 0f || y > ARENA_H
                assertTrue("enemy ${world.enemies.id[i]} appeared inside at $x,$y", outside)
            }
        }
        assertTrue(seen.size > 12)
    }

    @Test
    fun newcomersKeepTheirDistance() {
        val world = World(seed = 21)
        val seen = HashSet<Int>()
        var tooClose = 0
        world.run(TICK_HZ * 50) {
            for (i in 0 until world.enemies.count) {
                if (!seen.add(world.enemies.id[i])) continue
                val dx = world.enemies.x[i] - world.players.x[0]
                val dy = world.enemies.y[i] - world.players.y[0]
                // Pack members fan out up to 70 units around the chosen point.
                if (sqrt(dx * dx + dy * dy) < 300f) tooClose++
            }
        }
        assertTrue(seen.size > 12)
        assertEquals(0, tooClose)
    }

    @Test
    fun onlyDotsEarlyInTheFirstStage() {
        val world = World(seed = 33)
        world.run(TICK_HZ * 19) {
            for (i in 0 until world.enemies.count) assertEquals(Bestiary.DOT, world.enemies.type[i])
        }
    }

    @Test
    fun slabsWaitForTheSecondStage() {
        val world = World(seed = 34)
        val types = HashSet<Int>()
        world.run(STAGE_TICKS) { for (i in 0 until world.enemies.count) types += world.enemies.type[i] }
        assertEquals(setOf(Bestiary.DOT, Bestiary.PIP), types)

        world.finishStage()
        world.run(STAGE_TICKS - 10) { for (i in 0 until world.enemies.count) types += world.enemies.type[i] }
        assertTrue(Bestiary.SLAB in types)
        assertTrue(types.all { Bestiary.of(it).from <= 2 })
    }

    @Test
    fun newKindsJoinStageByStage() {
        val d = Director()
        for (type in 0 until Bestiary.size) {
            val def = Bestiary.of(type)
            if (def.boss || def.from == 0) {
                assertEquals("${def.name} is never sent on its own", 0f, d.weight(type, 30, 30f), 0f)
                continue
            }
            if (def.from > 1) assertEquals("${def.name} too early", 0f, d.weight(type, def.from - 1, 30f), 0f)
            assertTrue("${def.name} on time", d.weight(type, def.from, 30f) > 0f)
            // Its first stage sends it more often than later ones, so it gets noticed.
            if (def.from > 1) assertTrue(d.weight(type, def.from, 30f) > d.weight(type, def.from + 1, 30f))
        }
    }

    @Test
    fun laterStagesSendMore() {
        // Measured in threat, since later stages send fewer but tougher bodies.
        fun arrivals(stages: Int): Float {
            val world = World(seed = 50)
            repeat(stages - 1) { world.finishStage() }
            val seen = HashMap<Int, Int>()
            world.run(STAGE_TICKS - 10) { for (i in 0 until world.enemies.count) seen[world.enemies.id[i]] = world.enemies.type[i] }
            return seen.values.sumOf { Bestiary.of(it).cost.toDouble() }.toFloat()
        }
        val first = arrivals(1)
        val fourth = arrivals(4)
        assertTrue("stage 1 sent $first, stage 4 sent $fourth", fourth > first * 2)
    }

    @Test
    fun theRateClimbsWithinAndAcrossStages() {
        val d = Director()
        assertTrue(d.ratePerSecond(1, 1f) > d.ratePerSecond(1, 0f))
        assertTrue(d.ratePerSecond(2, 0f) > d.ratePerSecond(1, 0f))
        assertTrue(d.ratePerSecond(1, 0f) < 1f)
    }

    @Test
    fun hardSendsMoreThanEasy() {
        fun arrivals(difficulty: Difficulty): Int {
            val world = World(seed = 61)
            world.reset(seed = 61, difficulty = difficulty)
            val seen = HashSet<Int>()
            world.run(STAGE_TICKS - 10) { for (i in 0 until world.enemies.count) seen += world.enemies.id[i] }
            return seen.size
        }
        assertTrue(arrivals(Difficulty.HARD) > arrivals(Difficulty.EASY))
    }

    @Test
    fun aSeedReplaysTheSameRun() {
        val a = World(seed = 77)
        val b = World(seed = 77)
        repeat(TICK_HZ * 20) {
            a.step(idle)
            b.step(idle)
        }
        assertEquals(a.enemies.count, b.enemies.count)
        for (i in 0 until a.enemies.count) {
            assertEquals(a.enemies.x[i], b.enemies.x[i], 0f)
            assertEquals(a.enemies.type[i], b.enemies.type[i])
        }
    }
}
