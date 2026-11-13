package dev.david.tessel.core.content

/** How a body gets around. */
enum class Move {
    /** Straight at the nearest player. */
    CHASE,

    /** Flies straight and glances off the walls, paying no attention to anyone. */
    BOUNCE,

    /** Holds [EnemyDef.range] away from the nearest player and sidesteps. */
    HOVER,

    /** Circles the nearest player at [EnemyDef.range]. */
    ORBIT,

    /** Walks in, stops, aims a line for a moment, then charges along it. */
    DASH,

    /** Picks a spot inside the arena, parks there and works from it. */
    PARK,

    /** Circles the boss. Chases once the boss is gone. */
    ESCORT,

    /** Each boss has its own routine in the sim. */
    BOSS,
}

/** What it shoots. Hostile shots are always slow, hollow and dodgeable. */
enum class Attack { NONE, AIMED, FAN, RING, SPIRAL, SPAWN }

class EnemyDef(
    val name: String,
    val shape: Shape,
    val radius: Float,
    val hp: Float,
    val speed: Float,
    /** Divides knockback; heavy things barely flinch. */
    val mass: Float,
    /** What the spawn director pays for one. */
    val cost: Float,
    val coins: Int,
    /** Its own colour, as an OKLCH hue in degrees. 10 to 100 belong to danger and coins. */
    val hue: Int,
    /** -1 deeper, 0 normal, 1 lighter than the rest at the same hue. */
    val tone: Int = 0,
    val coinChance: Float = 1f,
    val move: Move = Move.CHASE,
    val range: Float = 0f,
    val attack: Attack = Attack.NONE,
    /** Seconds between attacks at stage 1. */
    val every: Float = 0f,
    val shots: Int = 0,
    val shotSpeed: Float = 0f,
    /** Radians between neighbouring shots of a fan. */
    val spread: Float = 0f,
    /** Breaks into this type when it dies, or releases it when it attacks with [Attack.SPAWN]. */
    val child: Int = -1,
    val children: Int = 0,
    /** A ring of this many shots when it dies. */
    val deathRing: Int = 0,
    /** Turns to face where it is going. */
    val faces: Boolean = false,
    /** Turns on its own, radians per second. */
    val spin: Float = 0f,
    /** First stage the director sends it; 0 for things that only appear from others. */
    val from: Int = 0,
    /** How often the director picks it, against the others on offer. */
    val weight: Float = 1f,
    val group: Int = 1,
    val groupSpread: Int = 0,
    /** Arrives as a tight pack through one gap instead of spread along the edges. */
    val pack: Boolean = false,
    val boss: Boolean = false,
    val blurb: String = "",
)

/** Every enemy type, indexed by the type id the sim stores. */
object Bestiary {
    const val DOT = 0
    const val PIP = 1
    const val SLAB = 2
    const val DRIFTER = 3
    const val TICK = 4
    const val DASHER = 5
    const val HALVER = 6
    const val HALFLING = 7
    const val FANNER = 8
    const val BROOD = 9
    const val BLOOM = 10
    const val WISP = 11
    const val QUARTERN = 12
    const val SLIVER = 13
    const val SPINDLE = 14
    const val CINDER = 15
    const val DRONE = 16
    const val HEXARCH = 17
    const val LANCER = 18
    const val APIARY = 19
    const val GYRE = 20

