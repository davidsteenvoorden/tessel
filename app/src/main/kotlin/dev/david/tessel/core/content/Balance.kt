package dev.david.tessel.core.content

/** Starting numbers. Distances are arena units, speeds are units per second. */
object Balance {
    const val PLAYER_RADIUS = 14f

    /** Cut from 220 after user test #2 ("too overpowered at the start"); Speed upgrades buy it back. */
    const val PLAYER_SPEED = 195f
    const val PLAYER_HP = 6
    const val INVULN_SECONDS = 0.6f

    /** Cut from 3.5 after user test #2; Fire Rate upgrades buy it back. */
    const val FIRE_RATE = 2.8f
    const val BULLET_SPEED = 700f
    const val BULLET_RANGE = 520f
    const val BULLET_RADIUS = 4f
    const val BULLET_DAMAGE = 1f

    /** Radians between neighbouring bullets of one multishot volley, about 7 degrees. */
    const val MULTISHOT_SPREAD = 0.12f

    /** Speed a hit adds to an enemy of mass 1. */
    const val KNOCKBACK = 140f

    /** Getting hit pushes nearby enemies away, so the player is not hit again the moment the grace period ends. */
    const val HIT_SHOVE = 320f
    const val HIT_SHOVE_RADIUS = 110f

    const val COIN_RADIUS = 5f
    const val MAGNET_RADIUS = 70f

    // Co-op: a player at 0 health is down, not out. They crawl at a fraction
    // of their speed and come back if their partner stands close for a while.
    const val DOWNED_SPEED = 0.4f
    const val REVIVE_REACH = 56f
    const val REVIVE_SECONDS = 2.5f

    /** A remote player's reported position is accepted, but it may not move faster than this times its speed. */
    const val REMOTE_SLACK = 1.6f

    const val STAGE_SECONDS = 60

    /** After a stage ends the stragglers get this long before the shop opens anyway. */
    const val CLEAR_SECONDS = 8

    // Enemy speed starts well below the listed speeds and grows each stage,
    // plus a little within a stage, up to a ceiling.
    const val ENEMY_SPEED_START = 0.72f
    const val ENEMY_SPEED_PER_STAGE = 0.06f
    const val ENEMY_SPEED_MAX = 1.2f
    const val ENEMY_SPEED_IN_STAGE = 0.08f

    /** Enemies keep their listed health through the first zone, then gain this much per stage. */
    const val ENEMY_HP_PER_STAGE = 0.18f
    const val ENEMY_HP_FROM_STAGE = 4

    /** Coin value multiplies by this each stage. */
    const val COIN_GROWTH = 1.22

    // Enemy shots: hollow, slow enough to read, a little faster and more frequent each stage.
    const val HOSTILE_RADIUS = 5.5f
    const val HOSTILE_SECONDS = 7f
    const val HOSTILE_SPEED_PER_STAGE = 0.02f
    const val HOSTILE_SPEED_MAX = 1.4f
    const val ATTACK_RATE_PER_STAGE = 0.03f
    const val ATTACK_RATE_MAX = 1.6f

    /** How long a shooter glows before it fires, so a shot never comes from nowhere. */
    const val TELL_SECONDS = 0.45f

    // Dashers and the Lancer.
    const val DASH_WINDUP = 0.7f
    const val DASH_SPEED = 560f
    const val DASH_SECONDS = 0.6f
    const val DASH_RECOVER = 0.9f

    // More players: more enemies rather than tougher ones, and a boss that lasts.
    const val CROWD_PER_PLAYER = 0.5f
    const val HP_PER_PLAYER = 0.08f
    const val BOSS_HP_PER_PLAYER = 0.65f
    const val SOFT_CAP = 250
    const val SOFT_CAP_PER_PLAYER = 18

    /** While a boss is up the director sends this share of its usual stream. */
    const val BOSS_STAGE_SPAWNS = 0.35f

    /** Each time the boss line comes round again, bosses get this much more health. */
    const val BOSS_HP_PER_ROUND = 1.5f
}
