package dev.david.tessel.core.net

import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.sim.Events
import dev.david.tessel.core.sim.MAX_BULLETS
import dev.david.tessel.core.sim.MAX_COINS
import dev.david.tessel.core.sim.MAX_ENEMIES
import dev.david.tessel.core.sim.MAX_HOSTILE
import dev.david.tessel.core.sim.MAX_PLAYERS
import dev.david.tessel.core.sim.Phase
import dev.david.tessel.core.sim.SHOP_SLOTS

/** One decoded host snapshot, as the client keeps it. Reused, never reallocated. */
class Snapshot {
    var tick = 0
    var stage = 1
    var phase = Phase.FIGHT
    var stageTicks = 0
    var paused = false

    /** The host is holding the game while a phone tries to reconnect. */
    var waiting = false
    var difficulty = Difficulty.NORMAL
    var playerCount = 2

    /** Bit p set when player p has pressed Ready in the shop. */
    var ready = 0

    /** Bit p set while player p is still in the game. */
    var present = 0

    var bossType = -1
    var bossHealth = 0f

    /** Local clock when it arrived. */
    var arrivalNanos = 0L

    /** Arrival order on this phone; the host's tick can repeat while it is paused. */
    var seq = 0L

    val px = FloatArray(MAX_PLAYERS)
    val py = FloatArray(MAX_PLAYERS)
    val aimX = FloatArray(MAX_PLAYERS)
    val aimY = FloatArray(MAX_PLAYERS)
    val hp = IntArray(MAX_PLAYERS)
    val maxHp = IntArray(MAX_PLAYERS)
    val hurt = BooleanArray(MAX_PLAYERS)
    val invuln = BooleanArray(MAX_PLAYERS)

    /** 0..1 progress of a revive in progress. */
    val revive = FloatArray(MAX_PLAYERS)
    val coins = IntArray(MAX_PLAYERS)
    val earned = IntArray(MAX_PLAYERS)
    val kills = IntArray(MAX_PLAYERS)

    // What a client needs to predict its own player's movement and shots.
    val moveSpeed = FloatArray(MAX_PLAYERS)
    val fireRate = FloatArray(MAX_PLAYERS)
    val bulletSpeed = FloatArray(MAX_PLAYERS)
    val range = FloatArray(MAX_PLAYERS)
    val bulletRadius = FloatArray(MAX_PLAYERS)
    val volley = IntArray(MAX_PLAYERS)
    val pierce = IntArray(MAX_PLAYERS)

    var enemyCount = 0
    val enemyId = IntArray(MAX_ENEMIES)
    val enemyType = IntArray(MAX_ENEMIES)
    val enemyX = FloatArray(MAX_ENEMIES)
    val enemyY = FloatArray(MAX_ENEMIES)
    val enemyFlash = BooleanArray(MAX_ENEMIES)
    val enemyCue = FloatArray(MAX_ENEMIES)
    val enemyAngle = FloatArray(MAX_ENEMIES)
    val enemyIndex = IdMap(1024)

    var bulletCount = 0
    val bulletX = FloatArray(MAX_BULLETS)
    val bulletY = FloatArray(MAX_BULLETS)
    val bulletVx = FloatArray(MAX_BULLETS)
    val bulletVy = FloatArray(MAX_BULLETS)
    val bulletRadiusOf = FloatArray(MAX_BULLETS)

    var hostileCount = 0
    val hostileX = FloatArray(MAX_HOSTILE)
    val hostileY = FloatArray(MAX_HOSTILE)
    val hostileVx = FloatArray(MAX_HOSTILE)
    val hostileVy = FloatArray(MAX_HOSTILE)

    var coinCount = 0
    val coinId = IntArray(MAX_COINS)
    val coinX = FloatArray(MAX_COINS)
    val coinY = FloatArray(MAX_COINS)
    val coinIndex = IdMap(1024)

    val events = Events(1024)

    // The receiving player's shop, present only while the phase is SHOP.
    var hasShop = false
    val shopItem = IntArray(SHOP_SLOTS)
    val shopLevel = IntArray(SHOP_SLOTS)
    val shopPrice = IntArray(SHOP_SLOTS)
    val shopMaxed = BooleanArray(SHOP_SLOTS)
    val shopAffordable = BooleanArray(SHOP_SLOTS)
    var healPrice = 0
    var healsLeft = 0
    var canHeal = false
    var rerollPrice = 0
    var canReroll = false
}

/**
 * Int-to-int map with open addressing. Cleared by bumping a generation
 * number rather than wiping the arrays, so it can be rebuilt every snapshot
 * without allocating.
 */
class IdMap(capacity: Int) {
    private val mask = capacity - 1
    private val shift = 32 - Integer.numberOfTrailingZeros(capacity)
    private val keys = IntArray(capacity)
    private val values = IntArray(capacity)
    private val stamp = IntArray(capacity)
    private var generation = 1

    init {
        require(capacity and mask == 0) { "capacity must be a power of two" }
    }

    fun clear() {
        generation++
    }

    fun put(key: Int, value: Int) {
        var i = hash(key)
        while (stamp[i] == generation && keys[i] != key) i = (i + 1) and mask
        stamp[i] = generation
        keys[i] = key
        values[i] = value
    }

    /** Value for [key], or -1. */
    operator fun get(key: Int): Int {
        var i = hash(key)
        while (stamp[i] == generation) {
            if (keys[i] == key) return values[i]
            i = (i + 1) and mask
        }
        return -1
    }

    /** Fibonacci hashing: multiply by 2^32 / golden ratio and keep the top bits. */
    private fun hash(key: Int) = (key * -0x61c88647) ushr shift
}
