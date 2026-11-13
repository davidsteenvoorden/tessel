package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Balance
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.rng.Rng
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val ARENA_W = 1600f
const val ARENA_H = 720f
const val TICK_HZ = 60
const val TICK_SECONDS = 1f / TICK_HZ
const val MAX_PLAYERS = 8
const val MAX_ENEMIES = 400
const val MAX_BULLETS = 512
const val MAX_HOSTILE = 600
const val MAX_COINS = 300

/** Enemies may stand this far outside the arena while they walk in. */
const val OUTSIDE = 96f

const val STAGE_TICKS = Balance.STAGE_SECONDS * TICK_HZ
private const val CLEAR_TICKS = Balance.CLEAR_SECONDS * TICK_HZ
private const val GRID_CELL = 48f
private const val FLASH_TICKS = 3
private const val HURT_TICKS = 8
private const val KNOCK_DECAY = 0.86f
private const val SEPARATION = 0.5f
private const val MUZZLE = 1.3f
private const val BULLET_MARGIN = 24f

/** A bullet checks for hits at least this often along its path, so fast shots cannot skip a small enemy. */
private const val SUBSTEP = 8f
private const val COIN_DRAG = 0.9f
private const val MAGNET_ACCEL = 1800f
private const val MAGNET_TOP_SPEED = 900f
private const val TWO_PI = (2 * PI).toFloat()


/** The boss walks in this long after a boss stage begins. */
private const val BOSS_DELAY_TICKS = 90

/** While its drones fly, the Apiary takes this share of each hit. */
private const val SHIELDED_DAMAGE = 0.3f

val INVULN_TICKS = (Balance.INVULN_SECONDS * TICK_HZ).toInt()
val HOSTILE_TICKS = (Balance.HOSTILE_SECONDS * TICK_HZ).toInt()

/** A shot has to reach this far into the player's body to count; grazes are free. */
const val GRAZE = 0.75f
val REVIVE_TICKS = (Balance.REVIVE_SECONDS * TICK_HZ).toInt()

enum class Phase {
    /** Enemies keep arriving until the stage clock runs out, or the boss falls. */
    FIGHT,

    /** No more arrivals; the stragglers have a few seconds. */
    CLEAR,

    /** Between stages. Nothing moves until [World.nextStage]. */
    SHOP,
    OVER,
}

class World(seed: Long = 1L) {
    val rng = Rng(seed)
    val events = Events()
    val players = PlayerState()
    val bullets = BulletPool(MAX_BULLETS)

    /** Enemy shots. Same storage as the players' bullets, owner -1. */
    val hostile = BulletPool(MAX_HOSTILE)
    val enemies = EnemyPool(MAX_ENEMIES)
    val coins = CoinPool(MAX_COINS)
    val shop = Shop()
    private val director = Director()
    private val brains = Brains(this)

    // Bosses stay out of the grid: they are big enough to span many cells and are checked on their own.
    internal val grid = Grid(GRID_CELL, -OUTSIDE, -OUTSIDE, ARENA_W + OUTSIDE, ARENA_H + OUTSIDE, MAX_ENEMIES)

    /** Off in tests that want an empty arena. */
    var spawning = true

    var tick = 0
        private set
    var playerCount = 1
        private set
    var difficulty = Difficulty.NORMAL
        private set
    var phase = Phase.FIGHT
        private set
    var stage = 1
        private set

    /** Ticks of fighting in the current stage. */
    var stageTicks = 0
        private set

    /**
     * Players on another phone. Their phone decides what their shots hit and
     * what hits them, from what it shows, so a slow link never costs them a
     * hit they dodged or a kill they landed. Here their shots are only drawn.
     */
    val remote = BooleanArray(MAX_PLAYERS)

    /** Index of the boss in [enemies], or -1. Refreshed whenever indices can move. */
    var boss = -1
        private set

    /** On a client, the host's word for the boss: its type and how much health it has left. */
    private var shownBossType = -1
    private var shownBossHealth = 0f

    val over get() = phase == Phase.OVER

    private var clearTicks = 0
    private var coinBase = 1
    private var nextId = 1
    private var bossDue = -1

