package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Balance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviveTest {
    private val inputs = Array(MAX_PLAYERS) { PlayerInput() }
    private val world = World(seed = 2).apply {
        spawning = false
        reset(players = 2, seed = 2)
    }

    @Test
    fun oneDownIsNotTheEndOfTheRun() {
        world.players.hp[1] = 0
        world.step(inputs)
        assertFalse(world.over)
    }

    @Test
    fun bothDownEndsIt() {
        world.players.hp[0] = 0
        world.players.hp[1] = 0
        world.step(inputs)
        assertTrue(world.over)
    }

    @Test
    fun aDownedPlayerCrawls() {
        world.players.hp[1] = 0
        val start = world.players.x[1]
        inputs[1].moveX = 1f
        repeat(TICK_HZ) { world.step(inputs) }
        assertEquals(start + Balance.PLAYER_SPEED * Balance.DOWNED_SPEED, world.players.x[1], 0.5f)
    }

    @Test
    fun aDownedPlayerCannotShoot() {
        world.players.hp[1] = 0
        inputs[1].aimX = 1f
        inputs[1].fire = true
        repeat(30) { world.step(inputs) }
        for (i in 0 until world.bullets.count) assertTrue(world.bullets.owner[i] != 1)
    }

    @Test
    fun standingCloseLongEnoughRevives() {
        world.players.hp[1] = 0
        world.players.x[0] = world.players.x[1] + 30f
        world.players.y[0] = world.players.y[1]
        repeat(REVIVE_TICKS - 1) { world.step(inputs) }
        assertFalse(world.players.alive(1))
        world.step(inputs)
        assertTrue(world.players.alive(1))
        assertTrue(world.events.has(Events.REVIVED))
    }

    @Test
    fun walkingAwayLosesProgress() {
        world.players.hp[1] = 0
        world.players.x[0] = world.players.x[1] + 30f
        repeat(REVIVE_TICKS / 2) { world.step(inputs) }
        val halfway = world.players.revive[1]
        world.players.x[0] = world.players.x[1] + 400f
        repeat(10) { world.step(inputs) }
        assertTrue(world.players.revive[1] < halfway)
    }

    @Test
    fun aRemotePositionIsFollowedButNotTeleportedTo() {
        inputs[1].hasPosition = true
        inputs[1].posX = world.players.x[1] + 2f
        inputs[1].posY = world.players.y[1]
        world.step(inputs)
        assertEquals(inputs[1].posX, world.players.x[1], 0.001f)

        inputs[1].posX = world.players.x[1] + 500f
        val before = world.players.x[1]
        world.step(inputs)
        val maxStep = Balance.PLAYER_SPEED * TICK_SECONDS * Balance.REMOTE_SLACK
        assertEquals(before + maxStep, world.players.x[1], 0.01f)
    }
}
