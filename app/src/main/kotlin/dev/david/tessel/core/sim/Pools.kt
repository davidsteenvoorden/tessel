package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Balance
import dev.david.tessel.core.content.Upgrades

/*
 * Entity storage as parallel arrays. Removal swaps the last entry into the
 * hole, so indices move between ticks; ids do not.
 */

class PlayerState {
    val x = FloatArray(MAX_PLAYERS)
    val y = FloatArray(MAX_PLAYERS)
    val prevX = FloatArray(MAX_PLAYERS)
    val prevY = FloatArray(MAX_PLAYERS)
    val aimX = FloatArray(MAX_PLAYERS)
    val aimY = FloatArray(MAX_PLAYERS)
    val hp = IntArray(MAX_PLAYERS)
    val maxHp = IntArray(MAX_PLAYERS)

    /** Ticks left during which contact does no damage. */
    val invuln = IntArray(MAX_PLAYERS)

    /** Ticks left of the red hit tint. */
    val hurt = IntArray(MAX_PLAYERS)

    /** Seconds until the next shot may leave. */
    val reload = FloatArray(MAX_PLAYERS)

    /** Ticks a partner has spent standing next to this downed player. */
    val revive = IntArray(MAX_PLAYERS)

    /** False for an empty seat, or for a player whose phone left the game. */
    val present = BooleanArray(MAX_PLAYERS)
    /** Coins in hand, spent in the shop. */
    val coins = IntArray(MAX_PLAYERS)

    /** Every coin picked up this run, spent or not. */
    val earned = IntArray(MAX_PLAYERS)
    val kills = IntArray(MAX_PLAYERS)

    /** Shop upgrade levels, indexed by [Upgrades] id. */
    val levels = Array(MAX_PLAYERS) { IntArray(Upgrades.count) }

    fun alive(i: Int) = hp[i] > 0 && present[i]

    fun damage(p: Int) = scaled(p, Upgrades.DAMAGE, Balance.BULLET_DAMAGE)
    fun fireRate(p: Int) = scaled(p, Upgrades.FIRE_RATE, Balance.FIRE_RATE)
    fun volley(p: Int) = 1 + levels[p][Upgrades.MULTISHOT]
    fun bulletSpeed(p: Int) = scaled(p, Upgrades.VELOCITY, Balance.BULLET_SPEED)
    fun pierce(p: Int) = levels[p][Upgrades.PIERCE]
    fun range(p: Int) = scaled(p, Upgrades.RANGE, Balance.BULLET_RANGE)
    fun moveSpeed(p: Int) = scaled(p, Upgrades.SPEED, Balance.PLAYER_SPEED)
    fun wealth(p: Int) = Upgrades.of(Upgrades.WEALTH).step * levels[p][Upgrades.WEALTH]
    fun magnet(p: Int) = scaled(p, Upgrades.MAGNET, Balance.MAGNET_RADIUS)
    fun bulletRadius(p: Int) = scaled(p, Upgrades.CALIBER, Balance.BULLET_RADIUS)

    private fun scaled(p: Int, upgrade: Int, base: Float) = base * (1f + Upgrades.of(upgrade).step * levels[p][upgrade])

    /** [bonusHp] comes from the difficulty: Easy starts with more health, Impossible with less. */
    fun reset(count: Int, bonusHp: Int = 0) {
        for (i in 0 until MAX_PLAYERS) {
            // A lone player starts dead centre, a team stands in a row around it.
            x[i] = ARENA_W / 2f + (i - (count - 1) / 2f) * 70f
            y[i] = ARENA_H / 2f
            prevX[i] = x[i]
            prevY[i] = y[i]
            aimX[i] = 1f
            aimY[i] = 0f
            maxHp[i] = (Balance.PLAYER_HP + bonusHp).coerceAtLeast(1)
            hp[i] = if (i < count) maxHp[i] else 0
            invuln[i] = 0
            hurt[i] = 0
            reload[i] = 0f
            revive[i] = 0
            present[i] = i < count
            coins[i] = 0
            earned[i] = 0
            kills[i] = 0
            levels[i].fill(0)
        }
    }

    fun remember() {
        x.copyInto(prevX)
        y.copyInto(prevY)
    }
}

class BulletPool(private val capacity: Int) {
    val id = IntArray(capacity)
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val prevX = FloatArray(capacity)
    val prevY = FloatArray(capacity)
    val vx = FloatArray(capacity)
    val vy = FloatArray(capacity)
    val life = IntArray(capacity)
    val damage = FloatArray(capacity)
    val radius = FloatArray(capacity)
    val owner = IntArray(capacity)

    /** Enemies it may still pass through. */
    val pierce = IntArray(capacity)

    /** Id of the enemy it last went through, so a piercing bullet does not hit the same one twice. */
    val lastHit = IntArray(capacity)

    var count = 0
        private set

    fun add(id: Int, x: Float, y: Float, vx: Float, vy: Float, life: Int, damage: Float, radius: Float, pierce: Int, owner: Int): Int {
        if (count == capacity) return -1
        val i = count++
        this.id[i] = id
        this.x[i] = x
        this.y[i] = y
        prevX[i] = x
        prevY[i] = y
        this.vx[i] = vx
        this.vy[i] = vy
        this.life[i] = life
        this.damage[i] = damage
        this.radius[i] = radius
        this.pierce[i] = pierce
        lastHit[i] = -1
        this.owner[i] = owner
        return i
    }

