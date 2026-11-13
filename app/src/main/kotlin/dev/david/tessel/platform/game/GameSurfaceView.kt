package dev.david.tessel.platform.game

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.content.Upgrades
import dev.david.tessel.core.net.ClientSession
import dev.david.tessel.core.net.HOST_PLAYER
import dev.david.tessel.core.net.HostSession
import dev.david.tessel.core.net.Snapshot
import dev.david.tessel.core.net.Wire
import dev.david.tessel.core.sim.Bot
import dev.david.tessel.core.sim.Events
import dev.david.tessel.core.sim.MAX_PLAYERS
import dev.david.tessel.core.sim.Phase
import dev.david.tessel.core.sim.PlayerInput
import dev.david.tessel.core.sim.SHOP_SLOTS
import dev.david.tessel.core.sim.TICK_HZ
import dev.david.tessel.core.sim.World
import dev.david.tessel.design.Palette
import dev.david.tessel.platform.audio.Haptics
import dev.david.tessel.platform.input.Gamepad
import dev.david.tessel.platform.input.StickView
import dev.david.tessel.platform.input.TwinStickController
import dev.david.tessel.platform.net.HostLink
import dev.david.tessel.platform.net.Link
import dev.david.tessel.platform.store.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Alone, running the game for a team, or drawing what the host runs. */
enum class Role { SOLO, HOST, CLIENT }

/**
 * Owns the game thread and the run. The sim advances in fixed 60 Hz steps no
 * matter how fast the screen refreshes; drawing blends between the last two.
 *
 * Menus talk to it through the commands below, which hop onto the game
 * thread, and read back through the state flows. In co-op the host phone
 * runs the only simulation; the phones that joined follow its snapshots.
 */
@SuppressLint("ViewConstructor")
class GameSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Choreographer.FrameCallback {

    private val density = resources.displayMetrics.density
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val controller = TwinStickController(density)

    /** A game controller, if one is connected. MainActivity hands it the axis events. */
    val gamepad = Gamepad()
    private val world = World(seed = SystemClock.elapsedRealtimeNanos())
    private val viewport = Viewport()
    private val renderer = Renderer(density, paletteFor(resources.configuration)).apply {
        stickRadius = controller.stickRadius
    }
    private val hud = Hud(density, resources.configuration.fontScale)
    private val shards = Shards()
    private val haptics = Haptics(context)
    private val stats = FrameStats()
    private val inputs = Array(MAX_PLAYERS) { PlayerInput() }
    private val moveView = StickView()
    private val aimView = StickView()

    /** Set before the first frame; only honoured in debug builds. */
    var stress: StressRig? = null
        set(value) {
            field = if (debuggable) value else null
        }

    /** Debug builds: a bot plays this phone's player, so a two-phone test can run hands-free. */
    var autopilot: Bot? = null
        set(value) {
            field = if (debuggable) value else null
        }

    private val modeFlow = MutableStateFlow(Mode.HOME)
    val mode: StateFlow<Mode> = modeFlow
    private val shopFlow = MutableStateFlow<ShopView?>(null)
    val shop: StateFlow<ShopView?> = shopFlow
    private val summaryFlow = MutableStateFlow<RunSummary?>(null)
    val summary: StateFlow<RunSummary?> = summaryFlow
    private val roleFlow = MutableStateFlow(Role.SOLO)
    val role: StateFlow<Role> = roleFlow

    // What carries each link, from Nearby: 0 unknown, 1 Bluetooth LE, 2 Bluetooth, 3 Wi-Fi. Written on the main thread.
    private val linkQuality = IntArray(MAX_PLAYERS)

    // Links that skip Nearby and go straight over the local network. Written on the main thread.
    private val direct = BooleanArray(MAX_PLAYERS)

    /** True while the host holds the game for a phone that dropped out. */
    private val heldFlow = MutableStateFlow(false)
    val held: StateFlow<Boolean> = heldFlow

    private val thread = HandlerThread("game").apply { start() }
    private val handler = Handler(thread.looper)
    private lateinit var choreographer: Choreographer

    // Guards the surface and everything draw() reads that the main thread can change.
    private val surfaceLock = Any()
    private var surfaceLive = false
    private var resumed = false
    @Volatile private var running = false

    // Game thread only.
    private var current = Mode.HOME
    private var playingAs = Role.SOLO
    private var host: HostSession? = null
    private var client: ClientSession? = null
    private var loopback: LoopbackHost? = null
    private var me = HOST_PLAYER

