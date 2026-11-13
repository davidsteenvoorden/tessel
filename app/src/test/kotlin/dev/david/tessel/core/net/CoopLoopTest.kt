package dev.david.tessel.core.net

import dev.david.tessel.core.content.Balance
import dev.david.tessel.core.content.Upgrades
import dev.david.tessel.core.sim.Bot
import dev.david.tessel.core.sim.Events
import dev.david.tessel.core.sim.MAX_PLAYERS
import dev.david.tessel.core.sim.Phase
import dev.david.tessel.core.sim.PlayerInput
import dev.david.tessel.core.sim.TICK_HZ
import dev.david.tessel.core.sim.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * A host and the phones that joined it, wired together in memory with a
 * fixed delay and stepped on one fake clock. Checks what each phone would
 * see of the others. Most tests use one joining phone; a few use three.
 */
class CoopLoopTest {
    private val stepNanos = 1_000_000_000L / TICK_HZ
    private var delayNanos = 40_000_000L
    private var now = 0L

    /** [to] is the player number of the receiving phone; the host is 0. */
    private class Letter(val at: Long, val bytes: ByteArray, val from: Int, val to: Int)

    private val post = ArrayList<Letter>()
    private var players = 2
    private val world = World(seed = 21)
    private lateinit var host: HostSession
    private var clients = emptyList<ClientSession>()
    private val hostInputs = Array(MAX_PLAYERS) { PlayerInput() }
    private val sticks = Array(MAX_PLAYERS) { PlayerInput() }
    private val bot = Bot()
    private var paused = false

    /** The first phone that joined, player 1. */
    private val client get() = clients[0]
    private val clientStick get() = sticks[CLIENT]

    init {
        setUp(2)
    }

    private fun setUp(count: Int) {
        players = count
        world.reset(players = count, seed = 21)
        for (p in 1 until count) world.remote[p] = true
        host = HostSession(world) { to, bytes, _ -> post += Letter(now + delayNanos, bytes, HOST_PLAYER, to); true }
        for (p in 1 until count) host.connect(p)
        host.onRequest = { kind ->
            when (kind) {
                Wire.PAUSE -> paused = true
                Wire.RESUME -> paused = false
            }
        }
        clients = (1 until count).map { me ->
            ClientSession(me, count) { bytes, _ -> post += Letter(now + delayNanos, bytes, me, HOST_PLAYER); true }.also { it.reset() }
        }
    }

    private fun deliver() {
        val due = post.filter { it.at <= now }
        post.removeAll(due.toSet())
        for (letter in due) {
            if (letter.to == HOST_PLAYER) host.receive(letter.from, letter.bytes, now) else clients[letter.to - 1].receive(letter.bytes, now)
        }
    }

    /** One 60 Hz tick on every phone. */
    private fun tick(hostDrivesBot: Boolean = true) {
        now += stepNanos
        deliver()
        if (!paused && world.phase != Phase.SHOP && !world.over) {
            if (hostDrivesBot) bot.drive(world, HOST_PLAYER, hostInputs[HOST_PLAYER]) else hostInputs[HOST_PLAYER].clear()
            for (p in 1 until players) hostInputs[p].copyFrom(host.remoteInput(p, now))
            world.step(hostInputs)
        }
        if (world.phase == Phase.SHOP && host.allReady()) {
            host.clearReady()
            world.nextStage()
        }
        host.collect(world.events)
        world.events.clear()
        host.pump(now, paused, false)
        for (c in clients) {
            val s = c.latest
            val playing = s != null && !s.paused && s.phase != Phase.SHOP && s.phase != Phase.OVER
            if (playing) c.tick(sticks[c.me], now)
            c.frame(now)
        }
    }

    private fun run(seconds: Float, hostDrivesBot: Boolean = true) = repeat((seconds * TICK_HZ).toInt()) { tick(hostDrivesBot) }

    @Test
    fun theClientSeesTheHostsEnemiesALittleLate() {
        run(8f)
        val view = client.view
        assertTrue(world.enemies.count > 0)
        assertTrue(view.enemies.count > 0)
        // Every drawn enemy exists on the host and sits near where it really is.
        var matched = 0
        for (i in 0 until view.enemies.count) {
            for (j in 0 until world.enemies.count) {
                if (world.enemies.id[j] != view.enemies.id[i]) continue
                matched++
                val dx = abs(world.enemies.x[j] - view.enemies.x[i])
                val dy = abs(world.enemies.y[j] - view.enemies.y[i])
                assertTrue("enemy drawn ${dx}x$dy from the truth", dx < 40f && dy < 40f)
            }
        }
        assertTrue(matched >= view.enemies.count - 3)
    }

    @Test
    fun theClientMovesAtOnceAndTheHostFollows() {
        run(1f)
        val startX = client.view.players.x[CLIENT]
        clientStick.moveX = 1f
        tick()
        assertTrue("client moved right away", client.view.players.x[CLIENT] > startX)
        run(1f)
        clientStick.moveX = 0f
        run(0.5f)
        assertEquals(client.view.players.x[CLIENT], world.players.x[CLIENT], 1f)
        assertTrue(world.players.x[CLIENT] > startX + Balance.PLAYER_SPEED * 0.8f)
    }

