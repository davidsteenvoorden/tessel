package dev.david.tessel.core.content

class PerkDef(val name: String, val blurb: String)

/** Run-long special effects, offered three at a time after each boss. Ids are bit positions in a player's perk mask. */
object Perks {
    const val SECOND_WIND = 0
    const val THICK_SKIN = 1
    const val SIPHON = 2
    const val OVERCLOCK = 3
    const val HEAVY_ROUNDS = 4
    const val QUICK_FEET = 5
    const val MAGNET_CORE = 6
    const val LAST_STAND = 7
    const val SHOCKWAVE = 8
    const val HAGGLER = 9

    private val defs = arrayOf(
        PerkDef("Second Wind", "Once a stage, a hit that would down you leaves you on one health."),
        PerkDef("Thick Skin", "Two more health pips, filled."),
        PerkDef("Siphon", "Every 25th kill heals one pip."),
        PerkDef("Overclock", "Fire 30% faster after eight seconds without a hit."),
        PerkDef("Heavy Rounds", "+30% damage and one more pierce, but 15% slower fire."),
        PerkDef("Quick Feet", "+15% move speed."),
        PerkDef("Magnet Core", "Coins are drawn in from twice as far."),
        PerkDef("Last Stand", "+50% damage while you have two health or fewer."),
        PerkDef("Shockwave", "A hit on you damages and shoves the enemies close by."),
        PerkDef("Haggler", "Shop upgrades cost 10% less."),
    )

    val count get() = defs.size

    fun of(id: Int) = defs[id]

    /** How many perks a boss offers at once. */
    const val OFFERED = 3
}

/** Extra traits an enemy can carry, shown by a bright outline. */
object Elite {
    const val NONE = 0
    const val ARMORED = 1
    const val FAST = 2
    const val SPLITTING = 3
    const val BURSTING = 4
    const val COUNT = 5

    /** Marks a boss the Core has borrowed. Never picked at random, so it sits past [COUNT]. */
    const val TETHERED = 5

    private val names = arrayOf("", "Armored", "Fast", "Splitting", "Bursting", "Tethered")

    fun name(trait: Int) = names[trait]

    /** Health added to an elite on top of its kind's. */
    const val HEALTH = 1.6f

    /** Shots taken by an armored elite deal this share of their damage. */
    const val ARMOR = 0.5f
    const val SPEED = 1.5f

    /** A kill sometimes drops a surge: faster and harder shots for a while. */
    const val SURGE_CHANCE = 0.25f
    const val SURGE_SECONDS = 15
    const val SURGE_FIRE = 1.35f
    const val SURGE_DAMAGE = 1.25f

    /** Chance that an enemy turning up at [stage] is elite. */
    fun chance(stage: Int) = if (stage < 6) 0f else minOf(0.04f + 0.012f * (stage - 6), 0.18f)
}
