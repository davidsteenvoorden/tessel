package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Balance
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnemyTest {
    private val idle = Array(MAX_PLAYERS) { PlayerInput() }
    private val world = World(seed = 12).apply { spawning = false }

    private fun steps(n: Int) = repeat(n) { world.step(idle) }

    private fun place(type: Int, x: Float, y: Float) = world.spawnEnemy(type, x, y)

    private fun indexOf(type: Int): Int {
        for (i in 0 until world.enemies.count) if (world.enemies.type[i] == type) return i
        return -1
    }

    @Test
    fun aShooterWarnsThenFires() {
        place(Bestiary.TICK, 500f, 360f)
        var warned = false
        var fired = 0
        repeat(TICK_HZ * 6) {
            world.players.hp[0] = Balance.PLAYER_HP
            world.step(idle)
            val i = indexOf(Bestiary.TICK)
            if (i >= 0 && world.enemies.cue[i] > 0.5f) warned = true
            if (world.hostile.count > fired) {
                assertTrue("a shot came without its warning", warned)
                fired = world.hostile.count
            }
        }
        assertTrue(fired > 0)
    }

    @Test
    fun aHostileShotCostsOneHealthAndIsUsedUp() {
        val px = world.players.x[0]
        val py = world.players.y[0]
        world.hostile.add(1, px - 60f, py, 240f, 0f, 120, 1f, Balance.HOSTILE_RADIUS, 0, -1)
        steps(30)
        assertEquals(START_HP - 1, world.players.hp[0])
        assertEquals(0, world.hostile.count)
    }

    @Test
    fun shotsPassAPlayerWhoWasJustHit() {
        val px = world.players.x[0]
        val py = world.players.y[0]
        world.players.invuln[0] = 60
        world.hostile.add(1, px - 30f, py, 240f, 0f, 120, 1f, Balance.HOSTILE_RADIUS, 0, -1)
        steps(20)
        assertEquals(START_HP, world.players.hp[0])
    }

    @Test
    fun aHalverBreaksIntoTwoHalflings() {
        val i = place(Bestiary.HALVER, 500f, 360f)
        world.enemies.hp[i] = 0.5f
        shootAt(500f, 360f)
        var halflings = 0
        for (k in 0 until world.enemies.count) if (world.enemies.type[k] == Bestiary.HALFLING) halflings++
        assertEquals(2, halflings)
        assertEquals(-1, indexOf(Bestiary.HALVER))
    }

    @Test
    fun aCinderBurstsIntoARingOfShots() {
        val i = place(Bestiary.CINDER, 500f, 360f)
        world.enemies.hp[i] = 0.5f
        shootAt(500f, 360f)
        assertTrue(world.hostile.count >= Bestiary.of(Bestiary.CINDER).deathRing - 1)
    }

    @Test
    fun aBroodLetsOutPipsUntilItRunsOut() {
        place(Bestiary.BROOD, 400f, 360f)
        repeat(TICK_HZ * 30) {
            world.players.hp[0] = Balance.PLAYER_HP
            world.step(idle)
        }
        var pips = 0
        for (k in 0 until world.enemies.count) if (world.enemies.type[k] == Bestiary.PIP) pips++
        assertEquals(Bestiary.of(Bestiary.BROOD).children, pips)
    }

    @Test
    fun aDasherAimsALineThenCharges() {
        val i = place(Bestiary.DASHER, 560f, 360f)
        var lined = false
        var fastest = 0f
        repeat(TICK_HZ * 4) {
            world.players.hp[0] = Balance.PLAYER_HP
            val k = indexOf(Bestiary.DASHER)
            val x0 = world.enemies.x[k]
            world.step(idle)
            if (world.enemies.cue[k] > 0f) lined = true
            fastest = maxOf(fastest, kotlin.math.abs(world.enemies.x[k] - x0) * TICK_HZ)
        }
        assertTrue(i >= 0)
        assertTrue(lined)
        assertTrue("charged at $fastest", fastest > Bestiary.of(Bestiary.DASHER).speed * 2f)
    }

    @Test
    fun bouncersStayInsideOnceIn() {
        place(Bestiary.DRIFTER, -20f, 300f)
        repeat(TICK_HZ * 20) {
            world.players.hp[0] = Balance.PLAYER_HP
            world.players.invuln[0] = 10
            world.step(idle)
        }
        val i = indexOf(Bestiary.DRIFTER)
        val r = Bestiary.of(Bestiary.DRIFTER).radius
        assertTrue(world.enemies.x[i] > -r && world.enemies.x[i] < ARENA_W + r)
        assertTrue(world.enemies.y[i] > -r && world.enemies.y[i] < ARENA_H + r)
    }

    @Test
    fun everyKindHasItsOwnColour() {
        val hues = HashSet<Pair<Int, Int>>()
        for (t in 0 until Bestiary.size) {
            val def = Bestiary.of(t)
            // Hues from 10 to 100 belong to danger and coins.
            assertTrue("${def.name} hue ${def.hue}", def.hue > 100 && def.hue < 360)
            // Children share their parent's hue in a lighter tone, so only adults must differ.
            if (def.from > 0) assertTrue("${def.name} repeats a colour", hues.add(def.hue to def.tone))
        }
    }

    @Test
    fun theTableIsConsistent() {
        for (t in 0 until Bestiary.size) {
            val def = Bestiary.of(t)
            assertTrue(def.radius > 0f && def.hp > 0f && def.mass > 0f)
            if (def.child >= 0) assertTrue(def.child < Bestiary.size)
            if (def.from > 0) assertTrue("${def.name} costs nothing", def.cost > 0f)
            if (def.move == Move.HOVER || def.move == Move.ORBIT || def.move == Move.DASH) assertTrue(def.range > 0f)
            if (def.boss) assertTrue(def.blurb.isNotEmpty())
        }
        for (b in Bestiary.bossOrder) assertTrue(Bestiary.of(b).boss)
    }

    /** Player 0 stands west of the point and fires one volley at it, then the shot is given time to land. */
    private fun shootAt(x: Float, y: Float) {
        world.players.x[0] = x - 120f
        world.players.y[0] = y
        val aim = Array(MAX_PLAYERS) { PlayerInput() }
        aim[0].aimX = 1f
        aim[0].fire = true
        world.step(aim)
        repeat(20) { world.step(idle) }
    }
}

/** What a player starts with on Normal, which these worlds use. */
private val START_HP = Balance.PLAYER_HP + Difficulty.NORMAL.bonusHp