    @Test
    fun theClientsShotsCountWhereItsScreenSawThemLand() {
        world.spawning = false
        run(0.5f)
        val px = world.players.x[CLIENT]
        val py = world.players.y[CLIENT]
        val slab = world.spawnEnemy(dev.david.tessel.core.content.Bestiary.SLAB, px, py - 200f)
        val id = world.enemies.id[slab]
        run(0.3f, hostDrivesBot = false)
        clientStick.aimX = 0f
        clientStick.aimY = -1f
        clientStick.fire = true
        run(3f, hostDrivesBot = false)
        var alive = false
        for (i in 0 until world.enemies.count) if (world.enemies.id[i] == id) alive = true
        assertTrue("the slab should have broken", !alive)
        assertEquals(1, world.players.kills[CLIENT])
        // The host draws the client's shots too, but they do no damage there.
        var ghosts = 0
        for (i in 0 until world.bullets.count) if (world.bullets.owner[i] == CLIENT) {
            ghosts++
            assertEquals(0f, world.bullets.damage[i], 0f)
        }
        assertTrue(ghosts > 0)
    }

    @Test
    fun anEnemyShotTheClientSeesHurtsItOnce() {
        world.spawning = false
        run(0.5f)
        val before = world.players.hp[CLIENT]
        world.spawnHostile(world.players.x[CLIENT] - 150f, world.players.y[CLIENT], 0f, 200f)
        run(1.5f, hostDrivesBot = false)
        assertEquals(before - 1, world.players.hp[CLIENT])
        // Used up: gone on the host and on the client.
        assertEquals(0, world.hostile.count)
        assertEquals(0, client.view.hostile.count)
    }

    @Test
    fun theHostLeavesAJoinedPlayersDamageToTheirPhone() {
        world.spawning = false
        run(0.5f)
        // The host sees a shot pass right through the client, but the client's phone never saw it.
        val before = world.players.hp[CLIENT]
        world.hostile.add(9, world.players.x[CLIENT] - 30f, world.players.y[CLIENT], 300f, 0f, 20, 1f, 5f, 0, -1)
        repeat(10) { world.step(hostInputs) }
        assertEquals(before, world.players.hp[CLIENT])
    }

    @Test
    fun everyoneElsesShotsAreDrawnOnEachPhone() {
        setUp(3)
        world.spawning = false
        sticks[2].aimX = 1f
        sticks[2].fire = true
        run(1f)
        var fromTwo = 0
        for (i in 0 until clients[0].view.bullets.count) if (clients[0].view.bullets.owner[i] == 2) fromTwo++
        assertTrue("player 1 sees player 2 shooting", fromTwo > 0)
    }

    @Test
    fun pausingFromTheClientPausesTheHost() {
        run(1f)
        client.action(Wire.PAUSE)
        run(0.3f)
        assertTrue(paused)
        val frozen = world.tick
        run(0.5f)
        assertEquals(frozen, world.tick)
        assertTrue(client.latest!!.paused)
        client.action(Wire.RESUME)
        run(0.3f)
        assertTrue(world.tick > frozen)
    }

    @Test
    fun bothPlayersShopForThemselvesAndTheStageWaitsForBoth() {
        world.spawning = false
        while (world.phase != Phase.SHOP) tick(hostDrivesBot = false)
        world.players.coins[CLIENT] = 500
        run(0.3f)
        val s = client.latest!!
        assertTrue(s.hasShop)
        val upgrade = s.shopItem[0]
        val before = world.players.levels[CLIENT][upgrade]
        client.action(Wire.BUY, 0)
        run(0.3f)
        assertEquals(before + 1, world.players.levels[CLIENT][upgrade])
        assertEquals(500 - Upgrades.price(upgrade, before, world.stage), world.players.coins[CLIENT])
        // The same slot can be bought again, at the next level's price.
        assertEquals(Upgrades.price(upgrade, before + 1, world.stage), client.latest!!.shopPrice[0])
        assertEquals(before + 1, client.latest!!.shopLevel[0])

        client.action(Wire.READY)
        run(0.3f)
        assertEquals(Phase.SHOP, world.phase)
        host.markReady(HOST_PLAYER)
        run(0.3f)
        assertEquals(2, world.stage)
        assertEquals(Phase.FIGHT, world.phase)
    }

    @Test
    fun killsReachTheClientAsShatterEvents() {
        var kills = 0
        repeat(TICK_HZ * 10) {
            tick()
            for (i in 0 until client.view.events.count) if (client.view.events.kind[i] == Events.ENEMY_KILLED) kills++
            client.view.events.clear()
        }
        assertTrue(world.players.kills[HOST_PLAYER] > 0)
        assertTrue(kills > 0)
    }