    init {
        reset(seed = seed)
    }

    fun reset(players: Int = 1, seed: Long = rng.nextLong(), difficulty: Difficulty = this.difficulty) {
        rng.seed(seed)
        this.difficulty = difficulty
        tick = 0
        phase = Phase.FIGHT
        stage = 1
        stageTicks = 0
        clearTicks = 0
        coinBase = coinBaseFor(stage)
        nextId = 1
        playerCount = players.coerceIn(1, MAX_PLAYERS)
        this.players.reset(playerCount, difficulty.bonusHp)
        bullets.clear()
        hostile.clear()
        enemies.clear()
        coins.clear()
        events.clear()
        boss = -1
        bossDue = -1
        shownBossType = -1
        remote.fill(false)
        director.startStage()
    }

    fun step(inputs: Array<PlayerInput>) {
        if (phase == Phase.SHOP || phase == Phase.OVER) return
        tick++
        if (phase == Phase.FIGHT) {
            stageTicks++
            if (stageTicks >= STAGE_TICKS && !isBossStage()) endFight()
        } else {
            clearTicks++
        }

        players.remember()
        bullets.remember()
        hostile.remember()
        enemies.remember()
        coins.remember()

        for (p in 0 until playerCount) if (players.present[p]) updatePlayer(p, inputs[p])
        if (bossDue > 0 && --bossDue == 0) bringBoss()
        brains.think()
        grid.clear()
        for (i in 0 until enemies.count) if (i != boss) grid.insert(i, enemies.x[i], enemies.y[i])
        separateEnemies()
        moveBullets()
        moveHostile()
        touchPlayers()
        revivePlayers()
        moveCoins()

        bullets.sweep()
        hostile.sweep()
        enemies.sweep()
        coins.sweep()
        findBoss()
        if (spawning && phase == Phase.FIGHT) director.update(this)

        when {
            nobodyStanding() -> {
                phase = Phase.OVER
                events.add(Events.RUN_OVER, 0)
            }
            phase == Phase.CLEAR && (enemies.count == 0 || clearTicks >= CLEAR_TICKS) -> openShop()
        }
    }

    /** Leaves the shop and starts the next, harder stage. */
    fun nextStage() {
        if (phase != Phase.SHOP) return
        stage++
        stageTicks = 0
        coinBase = coinBaseFor(stage)
        phase = Phase.FIGHT
        // Anyone who went down comes back for the new stage at half health.
        for (p in 0 until playerCount) {
            if (players.present[p] && players.hp[p] == 0) players.hp[p] = max(1, players.maxHp[p] / 2)
        }
        players.remember()
        director.startStage()
        bossDue = if (isBossStage()) BOSS_DELAY_TICKS else -1
        events.add(Events.STAGE_START, stage)
    }

    /** Skips straight to the start of [stage]. For tests and the debug stress rig. */
    internal fun skipTo(stage: Int) {
        if (stage <= this.stage) return
        enemies.clear()
        hostile.clear()
        bullets.clear()
        coins.clear()
        boss = -1
        this.stage = stage - 1
        phase = Phase.SHOP
        nextStage()
    }

    /** A player whose phone left for good. Their body goes, the rest play on. */
    fun removePlayer(p: Int) {
        if (p !in 0 until playerCount || !players.present[p]) return
        players.present[p] = false
        players.hp[p] = 0
        for (i in 0 until coins.count) if (coins.target[i] == p) coins.target[i] = -1
        if (phase != Phase.SHOP && phase != Phase.OVER && nobodyStanding()) {
            phase = Phase.OVER
            events.add(Events.RUN_OVER, 0)
        }
    }

    /**
     * For a client's copy of the world: takes the host's clock and phase
     * instead of running its own. Nothing else about the copy is simulated.
     */
    fun adopt(tick: Int, stage: Int, phase: Phase, stageTicks: Int, players: Int, difficulty: Difficulty, bossType: Int, bossHealth: Float) {
        this.tick = tick
        this.stage = stage
        this.phase = phase
        this.stageTicks = stageTicks
        this.playerCount = players.coerceIn(1, MAX_PLAYERS)
        this.difficulty = difficulty
        shownBossType = bossType
        shownBossHealth = bossHealth
    }

