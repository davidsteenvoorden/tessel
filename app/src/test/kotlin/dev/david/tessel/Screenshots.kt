package dev.david.tessel

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.captureToImage
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.content.Upgrades
import dev.david.tessel.core.sim.Bot
import dev.david.tessel.core.sim.Events
import dev.david.tessel.core.sim.MAX_PLAYERS
import dev.david.tessel.core.sim.PlayerInput
import dev.david.tessel.core.sim.SHOP_SLOTS
import dev.david.tessel.core.sim.TICK_SECONDS
import dev.david.tessel.core.sim.World
import dev.david.tessel.design.Palette
import dev.david.tessel.platform.game.Hud
import dev.david.tessel.platform.game.Renderer
import dev.david.tessel.platform.game.ShopOffer
import dev.david.tessel.platform.game.ShopView
import dev.david.tessel.platform.game.Shards
import dev.david.tessel.platform.game.Viewport
import dev.david.tessel.platform.input.StickView
import dev.david.tessel.ui.components.Sheet
import dev.david.tessel.platform.store.Records
import dev.david.tessel.platform.store.Settings
import dev.david.tessel.platform.update.Updater
import dev.david.tessel.ui.screens.HomeScreen
import dev.david.tessel.ui.screens.SettingsScreen
import dev.david.tessel.ui.screens.ShopPanel
import dev.david.tessel.ui.screens.PausePanel
import dev.david.tessel.ui.screens.RunOverCard
import dev.david.tessel.ui.screens.SavedGame
import dev.david.tessel.platform.game.PlayerStats
import dev.david.tessel.platform.game.RunSummary
import dev.david.tessel.core.sim.Checkpoint
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.david.tessel.ui.theme.TesselTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the pictures in docs/ with the game's own renderer and menus, at the
 * Galaxy A71's resolution, using Robolectric's real Android graphics. Runs
 * only through `./gradlew shots`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w914dp-h411dp-land-night-420dpi")
class Screenshots {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val width = 2400
    private val height = 1080
    private val density = 2.625f
    private val out = File(System.getProperty("shots.dir") ?: "docs").apply { mkdirs() }

    /** A run that has been going a while: a few upgrades bought, a bot at the sticks. */
    private class Scene(players: Int, stage: Int, seed: Long, palette: Palette, density: Float) {
        val world = World(seed)
        val renderer = Renderer(density, palette)
        val hud = Hud(density, 1f)
        val shards = Shards()
        val bots = Array(players) { Bot(heading = it * 1.7f) }
        val inputs = Array(MAX_PLAYERS) { PlayerInput() }

        init {
            world.reset(players = players, seed = seed, difficulty = Difficulty.NORMAL)
            for (p in 0 until players) {
                val lv = world.players.levels[p]
                lv[Upgrades.DAMAGE] = stage / 3
                lv[Upgrades.FIRE_RATE] = stage / 3
                lv[Upgrades.MULTISHOT] = minOf(2, stage / 5)
                lv[Upgrades.VELOCITY] = 2
                lv[Upgrades.SPEED] = 2
                // Bots do not go after coins the way people do; a long reach keeps the floor tidy.
                lv[Upgrades.MAGNET] = 6
            }
            world.skipTo(stage)
        }

        fun play(seconds: Float, hurt: Int = 1) {
            repeat((seconds / TICK_SECONDS).toInt()) {
                for (p in 0 until world.playerCount) {
                    world.players.hp[p] = world.players.maxHp[p] - hurt
                    bots[p].drive(world, p, inputs[p])
                }
                world.step(inputs)
                val ev = world.events
                for (i in 0 until ev.count) {
                    if (ev.kind[i] != Events.ENEMY_KILLED) continue
                    val type = ev.who[i]
                    val def = Bestiary.of(type)
                    shards.burst(def.shape, ev.x[i], ev.y[i], def.radius, ev.angle[i], ev.dx[i], ev.dy[i], renderer.bodyOf(type), renderer.facetOf(type))
                }
                ev.clear()
                shards.update(TICK_SECONDS)
            }
        }
    }

