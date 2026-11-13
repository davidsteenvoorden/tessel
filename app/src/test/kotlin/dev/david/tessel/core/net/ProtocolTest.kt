package dev.david.tessel.core.net

import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.sim.Events
import dev.david.tessel.core.sim.MAX_PLAYERS
import dev.david.tessel.core.sim.Phase
import dev.david.tessel.core.sim.PlayerInput
import dev.david.tessel.core.sim.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    private val inputs = Array(MAX_PLAYERS) { PlayerInput() }

    private fun busyWorld(): World {
        val w = World(seed = 3)
        w.reset(players = 2, seed = 3, difficulty = Difficulty.HARD)
        repeat(40) { w.spawnEnemy(if (it % 3 == 0) Bestiary.SLAB else Bestiary.DOT, 100f + it * 30f, 50f + it * 10f) }
        inputs[0].aimX = 1f
        inputs[0].fire = true
        inputs[1].aimX = -1f
        inputs[1].fire = true
        repeat(30) { w.step(inputs) }
        return w
    }

    private fun roundTrip(w: World, outbox: Events = Events(), paused: Boolean = false, ready: Int = 0): Snapshot {
        val buf = Wire.buffer(MAX_MESSAGE * 2)
        Protocol.writeSnapshot(buf, w, paused, false, ready, outbox, Events(), Events(), CLIENT, Echo())
        val bytes = Wire.finish(buf)
        assertEquals(Wire.SNAPSHOT, Wire.typeOf(bytes))
        val s = Snapshot()
        Protocol.readSnapshot(Wire.open(bytes), s)
        return s
    }

    @Test
    fun theWorldSurvivesTheTripWithinQuantization() {
        val w = busyWorld()
        val s = roundTrip(w, paused = true, ready = 2)
        assertEquals(w.tick, s.tick)
        assertEquals(w.stage, s.stage)
        assertEquals(w.phase, s.phase)
        assertEquals(Difficulty.HARD, s.difficulty)
        assertTrue(s.paused)
        assertEquals(2, s.ready)
        assertEquals(2, s.playerCount)
        assertEquals(0b11, s.present)
        for (p in 0 until w.playerCount) {
            assertEquals(w.players.x[p], s.px[p], 0.13f)
            assertEquals(w.players.hp[p], s.hp[p])
            assertEquals(w.players.moveSpeed(p), s.moveSpeed[p], 0f)
            assertEquals(w.players.volley(p), s.volley[p])
        }
        assertEquals(w.enemies.count, s.enemyCount)
        for (i in 0 until w.enemies.count) {
            assertEquals(w.enemies.id[i] and 0xFFFF, s.enemyId[i])
            assertEquals(w.enemies.type[i], s.enemyType[i])
            assertEquals(w.enemies.x[i], s.enemyX[i], 0.13f)
            assertEquals(w.enemies.y[i], s.enemyY[i], 0.13f)
            assertEquals(i, s.enemyIndex[w.enemies.id[i] and 0xFFFF])
        }
        assertEquals(w.coins.count, s.coinCount)
    }

    @Test
    fun enemyShotsTravelOnceWhenFiredAndOnceWhenUsedUp() {
        val w = busyWorld()
        val born = Events()
        born.add(Events.HOSTILE_SPAWN, 70_123, 300.5f, 200f, 155.25f, -80.5f, (w.tick - 3).toFloat())
        val gone = Events()
        gone.add(Events.HOSTILE_GONE, 70_100)
        val buf = Wire.buffer(MAX_MESSAGE * 2)
        Protocol.writeSnapshot(buf, w, false, false, 0, Events(), born, gone, CLIENT, Echo())
        val s = Snapshot()
        Protocol.readSnapshot(Wire.open(Wire.finish(buf)), s)
        assertEquals(1, s.bornCount)
        assertEquals(70_123 and 0xFFFF, s.bornId[0])
        assertEquals(300.5f, s.bornX[0], 0.13f)
        assertEquals(155.25f, s.bornVx[0], 0.13f)
        assertEquals(-80.5f, s.bornVy[0], 0.13f)
        assertEquals(w.tick - 3, s.bornTick[0])
        assertEquals(1, s.goneCount)
        assertEquals(70_100 and 0xFFFF, s.goneId[0])
    }

    @Test
    fun eventsRideAlong() {
        val w = busyWorld()
        val outbox = Events()
        outbox.add(Events.ENEMY_KILLED, Bestiary.SLAB, 812.5f, 300f, 0.5f, -0.5f, 1f)
        outbox.add(Events.RUN_OVER, 0)
        val s = roundTrip(w, outbox)
        assertEquals(2, s.events.count)
        assertEquals(Events.ENEMY_KILLED, s.events.kind[0])
        assertEquals(Bestiary.SLAB, s.events.who[0])
        assertEquals(812.5f, s.events.x[0], 0.13f)
        assertEquals(0.5f, s.events.dx[0], 0.01f)
        assertEquals(Events.RUN_OVER, s.events.kind[1])
    }

    @Test
    fun theShopTravelsOnlyWhileShopping() {
        val w = World(seed = 9).apply { spawning = false }
        w.reset(players = 2, seed = 9)
        assertFalse(roundTrip(w).hasShop)
        while (w.phase != Phase.SHOP) w.step(inputs.also { it[0].fire = false; it[1].fire = false })
        w.players.coins[CLIENT] = 77
        val s = roundTrip(w)
        assertTrue(s.hasShop)
        assertEquals(77, s.coins[CLIENT])
        for (slot in 0 until 5) {
            assertEquals(w.shop.item(CLIENT, slot), s.shopItem[slot])
            assertEquals(w.shop.price(w, CLIENT, slot), s.shopPrice[slot])
        }
        assertEquals(w.shop.rerollPrice(w, CLIENT), s.rerollPrice)
    }

    @Test
    fun aFullArenaForEightStillFitsInOneMessage() {
        val w = World(seed = 4)
        w.reset(players = MAX_PLAYERS, seed = 4)
        // Every pool filled to the brim, every player in the snapshot.
        while (w.enemies.add(w.enemies.count + 1, Bestiary.SLAB, 400f, 300f, 5f) >= 0) Unit
        while (w.bullets.add(1, 10f, 10f, 700f, 0f, 30, 1f, 4f, 0, 0) >= 0) Unit
        while (w.hostile.add(1, 10f, 10f, 200f, 0f, 30, 1f, 5f, 0, -1) >= 0) Unit
        while (w.coins.add(w.coins.count + 1, 10f, 10f, 0f, 0f, 1) >= 0) Unit
        val outbox = Events(2048)
        repeat(2048) { outbox.add(Events.ENEMY_KILLED, Bestiary.DOT, 1f, 1f) }
        val born = Events(SNAPSHOT_SHOTS)
        repeat(SNAPSHOT_SHOTS) { born.add(Events.HOSTILE_SPAWN, it, 1f, 1f, 100f, 100f, 0f) }
        val gone = Events(SNAPSHOT_SHOTS)
        repeat(SNAPSHOT_SHOTS) { gone.add(Events.HOSTILE_GONE, it) }
        val buf = Wire.buffer(MAX_MESSAGE * 2)
        Protocol.writeSnapshot(buf, w, false, false, 0, outbox, born, gone, CLIENT, Echo())
        assertTrue("worst case is ${buf.position()} bytes", buf.position() <= MAX_MESSAGE)
    }

    @Test
    fun enemyShotsAndTheBossTravel() {
        val w = World(seed = 5).apply { spawning = false }
        w.reset(players = 2, seed = 5)
        val boss = w.spawnEnemy(Bestiary.HEXARCH, 800f, 300f)
        w.enemies.hp[boss] = w.enemies.maxHp[boss] / 2
        inputs.forEach { it.clear() }
        w.step(inputs)
        val s = roundTrip(w)
        assertEquals(Bestiary.HEXARCH, s.bossType)
        assertEquals(w.bossHealth(), s.bossHealth, 0.01f)
    }

    @Test
    fun startTellsEachPhoneWhoItIs() {
        val start = Wire.readStart(Wire.start(Difficulty.HARD.ordinal, 3, 5))
        assertEquals(Difficulty.HARD.ordinal, start.difficulty)
        assertEquals(3, start.you)
        assertEquals(5, start.players)
        assertEquals(listOf("A71", "Pixel", "Tab"), Wire.readRoster(Wire.roster(listOf("A71", "Pixel", "Tab"))))
    }

    @Test
    fun inputCarriesStickTriggerAndPosition() {
        val buf = Wire.buffer(1024)
        val sent = PlayerInput().apply {
            moveX = 0.5f
            moveY = -1f
            aimX = 0.6f
            aimY = 0.8f
            fire = true
        }
        val told = Reports().apply {
            hit(65_000, 0.6f, -0.8f)
            hurt(1234)
            hurt(-1)
            pickup(40_001)
        }
        val timing = Echo().apply {
            seq = 812
            stamp = 123_456
            echo = 123_000
            held = 7
        }
        Protocol.writeInput(buf, sent, 345.5f, 678.25f, told, timing)
        val got = PlayerInput()
        val heard = Reports()
        val back = Echo()
        Protocol.readInput(Wire.open(Wire.finish(buf)), got, heard, back)
        assertEquals(812, back.seq)
        assertEquals(123_456, back.stamp)
        assertEquals(123_000, back.echo)
        assertEquals(7, back.held)
        assertEquals(1, heard.pickups)
        assertEquals(40_001, heard.pickupId[0])
        assertEquals(1, heard.hits)
        assertEquals(65_000, heard.hitId[0])
        assertEquals(-0.8f, heard.hitY[0], 0.01f)
        assertEquals(2, heard.hurts)
        assertEquals(1234, heard.hurtBy[0])
        assertEquals(-1, heard.hurtBy[1])
        assertEquals(0.5f, got.moveX, 0.01f)
        assertEquals(-1f, got.moveY, 0.01f)
        assertEquals(0.8f, got.aimY, 0.01f)
        assertTrue(got.fire)
        assertTrue(got.hasPosition)
        assertEquals(345.5f, got.posX, 0.13f)
        assertEquals(678.25f, got.posY, 0.13f)
    }

    @Test
    fun strangersAndOtherVersionsAreRecognised() {
        assertEquals(-1, Wire.typeOf(byteArrayOf(1, 2, 3, 4)))
        val other = Wire.hello("x").also { it[2] = (Wire.VERSION + 1).toByte() }
        assertEquals(Wire.VERSION_MISMATCH, Wire.typeOf(other))
        assertEquals("Galaxy A71", Wire.getString(Wire.open(Wire.hello("Galaxy A71"))))
    }

    @Test
    fun idMapFindsWhatItHolds() {
        val map = IdMap(64)
        for (k in 0 until 40) map.put(k * 977, k)
        for (k in 0 until 40) assertEquals(k, map[k * 977])
        assertEquals(-1, map[5])
        map.clear()
        assertEquals(-1, map[977])
    }

    @Test
    fun enemyHealthAndKillIdsTravelSoAPhoneCanCallItsOwnKills() {
        val w = busyWorld()
        val boss = w.spawnEnemy(Bestiary.HEXARCH, 800f, 300f)
        val outbox = Events()
        outbox.add(Events.ENEMY_KILLED, Bestiary.DOT, 10f, 10f, 0f, 0f, 0f, 70_123)
        val s = roundTrip(w, outbox)
        for (i in 0 until w.enemies.count) {
            val expected = if (i == boss) Float.POSITIVE_INFINITY else w.enemies.hp[i]
            if (expected.isInfinite()) assertTrue(s.enemyHp[i].isInfinite()) else assertEquals(expected, s.enemyHp[i], 0.07f)
        }
        assertEquals(70_123 and 0xFFFF, s.events.ref[0])
        assertEquals(w.players.damage(CLIENT), s.damage[CLIENT], 0f)
    }

    @Test
    fun theRoundTripLeavesOutTimeSpentWaiting() {
        // Sent at 1000, the other side held it 30 ms, the reply arrives at 1110: 80 ms on the wire.
        val e = Echo().apply {
            echo = 1000
            held = 30
        }
        assertEquals(80, e.roundTrip(1110))
        assertEquals(-1, Echo().roundTrip(5000))
    }

    private companion object {
        const val CLIENT = 1
    }
}