    fun isBossStage() = stage % Bestiary.BOSS_EVERY == 0

    /** Type of the boss on the field, or -1. */
    fun bossType(): Int = if (boss >= 0) enemies.type[boss] else shownBossType

    /** 0..1 health of the boss on the field. */
    fun bossHealth(): Float = if (boss >= 0) (enemies.hp[boss] / enemies.maxHp[boss]).coerceIn(0f, 1f) else shownBossHealth

    fun spawnEnemy(type: Int, x: Float, y: Float): Int {
        val def = Bestiary.of(type)
        val hp = if (def.boss) def.hp * bossHpScale() else def.hp * enemyHpScale()
        val i = enemies.add(nextId++, type, x, y, hp)
        if (i >= 0) brains.prepare(i)
        return i
    }

    /** An enemy shot leaving [x], [y] at [angle]. */
    internal fun spawnHostile(x: Float, y: Float, angle: Float, speed: Float) {
        val s = speed * hostileSpeedScale()
        val id = nextId++
        val vx = cos(angle) * s
        val vy = sin(angle) * s
        if (hostile.add(id, x, y, vx, vy, HOSTILE_TICKS, 1f, Balance.HOSTILE_RADIUS, 0, -1) < 0) return
        // Shots fly straight, so other phones only need to hear about them once. The tick rides in angle.
        events.add(Events.HOSTILE_SPAWN, id, x, y, vx, vy, tick.toFloat())
    }

    /**
     * A shot from player [p]'s phone hit the enemy whose id ends in [id16].
     * The phone already saw it land; this applies it.
     */
    fun remoteHit(p: Int, id16: Int, ux: Float, uy: Float) {
        if (phase != Phase.FIGHT && phase != Phase.CLEAR) return
        val e = enemies
        for (j in 0 until e.count) {
            if (e.id[j] and 0xFFFF != id16 || e.hp[j] <= 0f) continue
            damageEnemy(j, players.damage(p), ux, uy, p)
            return
        }
    }

    /** Player [p]'s phone saw them get hit, by the enemy shot whose id ends in [shot16], or by touch when it is -1. */
    fun remoteHurt(p: Int, shot16: Int) {
        if (phase != Phase.FIGHT && phase != Phase.CLEAR) return
        if (shot16 >= 0) {
            val h = hostile
            for (i in 0 until h.count) {
                if (h.id[i] and 0xFFFF != shot16 || h.life[i] <= 0) continue
                h.life[i] = 0
                events.add(Events.HOSTILE_GONE, h.id[i])
                break
            }
        }
        if (players.alive(p) && players.invuln[p] == 0) hurtPlayer(p)
    }

    fun stageProgress() = min(stageTicks / STAGE_TICKS.toFloat(), 1f)

    fun enemySpeedScale(): Float {
        val byStage = min(Balance.ENEMY_SPEED_START + Balance.ENEMY_SPEED_PER_STAGE * (stage - 1), Balance.ENEMY_SPEED_MAX)
        return difficulty.speed * byStage * (1f + Balance.ENEMY_SPEED_IN_STAGE * stageProgress())
    }

    fun enemyHpScale() = difficulty.toughness *
        (1f + Balance.ENEMY_HP_PER_STAGE * max(0, stage - Balance.ENEMY_HP_FROM_STAGE + 1)) *
        (1f + Balance.HP_PER_PLAYER * (presentCount() - 1))

    fun bossHpScale(): Float {
        val round = (stage / Bestiary.BOSS_EVERY - 1).coerceAtLeast(0) / Bestiary.bossOrder.size
        return difficulty.bossToughness *
            (1f + Balance.BOSS_HP_PER_PLAYER * (presentCount() - 1)) *
            (1f + Balance.BOSS_HP_PER_ROUND * round)
    }

    /** Enemies attack more often as stages go by. */
    fun attackRate() = difficulty.attackRate * min(1f + Balance.ATTACK_RATE_PER_STAGE * (stage - 1), Balance.ATTACK_RATE_MAX)