    @Test
    fun aDownedPlayerIsRevivedByStandingClose() {
        world.spawning = false
        run(0.5f)
        world.players.hp[HOST_PLAYER] = 0
        world.players.x[HOST_PLAYER] = 400f
        world.players.y[HOST_PLAYER] = 360f
        // The client walks over and waits.
        while (abs(client.view.players.x[CLIENT] - 400f) > 30f) {
            clientStick.moveX = if (client.view.players.x[CLIENT] > 400f) -1f else 1f
            tick(hostDrivesBot = false)
        }
        clientStick.moveX = 0f
        run(Balance.REVIVE_SECONDS + 0.5f, hostDrivesBot = false)
        assertTrue(world.players.alive(HOST_PLAYER))
        assertEquals(world.players.maxHp[HOST_PLAYER] / 2, world.players.hp[HOST_PLAYER])
    }

    @Test
    fun aQuietClientStopsMovingOnTheHost() {
        world.spawning = false
        clientStick.moveX = 1f
        run(0.5f)
        val stale = host.remoteInput(CLIENT, now + HostSession.STALE_NANOS * 2)
        assertEquals(0f, stale.moveX, 0f)
        assertTrue(!stale.hasPosition)
    }

    @Test
    fun fourPhonesEachSeeTheGameAndMoveThemselves() {
        setUp(4)
        sticks[2].moveY = 1f
        sticks[3].moveX = -1f
        run(3f)
        for (c in clients) {
            assertTrue("player ${c.me} sees enemies", c.view.enemies.count > 0)
            assertEquals(4, c.view.playerCount)
        }
        assertTrue(world.players.y[2] > world.players.y[1] + 100f)
        assertTrue(world.players.x[3] < world.players.x[1] - 100f)
        // Once they stand still, each phone draws itself where the host has it.
        sticks.forEach { it.clear() }
        run(0.5f)
        for (c in clients) assertEquals(world.players.x[c.me], c.view.players.x[c.me], 1f)
    }

    @Test
    fun theStageWaitsForEveryoneButNotForSomeoneWhoLeft() {
        setUp(4)
        world.spawning = false
        while (world.phase != Phase.SHOP) tick(hostDrivesBot = false)
        host.markReady(HOST_PLAYER)
        clients[0].action(Wire.READY)
        clients[1].action(Wire.READY)
        run(0.3f)
        assertEquals(Phase.SHOP, world.phase)
        // Player 3's phone is gone for good: the others go on without them.
        host.disconnect(3)
        world.removePlayer(3)
        run(0.3f)
        assertEquals(Phase.FIGHT, world.phase)
        assertEquals(2, world.stage)
        assertTrue(!world.players.present[3])
    }

    @Test
    fun bothSidesMeasureTheRoundTrip() {
        run(2f)
        // 40 ms each way, give or take a frame for when things are read.
        assertEquals(80f, client.roundTripMillis.toFloat(), 20f)
        assertEquals(80f, host.roundTripMillis(CLIENT).toFloat(), 20f)
    }

    @Test
    fun aKillShowsOnTheJoiningPhoneBeforeTheHostHasHeard() {
        world.spawning = false
        run(0.5f)
        val dot = world.spawnEnemy(dev.david.tessel.core.content.Bestiary.DOT, world.players.x[CLIENT], world.players.y[CLIENT] - 150f)
        val id16 = world.enemies.id[dot] and 0xFFFF
        run(0.4f, hostDrivesBot = false)
        clientStick.aimY = -1f
        clientStick.fire = true
        var shownAt = -1L
        var hostAt = -1L
        var shown = 0
        repeat(TICK_HZ * 2) {
            tick(hostDrivesBot = false)
            val ev = client.view.events
            for (i in 0 until ev.count) if (ev.kind[i] == Events.ENEMY_KILLED && ev.ref[i] == id16) {
                shown++
                if (shownAt < 0) shownAt = now
            }
            ev.clear()
            var alive = false
            for (i in 0 until world.enemies.count) if (world.enemies.id[i] and 0xFFFF == id16) alive = true
            if (!alive && hostAt < 0) hostAt = now
        }
        assertTrue("the phone showed the kill", shownAt > 0)
        assertTrue("the host agreed", hostAt > 0)
        assertTrue("shown first", shownAt < hostAt)
        assertEquals("shown once, not again when the host agreed", 1, shown)
    }

    @Test
    fun aSlowLinkNeverPullsThePlayerBack() {
        delayNanos = 300_000_000L
        setUp(2)
        world.spawning = false
        run(1f)
        clientStick.moveX = 1f
        var last = client.view.players.x[CLIENT]
        repeat(TICK_HZ * 3) {
            tick(hostDrivesBot = false)
            val x = client.view.players.x[CLIENT]
            assertTrue("pulled back from $last to $x", x >= last - 0.01f)
            last = x
        }
        assertTrue(last > 700f)
    }

    private companion object {
        const val CLIENT = 1
    }
}
