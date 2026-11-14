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

/** Minutes of play between break reminders; 0 turns them off. */
val BREAK_CHOICES = listOf(0, 20, 30, 45, 60, 90)

/** What the developer page can change for a solo run. Never applies to a team, and a run that uses any of it is not recorded. */
data class Dev(
    /** Replaces the chosen difficulty; null leaves it. */
    val difficulty: Difficulty? = null,
    val startStage: Int = 1,
    val startCoins: Int = 0,
    val invincible: Boolean = false,
    val noSpawns: Boolean = false,
    val speed: Float = 1f,
) {
    val active get() = this != Dev()
}

val DEV_SPEEDS = listOf(0.5f, 1f, 1.5f, 2f)

data class Settings(
    val difficulty: Difficulty = Difficulty.NORMAL,
    val stickSize: StickSize = StickSize.MEDIUM,
    val leftHanded: Boolean = false,
    val vibration: Boolean = true,
    val shotVibration: Boolean = true,
    val showStats: Boolean = false,
    /** Ping and link type in co-op, top right. */
    val showLink: Boolean = true,
    /** Minutes between break reminders, one of [BREAK_CHOICES]. */
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
            .putString(KEY_DEV_DIFFICULTY, next.dev.difficulty?.name ?: "")
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
        breakEvery = prefs.getInt(KEY_BREAK, 0).takeIf { it in BREAK_CHOICES } ?: 0,
        tutorialSeen = prefs.getBoolean(KEY_TUTORIAL, false),
        devUnlocked = prefs.getBoolean(KEY_DEV_UNLOCKED, false),
        dev = Dev(
            difficulty = Difficulty.entries.firstOrNull { it.name == prefs.getString(KEY_DEV_DIFFICULTY, null) },
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
        const val KEY_DEV_DIFFICULTY = "dev_difficulty"
        const val KEY_DEV_STAGE = "dev_stage"
        const val KEY_DEV_COINS = "dev_coins"
        const val KEY_DEV_INVINCIBLE = "dev_invincible"
        const val KEY_DEV_NO_SPAWNS = "dev_no_spawns"
        const val KEY_DEV_SPEED = "dev_speed"
    }
}