    fun remember() {
        x.copyInto(prevX, endIndex = count)
        y.copyInto(prevY, endIndex = count)
    }

    /** Drops every bullet whose life ran out. */
    fun sweep() {
        for (i in count - 1 downTo 0) if (life[i] <= 0) removeAt(i)
    }

    fun clear() {
        count = 0
    }

    private fun removeAt(i: Int) {
        val last = --count
        if (i == last) return
        id[i] = id[last]
        x[i] = x[last]
        y[i] = y[last]
        prevX[i] = prevX[last]
        prevY[i] = prevY[last]
        vx[i] = vx[last]
        vy[i] = vy[last]
        life[i] = life[last]
        damage[i] = damage[last]
        radius[i] = radius[last]
        pierce[i] = pierce[last]
        lastHit[i] = lastHit[last]
        owner[i] = owner[last]
    }
}

class EnemyPool(private val capacity: Int) {
    val id = IntArray(capacity)
    val type = IntArray(capacity)
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val prevX = FloatArray(capacity)
    val prevY = FloatArray(capacity)

    /** Knockback velocity, decays every tick. */
    val kx = FloatArray(capacity)
    val ky = FloatArray(capacity)
    val hp = FloatArray(capacity)

    /** Ticks left of the white hit flash. */
    val flash = IntArray(capacity)

    /** Facing in radians; only shapes that are not round use it. */
    val angle = FloatArray(capacity)
    val maxHp = FloatArray(capacity)

    /** Own velocity, for things that keep a heading (bouncers, chargers) or a target point (parkers). */
    val vx = FloatArray(capacity)
    val vy = FloatArray(capacity)

    /** Ticks until the next attack. */
    val timer = IntArray(capacity)

    /** Step in a routine (charging, parked, a boss phase) and ticks spent in it. */
    val mode = IntArray(capacity)
    val modeTicks = IntArray(capacity)

    /** Free per-routine value: orbit direction, spiral angle, children released. */
    val aux = FloatArray(capacity)

    /** 0..1 build-up before an attack, drawn so nothing fires without warning. */
    val cue = FloatArray(capacity)

    var count = 0
        private set

    fun add(id: Int, type: Int, x: Float, y: Float, hp: Float): Int {
        if (count == capacity) return -1
        val i = count++
        this.id[i] = id
        this.type[i] = type
        this.x[i] = x
        this.y[i] = y
        prevX[i] = x
        prevY[i] = y
        kx[i] = 0f
        ky[i] = 0f
        this.hp[i] = hp
        maxHp[i] = hp
        flash[i] = 0
        angle[i] = 0f
        vx[i] = 0f
        vy[i] = 0f
        timer[i] = 0
        mode[i] = 0
        modeTicks[i] = 0
        aux[i] = 0f
        cue[i] = 0f
        return i
    }

    fun remember() {
        x.copyInto(prevX, endIndex = count)
        y.copyInto(prevY, endIndex = count)
    }

    fun sweep() {
        for (i in count - 1 downTo 0) if (hp[i] <= 0f) removeAt(i)
    }

    fun clear() {
        count = 0
    }

    private fun removeAt(i: Int) {
        val last = --count
        if (i == last) return
        id[i] = id[last]
        type[i] = type[last]
        x[i] = x[last]
        y[i] = y[last]
        prevX[i] = prevX[last]
        prevY[i] = prevY[last]
        kx[i] = kx[last]
        ky[i] = ky[last]
        hp[i] = hp[last]
        maxHp[i] = maxHp[last]
        flash[i] = flash[last]
        angle[i] = angle[last]
        vx[i] = vx[last]
        vy[i] = vy[last]
        timer[i] = timer[last]
        mode[i] = mode[last]
        modeTicks[i] = modeTicks[last]
        aux[i] = aux[last]
        cue[i] = cue[last]
    }
}

class CoinPool(private val capacity: Int) {
    val id = IntArray(capacity)
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val prevX = FloatArray(capacity)
    val prevY = FloatArray(capacity)
    val vx = FloatArray(capacity)
    val vy = FloatArray(capacity)
    val value = IntArray(capacity)

    /** Player the coin is flying to, or -1 while it lies on the floor. */
    val target = IntArray(capacity)

    var count = 0
        private set

    fun add(id: Int, x: Float, y: Float, vx: Float, vy: Float, value: Int): Int {
        if (count == capacity) return -1
        val i = count++
        this.id[i] = id
        this.x[i] = x
        this.y[i] = y
        prevX[i] = x
        prevY[i] = y
        this.vx[i] = vx
        this.vy[i] = vy
        this.value[i] = value
        target[i] = -1
        return i
    }

    fun remember() {
        x.copyInto(prevX, endIndex = count)
        y.copyInto(prevY, endIndex = count)
    }

    fun sweep() {
        for (i in count - 1 downTo 0) if (value[i] <= 0) removeAt(i)
    }

    fun clear() {
        count = 0
    }

    private fun removeAt(i: Int) {
        val last = --count
        if (i == last) return
        id[i] = id[last]
        x[i] = x[last]
        y[i] = y[last]
        prevX[i] = prevX[last]
        prevY[i] = prevY[last]
        vx[i] = vx[last]
        vy[i] = vy[last]
        value[i] = value[last]
        target[i] = target[last]
    }
}
