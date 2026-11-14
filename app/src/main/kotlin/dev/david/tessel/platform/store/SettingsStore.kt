package dev.david.tessel.platform.store

import android.content.Context
import android.content.pm.ApplicationInfo
import dev.david.tessel.core.content.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class StickSize(val label: String, val radiusDp: Float) {
    SMALL("Small", 46f),
    MEDIUM("Medium", 56f),
    LARGE("Large", 68f),
}

/** The longest time between break reminders, in minutes. 0 turns them off. */
const val MAX_BREAK_MINUTES = 600

/** What the developer page can change for a solo run. Never applies to a team, and a run that uses any of it is not recorded. */
data class Dev(
    /** Kinds that do not come in. Empty leaves the usual order; anything else brings the rest from the first stage. */
    val off: Set<Int> = emptySet(),
    /** A boss kind that comes in every stage, or -1. */
    val boss: Int = -1,
    val tweaks: Map<Int, Tweak> = emptyMap(),
    /** Upgrade levels to start a trial run with, by upgrade id. */
    val upgrades: Map<Int, Int> = emptyMap(),
    val startStage: Int = 1,
    val startCoins: Int = 0,
    val invincible: Boolean = false,
    val noSpawns: Boolean = false,
    val speed: Float = 1f,
) {
    val active get() = this != Dev()

    /** Anything that changes how a run plays once it is going; a start stage or coins only matter at the start. */
    val changesTheRun get() = invincible || noSpawns || speed != 1f || off.isNotEmpty() || boss >= 0 || tweaks.values.any { it != Tweak() }

    fun tweak(kind: Int) = tweaks[kind] ?: Tweak()
}

/** One kind of enemy changed for a trial run: speed and health as multiples, and how much health each of its hits takes. */
data class Tweak(val speed: Float = 1f, val health: Float = 1f, val damage: Int = 1)

val DEV_SPEEDS = listOf(0.5f, 1f, 1.5f, 2f)
val TWEAK_SPEEDS = listOf(0.5f, 1f, 2f, 3f)
val TWEAK_HEALTH = listOf(0.5f, 1f, 2f, 5f, 10f)
val TWEAK_DAMAGE = listOf(1, 2, 3, 5)

private fun encodeTweaks(m: Map<Int, Tweak>) = m.entries.joinToString(";") { (k, t) -> "$k:${t.speed}:${t.health}:${t.damage}" }

private fun decodeTweaks(s: String?): Map<Int, Tweak> = s.orEmpty().split(';').mapNotNull { part ->
    val f = part.split(':')
    if (f.size != 4) return@mapNotNull null
    val k = f[0].toIntOrNull() ?: return@mapNotNull null
    k to Tweak(f[1].toFloatOrNull() ?: 1f, f[2].toFloatOrNull() ?: 1f, f[3].toIntOrNull() ?: 1)
}.toMap()

data class Settings(
    val difficulty: Difficulty = Difficulty.NORMAL,
    val stickSize: StickSize = StickSize.MEDIUM,
    val leftHanded: Boolean = false,
    val vibration: Boolean = true,
    val shotVibration: Boolean = true,
    val showStats: Boolean = false,
    /** Ping and link type in co-op, top right. */
    val showLink: Boolean = true,
    /** Minutes between break reminders, 0 for off. */
    val breakEvery: Int = 0,
    val tutorialSeen: Boolean = false,
    val devUnlocked: Boolean = false,
    val dev: Dev = Dev(),
)