    /** Host: bit p set while player p's phone is out of reach. Client: bit 0 while the host is. */
    private var unreachable = 0
    private var publishedReady = 0
    private var clientShopKey = 0
    private var lastFrameNanos = 0L
    private var pendingNanos = 0L
    private var statWindows = 0
    private var nextNetLog = 0L
    @Volatile private var cutout = Insets.NONE

    init {
        holder.addCallback(this)
        keepScreenOn = true
        // Choreographer is per thread; asking for it here would hand back the main one.
        handler.post { choreographer = Choreographer.getInstance() }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            cutout = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout())
            fit()
            insets
        }
    }

    // Commands from the menus and the lobby. Each one runs on the game thread, in order.

    fun startRun(difficulty: Difficulty) = onGameThread {
        dropCoop()
        world.reset(players = 1, seed = SystemClock.elapsedRealtimeNanos(), difficulty = difficulty)
        freshRun()
    }

    fun startCoopHost(link: HostLink, difficulty: Difficulty, players: Int) = onGameThread {
        dropCoop()
        becomeRole(Role.HOST)
        world.reset(players = players, seed = SystemClock.elapsedRealtimeNanos(), difficulty = difficulty)
        for (p in 1 until players) world.remote[p] = true
        host = HostSession(world, link::send).also {
            for (p in 1 until players) it.connect(p)
            it.onRequest = ::onPartnerAsk
            it.announceStart()
        }
        freshRun()
    }

    fun startCoopClient(link: Link, you: Int, players: Int) = onGameThread {
        dropCoop()
        becomeRole(Role.CLIENT)
        me = you
        client = ClientSession(you, players, link::send).also { it.reset() }
        freshRun()
    }

    /** Debug builds: plays the joining side against a bot host inside this phone. */
    fun startLoopback() = onGameThread {
        if (!debuggable) return@onGameThread
        dropCoop()
        becomeRole(Role.CLIENT)
        val fake = LoopbackHost(handler, LOOPBACK_DELAY_MS) { bytes -> fromHost(bytes, System.nanoTime()) }
        me = LoopbackHost.YOU
        client = ClientSession(LoopbackHost.YOU, LoopbackHost.PLAYERS, fake.clientLink::send).also { it.reset() }
        loopback = fake
        fake.begin()
        freshRun()
    }

    /** A message from another phone, from any thread. [from] is its player number; the host is 0. */
    fun deliver(from: Int, bytes: ByteArray) {
        val arrived = System.nanoTime()
        handler.post {
            when (playingAs) {
                Role.HOST -> host?.receive(from, bytes, arrived)
                Role.CLIENT -> fromHost(bytes, arrived)
                Role.SOLO -> Unit
            }
        }
    }

    fun pause() = onGameThread {
        when (playingAs) {
            Role.CLIENT -> if (current == Mode.PLAYING) client?.action(Wire.PAUSE)
            else -> if (current == Mode.PLAYING) enter(Mode.PAUSED)
        }
    }

    fun resume() = onGameThread {
        when (playingAs) {
            Role.CLIENT -> client?.action(Wire.RESUME)
            else -> if (current == Mode.PAUSED && unreachable == 0) {
                restartClock()
                enter(Mode.PLAYING)
            }
        }
    }

    fun playAgain(difficulty: Difficulty) = onGameThread {
        when (playingAs) {
            Role.SOLO -> {
                world.reset(players = 1, seed = SystemClock.elapsedRealtimeNanos(), difficulty = difficulty)
                freshRun()
            }
            Role.HOST -> restartHost()
            Role.CLIENT -> client?.action(Wire.RESTART)
        }
    }

    fun leaveRun() = onGameThread {
        dropCoop()
        shards.clear()
        shopFlow.value = null
        enter(Mode.HOME)
    }

    fun buy(slot: Int) = onGameThread {
        if (current != Mode.SHOP) return@onGameThread
        if (playingAs == Role.CLIENT) client?.action(Wire.BUY, slot)
        else if (world.shop.buy(world, HOST_PLAYER, slot)) publishShop()
    }

    fun heal() = onGameThread {
        if (current != Mode.SHOP) return@onGameThread
        if (playingAs == Role.CLIENT) client?.action(Wire.HEAL)
        else if (world.shop.heal(world, HOST_PLAYER)) publishShop()
    }

    fun reroll() = onGameThread {
        if (current != Mode.SHOP) return@onGameThread
        if (playingAs == Role.CLIENT) client?.action(Wire.REROLL)
        else if (world.shop.reroll(world, HOST_PLAYER)) publishShop()
    }

    /** Alone this starts the next stage; in co-op it marks this player ready. */
    fun nextStage() = onGameThread {
        if (current != Mode.SHOP) return@onGameThread
        when (playingAs) {
            Role.SOLO -> {
                world.nextStage()
                restartClock()
                enter(Mode.PLAYING)
            }
            Role.HOST -> {
                host?.markReady(HOST_PLAYER)
                publishShop()
            }
            Role.CLIENT -> client?.action(Wire.READY)
        }
    }

    /** A phone dropped out mid-game ([player] 0 on a joining phone means the host). */
    fun linkLost(player: Int) = onGameThread {
        unreachable = unreachable or (1 shl player)
        host?.disconnect(player)
        if (playingAs == Role.HOST && current == Mode.PLAYING) enter(Mode.PAUSED)
        heldFlow.value = playingAs == Role.HOST
    }

    fun linkRestored(player: Int) = onGameThread {
        unreachable = unreachable and (1 shl player).inv()
        host?.connect(player)
        if (playingAs == Role.HOST) heldFlow.value = unreachable != 0
    }

    /** Host: a phone that is not coming back. Its player leaves the run; the rest go on. */
    fun dropPlayer(player: Int) = onGameThread {
        unreachable = unreachable and (1 shl player).inv()
        host?.disconnect(player)
        world.removePlayer(player)
        heldFlow.value = unreachable != 0
        if (current == Mode.SHOP) publishShop()
    }

    /** The host left, or this phone is the last one in the game. */
    fun partnerGone() = onGameThread {
        if (playingAs == Role.SOLO) return@onGameThread
        dropCoop()
        shards.clear()
        shopFlow.value = null
        enter(Mode.HOME)
    }

    /** The link to [player] (0 on a joining phone means the host) got faster or was just made. */
    fun setLinkQuality(player: Int, quality: Int) {
        if (player in 0 until MAX_PLAYERS) linkQuality[player] = quality
    }

    /** The link to [player] now goes straight over the local network, or no longer does. */
    fun setDirect(player: Int, on: Boolean) {
        if (player in 0 until MAX_PLAYERS) direct[player] = on
    }

    /** For the readout: 4 for a direct link, otherwise what Nearby reported. */
    private fun qualityOf(player: Int) = if (direct[player]) 4 else linkQuality[player]

    fun applySettings(s: Settings) {
        controller.leftHanded = s.leftHanded
        controller.stickRadiusDp = s.stickSize.radiusDp
        haptics.enabled = s.vibration
        haptics.shots = s.shotVibration
        handler.post {
            renderer.stickRadius = controller.stickRadius
            renderer.showStats = s.showStats
            hud.showLink = s.showLink
        }
    }

    fun setResumed(value: Boolean) {
        resumed = value
        updateRunning()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        synchronized(surfaceLock) { surfaceLive = true }
        updateRunning()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = fit()

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Taking the lock waits out a frame that is still drawing.
        synchronized(surfaceLock) { surfaceLive = false }
        updateRunning()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
        thread.quitSafely()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        synchronized(surfaceLock) { renderer.palette = paletteFor(newConfig) }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (Build.VERSION.SDK_INT >= 29) {
            // Keeps a thumb that starts at the screen edge from triggering Back.
            // The system honours at most 200 dp of height per edge.
            val strip = (EDGE_STRIP_DP * density).toInt()
            val tall = (200 * density).toInt()
            systemGestureExclusionRects = listOf(
                Rect(0, height - tall, strip, height),
                Rect(width - strip, height - tall, width, height),
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        controller.onTouch(event)
        return true
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        choreographer.postFrameCallback(this)
        val now = SystemClock.uptimeMillis()
        val nowNanos = System.nanoTime()

        val elapsed = if (lastFrameNanos == 0L) STEP_NANOS else frameTimeNanos - lastFrameNanos
        lastFrameNanos = frameTimeNanos
        // After a stall, catching up on every missed step would only stall again.
        val usable = elapsed.coerceAtMost(MAX_CATCH_UP_NANOS)

        controller.read(inputs[0], moveView, aimView, now)
        gamepad.apply(inputs[0])
        val c = client
        autopilot?.drive(if (playingAs == Role.CLIENT && c != null) c.view else world, me, inputs[0])
        val simStart = System.nanoTime()
        val shown: World
        if (playingAs == Role.CLIENT && c != null) {
            loopback?.frame(usable, nowNanos)
            followHost(c)
            if (current == Mode.PLAYING) {
                pendingNanos += usable
                while (pendingNanos >= STEP_NANOS) {
                    c.tick(inputs[0], nowNanos)
                    pendingNanos -= STEP_NANOS
                }
            } else {
                pendingNanos = 0
            }
            c.frame(nowNanos)
            react(c.view.events, now)
            hud.link(c.roundTripMillis, qualityOf(0))
            shown = c.view
        } else {
            val h = host
            if (current == Mode.PLAYING) {
                stress?.apply(world, inputs[0])
                pendingNanos += usable
                while (pendingNanos >= STEP_NANOS) {
                    if (h != null) for (p in 1 until world.playerCount) inputs[p].copyFrom(h.remoteInput(p, nowNanos))
                    world.step(inputs)
                    pendingNanos -= STEP_NANOS
                }
            }
            if (h != null) {
                if (current == Mode.SHOP && h.allReady()) {
                    h.clearReady()
                    world.nextStage()
                    restartClock()
                    enter(Mode.PLAYING)
                } else if (current == Mode.SHOP && h.ready != publishedReady) {
                    publishShop()
                }
                h.collect(world.events)
            }
            react(world.events, now)
            h?.pump(nowNanos, current == Mode.PAUSED, unreachable != 0)
            if (h == null) hud.link(-1, -1) else showWorstLink(h)
            shown = world
        }
        shards.update(usable / 1e9f)
        val blend = if (playingAs != Role.CLIENT && current == Mode.PLAYING) pendingNanos.toFloat() / STEP_NANOS else 1f
        val drawStart = System.nanoTime()

        synchronized(surfaceLock) {
            if (!surfaceLive) return
            val canvas = holder.lockHardwareCanvas() ?: return
            try {
                if (current == Mode.HOME) {
                    canvas.drawColor(renderer.palette.ground)
                } else {
                    renderer.draw(canvas, shown, blend, viewport, shards, moveView, aimView, me)
                    val inset = cutout
                    hud.draw(canvas, shown, renderer.palette, inset.left, inset.top, inset.right, me, now)
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        if (playingAs != Role.SOLO && nowNanos >= nextNetLog) {
            nextNetLog = nowNanos + NET_LOG_NANOS
            logNet()
        }

        if (stats.record(elapsed, drawStart - simStart, System.nanoTime() - drawStart)) {
            renderer.setStats(stats, shown.enemies.count, shown.bullets.count)
            if (debuggable && ++statWindows % LOG_EVERY_WINDOWS == 0) {
                Log.d(TAG, "fps=${stats.fps} frame=${stats.frameMs} worst=${stats.worstMs} sim=${stats.simMs} draw=${stats.drawMs} enemies=${shown.enemies.count} stage=${shown.stage} mode=$current role=$playingAs")
            }
        }
    }

    /** Turns what happened this frame into shards, haptics and menu changes, then forgets it. */
    private fun react(ev: Events, now: Long) {
        for (i in 0 until ev.count) {
            when (ev.kind[i]) {
                Events.SHOT -> if (ev.who[i] == me) haptics.shot(now)
                Events.ENEMY_KILLED, Events.ENEMY_GONE -> {
                    val type = ev.who[i]
                    if (type !in 0 until Bestiary.size) continue
                    val def = Bestiary.of(type)
                    shards.burst(
                        def.shape, ev.x[i], ev.y[i], def.radius, ev.angle[i], ev.dx[i], ev.dy[i],
                        renderer.bodyOf(type), renderer.facetOf(type),
                    )
                }
                Events.BOSS_IN -> {
                    hud.announce(ev.who[i], now)
                    haptics.down()
                }
                Events.BOSS_DOWN -> haptics.down()
                Events.PLAYER_HIT -> if (ev.who[i] == me) haptics.hurt()
                Events.PLAYER_DOWN -> if (ev.who[i] == me) haptics.down()
                Events.REVIVED -> if (ev.who[i] == me) haptics.hurt()
                Events.SHOP_OPEN -> if (playingAs != Role.CLIENT) {
                    host?.clearReady()
                    publishShop()
                    enter(Mode.SHOP)
                }
                Events.RUN_OVER -> {
                    haptics.down()
                    if (playingAs != Role.CLIENT) {
                        val pl = world.players
                        summaryFlow.value = RunSummary(
                            seconds = world.tick / TICK_HZ,
                            stage = world.stage,
                            kills = pl.kills[me],
                            coins = pl.earned[me],
                            difficulty = world.difficulty,
                            players = world.presentCount(),
                        )
                        enter(Mode.OVER)
                    }
                }
            }
        }
        ev.clear()
    }

    private fun fromHost(bytes: ByteArray, arrived: Long) {
        val c = client ?: return
        if (Wire.typeOf(bytes) == Wire.START) {
            // The host began a fresh run (Play Again). Same session, new game.
            c.reset()
            shards.clear()
            summaryFlow.value = null
            shopFlow.value = null
            restartClock()
            enter(Mode.PLAYING)
            return
        }
        c.receive(bytes, arrived)
        val s = c.latest ?: return
        heldFlow.value = s.waiting
    }

    /** On the joining phone the menus follow the host: its pause, its shop, its end of run. */
    private fun followHost(c: ClientSession) {
        val s = c.latest ?: return
        val wanted = when {
            s.paused -> Mode.PAUSED
            s.phase == Phase.SHOP -> Mode.SHOP
            s.phase == Phase.OVER -> Mode.OVER
            else -> Mode.PLAYING
        }
        if (wanted == Mode.SHOP) publishClientShop(s)
        if (wanted == Mode.OVER && current != Mode.OVER) {
            summaryFlow.value = RunSummary(
                seconds = s.tick / TICK_HZ,
                stage = s.stage,
                kills = s.kills[me],
                coins = s.earned[me],
                difficulty = s.difficulty,
                players = Integer.bitCount(s.present),
            )
        }
        if (wanted != current) {
            if (wanted == Mode.PLAYING) restartClock()
            enter(wanted)
        }
    }

    /**
     * Every two seconds in co-op, one line on how the link is doing, so a
     * slow game can be looked into afterwards with logcat.
     */
    private fun logNet() {
        val c = client
        val h = host
        if (playingAs == Role.CLIENT && c != null) {
            Log.d(TAG, "net client via=${if (direct[0]) "direct" else "nearby"} rtt=${c.roundTripMillis} spread=${c.spreadMillis} " +
                "delay=${c.delayNanos / 1_000_000} target=${c.targetDelayNanos / 1_000_000} starved=${c.starvedFrames} mode=$current")
        } else if (h != null) {
            for (p in 1 until world.playerCount) {
                if (h.linked and (1 shl p) == 0) continue
                Log.d(TAG, "net host p=$p via=${if (direct[p]) "direct" else "nearby"} rtt=${h.roundTripMillis(p)} " +
                    "spread=${h.spreadMillis(p)} playback=${h.playbackMillis(p)} mode=$current")
            }
        }
    }

    /** The host shows its slowest link: that is the player who will feel it. */
    private fun showWorstLink(h: HostSession) {
        var trip = -1
        var quality = 0
        for (p in 1 until world.playerCount) {
            if (h.linked and (1 shl p) == 0) continue
            trip = maxOf(trip, h.roundTripMillis(p))
            val q = qualityOf(p)
            if (q != 0 && (quality == 0 || q < quality)) quality = q
        }
        hud.link(trip, quality)
    }

    private fun onPartnerAsk(kind: Int) {
        when (kind) {
            Wire.PAUSE -> if (current == Mode.PLAYING) enter(Mode.PAUSED)
            Wire.RESUME -> if (current == Mode.PAUSED && unreachable == 0) {
                restartClock()
                enter(Mode.PLAYING)
            }
            Wire.RESTART -> if (current == Mode.OVER) restartHost()
        }
    }

    private fun restartHost() {
        // Players who left stay out of the new run too.
        val gone = BooleanArray(world.playerCount) { !world.players.present[it] }
        world.reset(players = world.playerCount, seed = SystemClock.elapsedRealtimeNanos(), difficulty = world.difficulty)
        for (p in 1 until world.playerCount) world.remote[p] = true
        for (p in gone.indices) if (gone[p]) world.removePlayer(p)
        host?.announceStart()
        freshRun()
    }

    private fun freshRun() {
        shards.clear()
        shopFlow.value = null
        summaryFlow.value = null
        clientShopKey = 0
        restartClock()
        enter(Mode.PLAYING)
    }

    private fun becomeRole(role: Role) {
        playingAs = role
        roleFlow.value = role
    }

    private fun dropCoop() {
        host = null
        client = null
        loopback = null
        unreachable = 0
        me = HOST_PLAYER
        heldFlow.value = false
        becomeRole(Role.SOLO)
    }

    private fun publishShop() {
        val s = world.shop
        val pl = world.players
        val me = HOST_PLAYER
        val offers = ArrayList<ShopOffer>(SHOP_SLOTS)
        for (slot in 0 until SHOP_SLOTS) {
            val u = s.item(me, slot)
            if (u < 0) continue
            val def = Upgrades.of(u)
            offers += ShopOffer(
                slot = slot,
                name = def.name,
                blurb = def.blurb,
                level = pl.levels[me][u],
                maxLevel = def.maxLevel,
                price = s.price(world, me, slot),
                maxed = s.maxed(world, me, slot),
                affordable = s.canBuy(world, me, slot),
            )
        }
        val ready = host?.ready ?: 0
        publishedReady = ready
        shopFlow.value = ShopView(
            stage = world.stage,
            coins = pl.coins[me],
            hp = pl.hp[me],
            maxHp = pl.maxHp[me],
            offers = offers,
            healPrice = s.healPrice(world),
            healsLeft = s.healsLeft(me),
            canHeal = s.canHeal(world, me),
            rerollPrice = s.rerollPrice(world, me),
            canReroll = s.canReroll(world, me),
            coop = playingAs == Role.HOST,
            selfReady = ready and (1 shl HOST_PLAYER) != 0,
            readyCount = Integer.bitCount(ready),
            teamSize = world.presentCount(),
        )
    }

    /** Rebuilds the joining phone's shop view, but only when something in it changed. */
    private fun publishClientShop(s: Snapshot) {
        if (!s.hasShop) return
        var key = s.coins[me] * 31 + s.hp[me]
        key = key * 31 + s.ready
        key = key * 31 + s.healsLeft
        key = key * 31 + s.rerollPrice
        key = key * 31 + s.stage
        for (slot in 0 until SHOP_SLOTS) {
            key = key * 31 + s.shopItem[slot]
            key = key * 31 + s.shopLevel[slot]
            key = key * 31 + s.shopPrice[slot]
        }
        if (key == clientShopKey && shopFlow.value != null) return
        clientShopKey = key

        val offers = ArrayList<ShopOffer>(SHOP_SLOTS)
        for (slot in 0 until SHOP_SLOTS) {
            val u = s.shopItem[slot]
            if (u < 0) continue
            val def = Upgrades.of(u)
            offers += ShopOffer(
                slot = slot,
                name = def.name,
                blurb = def.blurb,
                level = s.shopLevel[slot],
                maxLevel = def.maxLevel,
                price = s.shopPrice[slot],
                maxed = s.shopMaxed[slot],
                affordable = s.shopAffordable[slot],
            )
        }
        shopFlow.value = ShopView(
            stage = s.stage,
            coins = s.coins[me],
            hp = s.hp[me],
            maxHp = s.maxHp[me],
            offers = offers,
            healPrice = s.healPrice,
            healsLeft = s.healsLeft,
            canHeal = s.canHeal,
            rerollPrice = s.rerollPrice,
            canReroll = s.canReroll,
            coop = true,
            selfReady = s.ready and (1 shl me) != 0,
            readyCount = Integer.bitCount(s.ready),
            teamSize = Integer.bitCount(s.present),
        )
    }

    private fun onGameThread(block: () -> Unit) {
        handler.post(block)
    }

    private fun enter(mode: Mode) {
        current = mode
        modeFlow.value = mode
    }

    private fun restartClock() {
        lastFrameNanos = 0L
        pendingNanos = 0L
    }

    private fun updateRunning() {
        val shouldRun = resumed && synchronized(surfaceLock) { surfaceLive }
        if (shouldRun == running) return
        running = shouldRun
        if (shouldRun) {
            handler.post {
                restartClock()
                // A quick pause and resume can leave the old callback queued; two would double the game speed.
                choreographer.removeFrameCallback(this)
                choreographer.postFrameCallback(this)
            }
        }
    }

    private fun fit() {
        if (width == 0 || height == 0) return
        synchronized(surfaceLock) { viewport.fit(width, height) }
        val c = cutout
        controller.layout(width, height, c.left, c.top, c.right, c.bottom)
    }

    private fun paletteFor(config: Configuration): Palette {
        val night = config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return if (night) Palette.Dark else Palette.Light
    }

    private companion object {
        const val TAG = "Tessel"
        const val STEP_NANOS = 1_000_000_000L / TICK_HZ
        const val MAX_CATCH_UP_NANOS = 100_000_000L
        const val EDGE_STRIP_DP = 48f
        const val LOG_EVERY_WINDOWS = 10
        const val NET_LOG_NANOS = 2_000_000_000L
        const val LOOPBACK_DELAY_MS = 35L
    }
}