    private fun draw(scene: Scene, palette: Palette, me: Int = 0): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val view = Viewport().apply { fit(width, height) }
        // Thumbs on the sticks: the left one pushed the way the player is walking, the right one aiming.
        val move = StickView().apply {
            held = true
            originX = 330f
            originY = 800f
            val a = atan2(scene.inputs[me].moveY, scene.inputs[me].moveX)
            knobX = originX + cos(a) * 95f
            knobY = originY + sin(a) * 95f
        }
        val aim = StickView().apply {
            held = true
            originX = width - 360f
            originY = 760f
            knobX = originX + scene.world.players.aimX[me] * 110f
            knobY = originY + scene.world.players.aimY[me] * 110f
        }
        scene.renderer.draw(canvas, scene.world, 1f, view, scene.shards, move, aim, me)
        scene.hud.draw(canvas, scene.world, palette, 0, 0, 0, me, 0L)
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String, dir: File = out) {
        dir.mkdirs()
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Menus worth a look but not for the README. */
    private val checks = File("build/screens")

    @Test
    fun aStageInFullSwing() {
        val scene = Scene(players = 1, stage = 13, seed = 1313, palette = Palette.Dark, density = density)
        scene.play(31f)
        settle(scene)
        save(draw(scene, Palette.Dark), "stage.png")
    }

    @Test
    fun aBoss() {
        val scene = Scene(players = 1, stage = 20, seed = 2020, palette = Palette.Dark, density = density)
        scene.world.spawning = false
        scene.play(9.5f, hurt = 2)
        settle(scene)
        // Twenty stages in, the wallet is never empty.
        scene.world.players.coins[0] = 1260
        save(draw(scene, Palette.Dark), "boss.png")
    }

    /** Steps on to a frame where nothing is mid hit-flash, so every body shows its own colour. */
    private fun settle(scene: Scene) {
        val e = scene.world.enemies
        repeat(30) {
            var flashing = false
            for (i in 0 until e.count) if (e.flash[i] > 0) flashing = true
            if (!flashing) return
            scene.play(TICK_SECONDS)
        }
    }

    @Test
    fun aTeamOfFour() {
        val scene = Scene(players = 4, stage = 12, seed = 412, palette = Palette.Light, density = density)
        scene.play(22f)
        settle(scene)
        // The host's readout: its slowest link, over Wi-Fi.
        scene.hud.link(34, 3)
        save(draw(scene, Palette.Light, me = 0), "team.png")
        // And how a slow Bluetooth link reads, for checking rather than the README.
        scene.hud.link(180, 2)
        save(draw(scene, Palette.Light, me = 0), "slow-link.png", checks)
    }

    @Test
    fun theShop() {
        val scene = Scene(players = 1, stage = 6, seed = 606, palette = Palette.Dark, density = density)
        scene.play(14f)
        val arena = draw(scene, Palette.Dark)
        val stage = 6
        val picks = intArrayOf(Upgrades.FIRE_RATE, Upgrades.MULTISHOT, Upgrades.DAMAGE, Upgrades.PIERCE, Upgrades.SPEED)
        val levels = intArrayOf(4, 1, 3, 0, 2)
        val coins = 418
        scene.world.players.coins[0] = coins
        val offers = List(SHOP_SLOTS) { k ->
            val u = picks[k]
            val def = Upgrades.of(u)
            val price = Upgrades.price(u, levels[k], stage)
            ShopOffer(k, def.name, def.blurb, levels[k], def.maxLevel, price, maxed = false, affordable = price <= coins)
        }
        val shop = ShopView(
            stage = stage, coins = coins, hp = 4, maxHp = 7, offers = offers,
            healPrice = Upgrades.healPrice(stage), healsLeft = 2, canHeal = true,
            rerollPrice = Upgrades.rerollPrice(stage, 0), canReroll = true,
        )
        compose.setContent {
            TesselTheme {
                Box(Modifier.fillMaxSize()) {
                    Image(arena.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    Sheet(visible = true, onDismiss = null) {
                        ShopPanel(shop, vibrate = false, onBuy = {}, onHeal = {}, onReroll = {}, onNext = {})
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(3000)
        compose.waitForIdle()
        save(compose.onRoot().captureToImage().asAndroidBitmap(), "shop.png")
    }

    private fun team(n: Int, stage: Int): RunSummary {
        val names = listOf("Galaxy A71", "Sam", "Noor's A13", "Jules", "Ava", "Teo", "Mika", "Lou")
        val team = List(n) { p ->
            PlayerStats(
                name = names[p], you = p == 1, present = p != 3 || n < 4,
                kills = 180 + p * 47 % 120, damage = 9_400 + p * 2_731 % 9_000, shots = 2_900 + p * 611 % 1_500,
                coins = 640 + p * 93 % 300, hitsTaken = 3 + p * 5 % 9, downed = p % 3, revives = (p * 2 + 1) % 4,
                upgrades = 14 + p % 5,
            )
        }
        return RunSummary(seconds = stage * 64 + 13, stage = stage, kills = team[minOf(1, n - 1)].kills, coins = team[minOf(1, n - 1)].coins,
            difficulty = Difficulty.NORMAL, players = n, bosses = stage / 5, team = if (n == 1) listOf(team[0].let {
                PlayerStats("You", true, true, it.kills, it.damage, it.shots, it.coins, it.hitsTaken, 0, 0, it.upgrades)
            }) else team)
    }

    @Test
    fun pauseAndRunOver() {
        val scene = Scene(players = 2, stage = 7, seed = 707, palette = Palette.Dark, density = density)
        scene.play(10f)
        val arena = draw(scene, Palette.Dark)
        var page by mutableStateOf(0)
        compose.setContent {
            TesselTheme {
                Box(Modifier.fillMaxSize()) {
                    Image(arena.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    when (page) {
                        0 -> Sheet(visible = true, onDismiss = {}) { PausePanel(false, true, team(1, 7), {}, {}, {}, {}) }
                        1, 2 -> Sheet(visible = true, onDismiss = {}) { PausePanel(true, true, team(4, 7), {}, {}, {}, {}) }
                        3 -> RunOverCard(true, team(1, 9), newBest = true, onAgain = {}, onHome = {})
                        4 -> RunOverCard(true, team(8, 12), newBest = false, onAgain = {}, onHome = {})
                        else -> Box(Modifier.fillMaxSize().padding(48.dp)) {
                            val cp = Checkpoint(Difficulty.HARD, 6, 6 * 3900, 3, true, 1)
                            cp.names[1] = "Sam"
                            cp.names[2] = "Noor's A13"
                            SavedGame(cp, {}, {})
                        }
                    }
                }
            }
        }
        val names = listOf("pause-solo", "pause-team", "pause-save", "over-solo", "over-team", "saved-game")
        for (k in names.indices) {
            page = k
            compose.mainClock.advanceTimeBy(1500)
            if (k == 2) {
                compose.onNodeWithText("Multiplayer Pause").performClick()
                compose.mainClock.advanceTimeBy(500)
            }
            compose.waitForIdle()
            save(compose.onRoot().captureToImage().asAndroidBitmap(), "${names[k]}.png", checks)
        }
    }

    @Test
    fun homeAndSettings() {
        val updater = Updater(compose.activity)
        var page by mutableStateOf(0)
        compose.setContent {
            TesselTheme {
                if (page == 0) {
                    HomeScreen(Settings(difficulty = Difficulty.EASY), Records(), {}, {}, {}, {})
                } else {
                    SettingsScreen(Settings(), updater, inRun = false, onChange = {}, onBack = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        save(compose.onRoot().captureToImage().asAndroidBitmap(), "home.png", checks)
        page = 1
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Check for Updates").performScrollTo()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        save(compose.onRoot().captureToImage().asAndroidBitmap(), "settings.png", checks)
    }
}
