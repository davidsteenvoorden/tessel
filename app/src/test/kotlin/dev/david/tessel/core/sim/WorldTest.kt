package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Balance
import org.junit.Assert.assertEquals
import org.junit.Test

class WorldTest {
    private val inputs = Array(MAX_PLAYERS) { PlayerInput() }
    private fun quietWorld() = World(seed = 7).apply { spawning = false }

    @Test
    fun oneSecondOfFullStickCoversPlayerSpeed() {
        val world = quietWorld()
        val startX = world.players.x[0]
        inputs[0].moveX = 1f
        repeat(TICK_HZ) { world.step(inputs) }
        assertEquals(startX + Balance.PLAYER_SPEED, world.players.x[0], 0.01f)
        assertEquals(TICK_HZ, world.tick)
    }

    @Test
    fun oversizedInputIsNoFasterThanFullStick() {
        val world = quietWorld()
        val startY = world.players.y[0]
        inputs[0].moveY = 5f
        world.step(inputs)
        assertEquals(startY + Balance.PLAYER_SPEED * TICK_SECONDS, world.players.y[0], 1e-3f)
    }

    @Test
    fun playerStaysInsideTheArena() {
        val world = quietWorld()
        inputs[0].moveX = -1f
        inputs[0].moveY = -1f
        repeat(TICK_HZ * 20) { world.step(inputs) }
        assertEquals(Balance.PLAYER_RADIUS, world.players.x[0], 0f)
        assertEquals(Balance.PLAYER_RADIUS, world.players.y[0], 0f)
    }

    @Test
    fun previousPositionLagsOneTick() {
        val world = quietWorld()
        inputs[0].moveX = 1f
        world.step(inputs)
        val afterFirst = world.players.x[0]
        world.step(inputs)
        assertEquals(afterFirst, world.players.prevX[0], 0f)
    }

    @Test
    fun aimHoldsWhenTheStickIsIdle() {
        val world = quietWorld()
        inputs[0].aimX = 0f
        inputs[0].aimY = -1f
        world.step(inputs)
        inputs[0].aimY = 0f
        world.step(inputs)
        assertEquals(-1f, world.players.aimY[0], 0f)
    }

    @Test
    fun aDuoStartsSideBySide() {
        val world = quietWorld()
        world.reset(players = 2, seed = 1)
        assertEquals(2, world.playerCount)
        assertEquals(world.players.y[0], world.players.y[1], 0f)
        assertEquals(70f, world.players.x[1] - world.players.x[0], 1e-3f)
    }
}