    fun hostileSpeedScale() = difficulty.shotSpeed * min(1f + Balance.HOSTILE_SPEED_PER_STAGE * (stage - 1), Balance.HOSTILE_SPEED_MAX)

    /** More players bring more enemies. */
    fun crowd() = 1f + Balance.CROWD_PER_PLAYER * (presentCount() - 1)

    fun presentCount(): Int {
        var n = 0
        for (p in 0 until playerCount) if (players.present[p]) n++
        return max(n, 1)
    }

    fun nearestPlayer(x: Float, y: Float, within: Float = Float.MAX_VALUE): Int {
        var best = -1
        var bestD2 = within * within
        for (p in 0 until playerCount) {
            if (!players.alive(p)) continue
            val dx = players.x[p] - x
            val dy = players.y[p] - y
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) {
                bestD2 = d2
                best = p
            }
        }
        return best
    }

    private fun coinBaseFor(stage: Int) = max(1, Balance.COIN_GROWTH.pow(stage - 1).roundToInt())

    private fun endFight() {
        phase = Phase.CLEAR
        clearTicks = 0
        events.add(Events.STAGE_CLEAR, stage)
    }

    private fun bringBoss() {
        val round = (stage / Bestiary.BOSS_EVERY - 1).coerceAtLeast(0)
        val type = Bestiary.bossOrder[round % Bestiary.bossOrder.size]
        val r = Bestiary.of(type).radius
        val i = spawnEnemy(type, ARENA_W / 2f, -r - 8f)
        if (i >= 0) {
            boss = i
            events.add(Events.BOSS_IN, type, ARENA_W / 2f, 0f)
        }
    }

    private fun findBoss() {
        boss = -1
        for (i in 0 until enemies.count) {
            if (Bestiary.of(enemies.type[i]).boss) {
                boss = i
                return
            }
        }
    }

    private fun updatePlayer(p: Int, input: PlayerInput) {
        val pl = players
        val standing = pl.alive(p)
        // Alone, falling ends the run. In a team, a downed player can still crawl toward help.
        if (!standing && playerCount == 1) return
        if (pl.invuln[p] > 0) pl.invuln[p]--
        if (pl.hurt[p] > 0) pl.hurt[p]--

        val travel = pl.moveSpeed(p) * (if (standing) 1f else Balance.DOWNED_SPEED) * TICK_SECONDS
        val r = Balance.PLAYER_RADIUS
        if (input.hasPosition) {
            val dx = input.posX - pl.x[p]
            val dy = input.posY - pl.y[p]
            val d = sqrt(dx * dx + dy * dy)
            val limit = travel * Balance.REMOTE_SLACK
            if (d > limit) {
                pl.x[p] += dx / d * limit
                pl.y[p] += dy / d * limit
            } else {
                pl.x[p] = input.posX
                pl.y[p] = input.posY
            }
        } else {
            var mx = input.moveX
            var my = input.moveY
            val len = sqrt(mx * mx + my * my)
            if (len > 1f) {
                mx /= len
                my /= len
            }
            pl.x[p] += mx * travel
            pl.y[p] += my * travel
        }
        pl.x[p] = pl.x[p].coerceIn(r, ARENA_W - r)
        pl.y[p] = pl.y[p].coerceIn(r, ARENA_H - r)

        val aimLen = sqrt(input.aimX * input.aimX + input.aimY * input.aimY)
        val aiming = aimLen > 0.0001f
        if (aiming) {
            pl.aimX[p] = input.aimX / aimLen
            pl.aimY[p] = input.aimY / aimLen
        }

        if (!standing) {
            pl.reload[p] = 0f
            return
        }
        pl.reload[p] -= TICK_SECONDS
        if (input.fire && aiming) {
            if (pl.reload[p] <= 0f) {
                shoot(p)
                pl.reload[p] += 1f / pl.fireRate(p)
            }
        } else if (pl.reload[p] < 0f) {
            pl.reload[p] = 0f
        }
    }

    private fun shoot(p: Int) {
        val pl = players
        val speed = pl.bulletSpeed(p)
        val life = (pl.range(p) / speed * TICK_HZ).toInt()
        // A remote player's shots here are for show; their own phone reports what they hit.
        val damage = if (remote[p]) 0f else pl.damage(p)
        val radius = pl.bulletRadius(p)
        val pierce = pl.pierce(p)
        val muzzle = Balance.PLAYER_RADIUS * MUZZLE
        val n = pl.volley(p)
        val aim = atan2(pl.aimY[p], pl.aimX[p])
        for (k in 0 until n) {
            val a = aim + (k - (n - 1) / 2f) * Balance.MULTISHOT_SPREAD
            val dx = cos(a)
            val dy = sin(a)
            bullets.add(nextId++, pl.x[p] + dx * muzzle, pl.y[p] + dy * muzzle, dx * speed, dy * speed, life, damage, radius, pierce, p)
        }
        events.add(Events.SHOT, p, pl.x[p], pl.y[p], pl.aimX[p], pl.aimY[p])
    }

    /** Pushes overlapping enemies apart, the lighter one moving more. The boss shoulders everyone aside. */
    private fun separateEnemies() {
        val e = enemies
        for (i in 0 until e.count) {
            if (i == boss) continue
            val di = Bestiary.of(e.type[i])
            forEnemiesNear(e.x[i], e.y[i], di.radius + Bestiary.maxRegularRadius) { j ->
                if (j > i) {
                    val dj = Bestiary.of(e.type[j])
                    var dx = e.x[j] - e.x[i]
                    var dy = e.y[j] - e.y[i]
                    val reach = di.radius + dj.radius
                    val d2 = dx * dx + dy * dy
                    if (d2 < reach * reach) {
                        var d = sqrt(d2)
                        if (d < 0.001f) {
                            dx = 1f
                            dy = 0f
                            d = 1f
                        }
                        val push = (reach - d) * SEPARATION / d
                        val share = dj.mass / (di.mass + dj.mass)
                        e.x[i] -= dx * push * share
                        e.y[i] -= dy * push * share
                        e.x[j] += dx * push * (1f - share)
                        e.y[j] += dy * push * (1f - share)
                    }
                }
            }
        }
        val b = boss
        if (b < 0) return
        val br = Bestiary.of(e.type[b]).radius
        forEnemiesNear(e.x[b], e.y[b], br + Bestiary.maxRegularRadius) { j ->
            val reach = br + Bestiary.of(e.type[j]).radius
            val dx = e.x[j] - e.x[b]
            val dy = e.y[j] - e.y[b]
            val d2 = dx * dx + dy * dy
            if (d2 < reach * reach && d2 > 0.0001f) {
                val d = sqrt(d2)
                e.x[j] = e.x[b] + dx / d * reach
                e.y[j] = e.y[b] + dy / d * reach
            }
        }
    }

    private fun moveBullets() {
        val b = bullets
        for (i in 0 until b.count) {
            b.life[i] -= 1
            if (b.life[i] <= 0) continue
            val stepX = b.vx[i] * TICK_SECONDS
            val stepY = b.vy[i] * TICK_SECONDS
            val parts = max(1, ceil(sqrt(stepX * stepX + stepY * stepY) / SUBSTEP).toInt())
            val sx = stepX / parts
            val sy = stepY / parts
            for (s in 0 until parts) {
                b.x[i] += sx
                b.y[i] += sy
                if (b.x[i] < -BULLET_MARGIN || b.x[i] > ARENA_W + BULLET_MARGIN ||
                    b.y[i] < -BULLET_MARGIN || b.y[i] > ARENA_H + BULLET_MARGIN
                ) {
                    b.life[i] = 0
                    break
                }
                val j = enemyTouching(b.x[i], b.y[i], b.radius[i], b.lastHit[i])
                if (j < 0) continue
                b.lastHit[i] = enemies.id[j]
                if (b.damage[i] > 0f) hitEnemy(j, i)
                if (b.pierce[i] > 0) {
                    b.pierce[i]--
                } else {
                    b.life[i] = 0
                    break
                }
            }
        }
    }

    private fun hitEnemy(j: Int, bullet: Int) {
        val b = bullets
        val speed = sqrt(b.vx[bullet] * b.vx[bullet] + b.vy[bullet] * b.vy[bullet])
        damageEnemy(j, b.damage[bullet], b.vx[bullet] / speed, b.vy[bullet] / speed, b.owner[bullet])
    }

    private fun damageEnemy(j: Int, damage: Float, ux: Float, uy: Float, owner: Int) {
        val e = enemies
        val def = Bestiary.of(e.type[j])
        val shielded = e.type[j] == Bestiary.APIARY && brains.escorts > 0
        e.hp[j] -= damage * (if (shielded) SHIELDED_DAMAGE else 1f)
        e.flash[j] = FLASH_TICKS
        e.kx[j] += ux * Balance.KNOCKBACK / def.mass
        e.ky[j] += uy * Balance.KNOCKBACK / def.mass
        if (e.hp[j] > 0f) {
            events.add(Events.ENEMY_HIT, e.type[j], e.x[j], e.y[j], ux, uy)
            return
        }
        kill(j, owner, ux, uy)
    }

    private fun kill(j: Int, owner: Int, ux: Float, uy: Float) {
        val e = enemies
        val def = Bestiary.of(e.type[j])
        events.add(Events.ENEMY_KILLED, e.type[j], e.x[j], e.y[j], ux, uy, e.angle[j])
        players.kills[owner]++
        val drops = if (def.coinChance >= 1f || rng.nextFloat() < def.coinChance) def.coins else 0
        val fling = if (def.boss) 260f else 150f
        repeat(drops) {
            val a = rng.range(0f, TWO_PI)
            val s = rng.range(60f, fling)
            coins.add(nextId++, e.x[j], e.y[j], cos(a) * s, sin(a) * s, coinValueFor(owner))
        }
        brains.died(j)
        if (def.boss) {
            events.add(Events.BOSS_DOWN, e.type[j], e.x[j], e.y[j])
            // The rest of the stage's enemies scatter once the boss falls.
            if (phase == Phase.FIGHT) endFight()
        }
    }

    /** Wealth adds a fraction; the fraction becomes an extra coin that often. */
    private fun coinValueFor(p: Int): Int {
        val exact = coinBase * (1f + players.wealth(p)) * difficulty.coinValue
        val whole = exact.toInt()
        return if (rng.nextFloat() < exact - whole) whole + 1 else whole
    }

    private fun moveHostile() {
        val h = hostile
        val pl = players
        for (i in 0 until h.count) {
            h.life[i] -= 1
            if (h.life[i] <= 0) continue
            h.x[i] += h.vx[i] * TICK_SECONDS
            h.y[i] += h.vy[i] * TICK_SECONDS
            if (h.x[i] < -BULLET_MARGIN || h.x[i] > ARENA_W + BULLET_MARGIN ||
                h.y[i] < -BULLET_MARGIN || h.y[i] > ARENA_H + BULLET_MARGIN
            ) {
                h.life[i] = 0
                continue
            }
            for (p in 0 until playerCount) {
                if (!pl.alive(p) || pl.invuln[p] > 0 || remote[p]) continue
                val reach = h.radius[i] + Balance.PLAYER_RADIUS * GRAZE
                val dx = pl.x[p] - h.x[i]
                val dy = pl.y[p] - h.y[i]
                if (dx * dx + dy * dy < reach * reach) {
                    h.life[i] = 0
                    events.add(Events.HOSTILE_GONE, h.id[i])
                    hurtPlayer(p)
                    break
                }
            }
        }
    }

    private fun touchPlayers() {
        val e = enemies
        val pl = players
        val r = Balance.PLAYER_RADIUS
        for (p in 0 until playerCount) {
            if (!pl.alive(p)) continue
            val px = pl.x[p]
            val py = pl.y[p]
            var touched = false
            forEnemiesNear(px, py, r + Bestiary.maxRegularRadius) { j ->
                if (e.hp[j] > 0f && pushOff(j, px, py, r)) touched = true
            }
            val b = boss
            if (b >= 0 && e.hp[b] > 0f) {
                val reach = r + Bestiary.of(e.type[b]).radius
                val dx = e.x[b] - px
                val dy = e.y[b] - py
                if (dx * dx + dy * dy < reach * reach) {
                    touched = true
                    // The boss does not budge; the player is pushed out instead.
                    val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                    pl.x[p] = (e.x[b] - dx / d * reach).coerceIn(r, ARENA_W - r)
                    pl.y[p] = (e.y[b] - dy / d * reach).coerceIn(r, ARENA_H - r)
                }
            }
            // Enemies still make way for a remote player, but only their own phone says when it hurts.
            if (touched && pl.invuln[p] == 0 && !remote[p]) hurtPlayer(p)
        }
    }

    /** Moves enemy [j] out of the player at [px], [py]; true if they were touching. */
    private fun pushOff(j: Int, px: Float, py: Float, r: Float): Boolean {
        val e = enemies
        val reach = r + Bestiary.of(e.type[j]).radius
        val dx = e.x[j] - px
        val dy = e.y[j] - py
        val d2 = dx * dx + dy * dy
        if (d2 >= reach * reach) return false
        val d = sqrt(d2)
        if (d > 0.001f) {
            e.x[j] = px + dx / d * reach
            e.y[j] = py + dy / d * reach
        }
        return true
    }

    private fun hurtPlayer(p: Int) {
        val e = enemies
        val pl = players
        val px = pl.x[p]
        val py = pl.y[p]
        pl.hp[p] -= 1
        pl.invuln[p] = INVULN_TICKS
        pl.hurt[p] = HURT_TICKS
        events.add(Events.PLAYER_HIT, p, px, py)

        val radius = Balance.HIT_SHOVE_RADIUS
        for (j in 0 until e.count) {
            if (j == boss) continue
            val dx = e.x[j] - px
            val dy = e.y[j] - py
            val d2 = dx * dx + dy * dy
            if (d2 >= radius * radius || d2 < 0.0001f) continue
            val d = sqrt(d2)
            val force = Balance.HIT_SHOVE * (1f - d / radius) / Bestiary.of(e.type[j]).mass
            e.kx[j] += dx / d * force
            e.ky[j] += dy / d * force
        }

        if (pl.hp[p] <= 0) {
            pl.hp[p] = 0
            events.add(Events.PLAYER_DOWN, p, px, py)
        }
    }

    /** A downed player comes back once a standing teammate has stayed close long enough. */
    private fun revivePlayers() {
        if (playerCount < 2) return
        val pl = players
        val reach = Balance.REVIVE_REACH
        for (p in 0 until playerCount) {
            if (!pl.present[p]) continue
            if (pl.alive(p)) {
                pl.revive[p] = 0
                continue
            }
            var helped = false
            for (q in 0 until playerCount) {
                if (q == p || !pl.alive(q)) continue
                val dx = pl.x[q] - pl.x[p]
                val dy = pl.y[q] - pl.y[p]
                if (dx * dx + dy * dy < reach * reach) helped = true
            }
            if (!helped) {
                // Stepping away loses progress, but not all at once.
                pl.revive[p] = max(0, pl.revive[p] - 2)
                continue
            }
            pl.revive[p]++
            if (pl.revive[p] >= REVIVE_TICKS) {
                pl.revive[p] = 0
                pl.hp[p] = max(1, pl.maxHp[p] / 2)
                pl.invuln[p] = INVULN_TICKS * 2
                events.add(Events.REVIVED, p, pl.x[p], pl.y[p])
            }
        }
    }

    private fun moveCoins() {
        val c = coins
        val pl = players
        val pickup = Balance.PLAYER_RADIUS + Balance.COIN_RADIUS + 2f
        for (i in 0 until c.count) {
            var t = c.target[i]
            if (t >= 0 && !pl.alive(t)) t = -1
            if (t < 0) t = magnetFor(c.x[i], c.y[i])
            c.target[i] = t

            if (t >= 0) {
                val dx = pl.x[t] - c.x[i]
                val dy = pl.y[t] - c.y[i]
                val d = sqrt(dx * dx + dy * dy)
                if (d < pickup) {
                    pl.coins[t] += c.value[i]
                    pl.earned[t] += c.value[i]
                    events.add(Events.COIN, t, c.x[i], c.y[i])
                    c.value[i] = 0
                    continue
                }
                val speed = min(sqrt(c.vx[i] * c.vx[i] + c.vy[i] * c.vy[i]) + MAGNET_ACCEL * TICK_SECONDS, MAGNET_TOP_SPEED)
                c.vx[i] = dx / d * speed
                c.vy[i] = dy / d * speed
            } else {
                c.vx[i] *= COIN_DRAG
                c.vy[i] *= COIN_DRAG
            }
            val cr = Balance.COIN_RADIUS
            c.x[i] = (c.x[i] + c.vx[i] * TICK_SECONDS).coerceIn(cr, ARENA_W - cr)
            c.y[i] = (c.y[i] + c.vy[i] * TICK_SECONDS).coerceIn(cr, ARENA_H - cr)
        }
    }

    /** The nearest standing player whose magnet reaches this point, or -1. */
    private fun magnetFor(x: Float, y: Float): Int {
        var best = -1
        var bestD2 = Float.MAX_VALUE
        for (p in 0 until playerCount) {
            if (!players.alive(p)) continue
            val dx = players.x[p] - x
            val dy = players.y[p] - y
            val d2 = dx * dx + dy * dy
            val reach = players.magnet(p)
            if (d2 < reach * reach && d2 < bestD2) {
                bestD2 = d2
                best = p
            }
        }
        return best
    }

    /** Sweeps the arena: stragglers vanish, loose coins go to the nearest player, the shop opens. */
    private fun openShop() {
        val e = enemies
        for (i in 0 until e.count) events.add(Events.ENEMY_GONE, e.type[i], e.x[i], e.y[i], 0f, 0f, e.angle[i])
        e.clear()
        boss = -1
        shownBossType = -1
        val c = coins
        for (i in 0 until c.count) {
            var p = nearestPlayer(c.x[i], c.y[i])
            if (p < 0) p = firstPresent()
            players.coins[p] += c.value[i]
            players.earned[p] += c.value[i]
        }
        c.clear()
        bullets.clear()
        hostile.clear()
        phase = Phase.SHOP
        shop.open(this)
        events.add(Events.SHOP_OPEN, stage)
    }

    private fun firstPresent(): Int {
        for (p in 0 until playerCount) if (players.present[p]) return p
        return 0
    }

    private fun nobodyStanding(): Boolean {
        for (p in 0 until playerCount) if (players.alive(p)) return false
        return true
    }

    private fun enemyTouching(x: Float, y: Float, r: Float, skipId: Int): Int {
        val e = enemies
        val reach = r + Bestiary.maxRegularRadius
        for (row in grid.row(y - reach)..grid.row(y + reach)) {
            for (col in grid.col(x - reach)..grid.col(x + reach)) {
                var j = grid.head[row * grid.cols + col]
                while (j >= 0) {
                    if (e.hp[j] > 0f && e.id[j] != skipId) {
                        val rr = r + Bestiary.of(e.type[j]).radius
                        val dx = e.x[j] - x
                        val dy = e.y[j] - y
                        if (dx * dx + dy * dy < rr * rr) return j
                    }
                    j = grid.next[j]
                }
            }
        }
        val b = boss
        if (b >= 0 && e.hp[b] > 0f && e.id[b] != skipId) {
            val rr = r + Bestiary.of(e.type[b]).radius
            val dx = e.x[b] - x
            val dy = e.y[b] - y
            if (dx * dx + dy * dy < rr * rr) return b
        }
        return -1
    }

    internal inline fun forEnemiesNear(x: Float, y: Float, reach: Float, action: (Int) -> Unit) {
        for (row in grid.row(y - reach)..grid.row(y + reach)) {
            for (col in grid.col(x - reach)..grid.col(x + reach)) {
                var j = grid.head[row * grid.cols + col]
                while (j >= 0) {
                    action(j)
                    j = grid.next[j]
                }
            }
        }
    }
}