    private val defs = arrayOf(
        EnemyDef(
            "Dot", Shape.CIRCLE, radius = 12f, hp = 1f, speed = 95f, mass = 1f, cost = 1f, coins = 1, hue = 185,
            from = 1, weight = 3f, group = 3, groupSpread = 2,
        ),
        EnemyDef(
            "Pip", Shape.CIRCLE, radius = 7f, hp = 1f, speed = 175f, mass = 0.5f, cost = 0.4f, coins = 1, hue = 160, tone = 1,
            coinChance = 0.35f, from = 1, weight = 2f, group = 6, groupSpread = 4, pack = true,
        ),
        EnemyDef(
            "Slab", Shape.SQUARE, radius = 19f, hp = 6f, speed = 55f, mass = 4f, cost = 4f, coins = 4, hue = 248,
            faces = true, from = 2, weight = 1.5f, group = 1, groupSpread = 1,
        ),
        EnemyDef(
            "Drifter", Shape.DIAMOND, radius = 13f, hp = 3f, speed = 125f, mass = 1f, cost = 1.5f, coins = 2, hue = 135,
            move = Move.BOUNCE, spin = 2.2f, from = 2, weight = 1.2f, group = 2, groupSpread = 2,
        ),
        EnemyDef(
            "Tick", Shape.TRIANGLE, radius = 12f, hp = 2f, speed = 85f, mass = 1f, cost = 3f, coins = 3, hue = 205,
            move = Move.HOVER, range = 300f, attack = Attack.AIMED, every = 3f, shots = 1, shotSpeed = 210f,
            faces = true, from = 3, weight = 1.3f, group = 1, groupSpread = 2,
            blurb = "Keeps its distance and shoots.",
        ),
        EnemyDef(
            "Dasher", Shape.DART, radius = 13f, hp = 3f, speed = 75f, mass = 1.2f, cost = 3f, coins = 3, hue = 292,
            move = Move.DASH, range = 330f, faces = true, from = 4, weight = 1.1f, group = 1, groupSpread = 2,
            blurb = "Aims a line, then charges down it.",
        ),
        EnemyDef(
            "Halver", Shape.HEXAGON, radius = 17f, hp = 5f, speed = 72f, mass = 2f, cost = 3f, coins = 2, hue = 122, tone = -1,
            child = HALFLING, children = 2, spin = 0.6f, from = 6, weight = 1.2f, group = 1, groupSpread = 2,
            blurb = "Splits in two.",
        ),
        EnemyDef(
            "Halfling", Shape.HEXAGON, radius = 10f, hp = 2f, speed = 135f, mass = 0.8f, cost = 0f, coins = 1, hue = 122, tone = 1,
            spin = 1.4f,
        ),
        EnemyDef(
            "Fanner", Shape.PENTAGON, radius = 15f, hp = 4f, speed = 80f, mass = 1.5f, cost = 4f, coins = 3, hue = 226,
            move = Move.HOVER, range = 270f, attack = Attack.FAN, every = 2.6f, shots = 3, shotSpeed = 195f, spread = 0.32f,
            faces = true, from = 7, weight = 1.1f, group = 1, groupSpread = 1,
            blurb = "Fires three at once.",
        ),
        EnemyDef(
            "Brood", Shape.OCTAGON, radius = 22f, hp = 12f, speed = 42f, mass = 4f, cost = 6f, coins = 6, hue = 322,
            attack = Attack.SPAWN, every = 2.6f, child = PIP, children = 5, spin = 0.3f,
            from = 8, weight = 0.8f, group = 1,
            blurb = "Lets out Pips until it is gone.",
        ),
        EnemyDef(
            "Bloom", Shape.STAR, radius = 16f, hp = 6f, speed = 60f, mass = 3f, cost = 5f, coins = 4, hue = 268, tone = 1,
            move = Move.PARK, attack = Attack.RING, every = 3.6f, shots = 10, shotSpeed = 165f, spin = 0.8f,
            from = 9, weight = 0.9f, group = 1, groupSpread = 1,
            blurb = "Settles down and pulses rings.",
        ),
        EnemyDef(
            "Wisp", Shape.TRIANGLE, radius = 10f, hp = 2f, speed = 150f, mass = 0.7f, cost = 3f, coins = 2, hue = 196, tone = 1,
            move = Move.ORBIT, range = 210f, attack = Attack.AIMED, every = 2.3f, shots = 1, shotSpeed = 235f,
            faces = true, from = 11, weight = 1f, group = 2, groupSpread = 1,
            blurb = "Circles you and takes shots.",
        ),
        EnemyDef(
            "Quartern", Shape.OCTAGON, radius = 20f, hp = 8f, speed = 62f, mass = 3f, cost = 5f, coins = 3, hue = 150, tone = -1,
            child = SLIVER, children = 4, spin = -0.5f, from = 12, weight = 1f, group = 1, groupSpread = 1,
            blurb = "Splits in four.",
        ),
        EnemyDef(
            "Sliver", Shape.TRIANGLE, radius = 8f, hp = 1f, speed = 195f, mass = 0.5f, cost = 0f, coins = 1, hue = 150, tone = 1,
            coinChance = 0.5f, faces = true,
        ),
        EnemyDef(
            "Spindle", Shape.CROSS, radius = 18f, hp = 10f, speed = 45f, mass = 5f, cost = 7f, coins = 5, hue = 308,
            move = Move.PARK, attack = Attack.SPIRAL, every = 0.42f, shots = 3, shotSpeed = 150f, spin = 1.2f,
            from = 13, weight = 0.6f, group = 1,
            blurb = "A turning turret.",
        ),
        EnemyDef(
            "Cinder", Shape.PENTAGON, radius = 16f, hp = 7f, speed = 88f, mass = 2f, cost = 5f, coins = 4, hue = 343,
            deathRing = 8, spin = 0.9f, from = 14, weight = 1f, group = 1, groupSpread = 2,
            blurb = "Bursts into shots when it breaks.",
        ),
        EnemyDef(
            "Drone", Shape.DIAMOND, radius = 9f, hp = 3f, speed = 150f, mass = 0.6f, cost = 0f, coins = 1, hue = 330, tone = 1,
            move = Move.ESCORT, range = 105f, attack = Attack.AIMED, every = 3.2f, shots = 1, shotSpeed = 200f, faces = true,
        ),
        EnemyDef(
            "Hexarch", Shape.HEXAGON, radius = 46f, hp = 220f, speed = 42f, mass = 40f, cost = 0f, coins = 30, hue = 182, tone = -1,
            move = Move.BOSS, spin = 0.35f, boss = true, blurb = "Rings of shots with a gap in each. Find the gap.",
        ),
        EnemyDef(
            "Lancer", Shape.DART, radius = 40f, hp = 450f, speed = 95f, mass = 40f, cost = 0f, coins = 40, hue = 286, tone = -1,
            move = Move.BOSS, faces = true, boss = true, blurb = "Watch the line. It charges along it.",
        ),
        EnemyDef(
            "Apiary", Shape.OCTAGON, radius = 50f, hp = 650f, speed = 32f, mass = 50f, cost = 0f, coins = 50, hue = 326, tone = -1,
            move = Move.BOSS, spin = 0.25f, child = DRONE, boss = true,
            blurb = "Shrugs off shots while its drones fly. Clear them first.",
        ),
        EnemyDef(
            "Gyre", Shape.STAR, radius = 48f, hp = 900f, speed = 40f, mass = 60f, cost = 0f, coins = 60, hue = 212, tone = -1,
            move = Move.BOSS, boss = true, blurb = "Spirals that change direction. Keep moving with them.",
        ),
    )

    /** Bosses in the order they appear, every [BOSS_EVERY] stages, then round again. */
    val bossOrder = intArrayOf(HEXARCH, LANCER, APIARY, GYRE)

    const val BOSS_EVERY = 5

    val size get() = defs.size
    val maxRadius = defs.maxOf { it.radius }

    /** Largest body the director sends; bosses are checked separately. */
    val maxRegularRadius = defs.filter { !it.boss }.maxOf { it.radius }

    fun of(type: Int) = defs[type]
}