/** Settings in one small preferences file. Writes happen off the main thread. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("tessel", Context.MODE_PRIVATE)
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val settingsFlow = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = settingsFlow

    fun update(change: (Settings) -> Settings) {
        val next = change(settingsFlow.value)
        if (next == settingsFlow.value) return
        settingsFlow.value = next
        prefs.edit()
            .putString(KEY_DIFFICULTY, next.difficulty.name)
            .putString(KEY_STICK, next.stickSize.name)
            .putBoolean(KEY_LEFT, next.leftHanded)
            .putBoolean(KEY_VIBRATION, next.vibration)
            .putBoolean(KEY_SHOT_VIBRATION, next.shotVibration)
            .putBoolean(KEY_STATS, next.showStats)
            .putBoolean(KEY_LINK, next.showLink)
            .putInt(KEY_BREAK, next.breakEvery)
            .putBoolean(KEY_TUTORIAL, next.tutorialSeen)
            .putBoolean(KEY_DEV_UNLOCKED, next.devUnlocked)
            .putString(KEY_DEV_OFF, next.dev.off.joinToString(","))
            .putInt(KEY_DEV_BOSS, next.dev.boss)
            .putString(KEY_DEV_TWEAKS, encodeTweaks(next.dev.tweaks))
            .putString(KEY_DEV_UPGRADES, next.dev.upgrades.entries.joinToString(";") { "${it.key}:${it.value}" })
            .putInt(KEY_DEV_STAGE, next.dev.startStage)
            .putInt(KEY_DEV_COINS, next.dev.startCoins)
            .putBoolean(KEY_DEV_INVINCIBLE, next.dev.invincible)
            .putBoolean(KEY_DEV_NO_SPAWNS, next.dev.noSpawns)
            .putFloat(KEY_DEV_SPEED, next.dev.speed)
            .apply()
    }

    private fun readSettings() = Settings(
        difficulty = enumOr(prefs.getString(KEY_DIFFICULTY, null), Difficulty.NORMAL),
        stickSize = enumOr(prefs.getString(KEY_STICK, null), StickSize.MEDIUM),
        leftHanded = prefs.getBoolean(KEY_LEFT, false),
        vibration = prefs.getBoolean(KEY_VIBRATION, true),
        shotVibration = prefs.getBoolean(KEY_SHOT_VIBRATION, true),
        showStats = prefs.getBoolean(KEY_STATS, debuggable),
        showLink = prefs.getBoolean(KEY_LINK, true),
        breakEvery = prefs.getInt(KEY_BREAK, 0).coerceIn(0, MAX_BREAK_MINUTES),
        tutorialSeen = prefs.getBoolean(KEY_TUTORIAL, false),
        devUnlocked = prefs.getBoolean(KEY_DEV_UNLOCKED, false),
        dev = Dev(
            off = prefs.getString(KEY_DEV_OFF, null).orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet(),
            boss = prefs.getInt(KEY_DEV_BOSS, -1),
            tweaks = decodeTweaks(prefs.getString(KEY_DEV_TWEAKS, null)),
            upgrades = prefs.getString(KEY_DEV_UPGRADES, null).orEmpty().split(';').mapNotNull {
                val f = it.split(':')
                val id = f.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val n = f.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                id to n
            }.toMap(),
            startStage = prefs.getInt(KEY_DEV_STAGE, 1).coerceIn(1, 99),
            startCoins = prefs.getInt(KEY_DEV_COINS, 0).coerceAtLeast(0),
            invincible = prefs.getBoolean(KEY_DEV_INVINCIBLE, false),
            noSpawns = prefs.getBoolean(KEY_DEV_NO_SPAWNS, false),
            speed = prefs.getFloat(KEY_DEV_SPEED, 1f).takeIf { it in DEV_SPEEDS } ?: 1f,
        ),
    )

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback

    private companion object {
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_STICK = "stick_size"
        const val KEY_LEFT = "left_handed"
        const val KEY_VIBRATION = "vibration"
        const val KEY_SHOT_VIBRATION = "shot_vibration"
        const val KEY_STATS = "show_stats"
        const val KEY_LINK = "show_link"
        const val KEY_BREAK = "break_every"
        const val KEY_TUTORIAL = "tutorial_seen"
        const val KEY_DEV_UNLOCKED = "dev_unlocked"
        const val KEY_DEV_OFF = "dev_off"
        const val KEY_DEV_BOSS = "dev_boss"
        const val KEY_DEV_TWEAKS = "dev_tweaks"
        const val KEY_DEV_UPGRADES = "dev_upgrades"
        const val KEY_DEV_STAGE = "dev_stage"
        const val KEY_DEV_COINS = "dev_coins"
        const val KEY_DEV_INVINCIBLE = "dev_invincible"
        const val KEY_DEV_NO_SPAWNS = "dev_no_spawns"
        const val KEY_DEV_SPEED = "dev_speed"
    }
}
