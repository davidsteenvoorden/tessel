package dev.david.tessel.platform.store

import android.content.Context
import android.content.pm.ApplicationInfo
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.platform.game.RunSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class StickSize(val label: String, val radiusDp: Float) {
    SMALL("Small", 46f),
    MEDIUM("Medium", 56f),
    LARGE("Large", 68f),
}

data class Settings(
    val difficulty: Difficulty = Difficulty.NORMAL,
    val stickSize: StickSize = StickSize.MEDIUM,
    val leftHanded: Boolean = false,
    val vibration: Boolean = true,
    val shotVibration: Boolean = true,
    val showStats: Boolean = false,
    /** Ping and link type in co-op, top right. */
    val showLink: Boolean = true,
)

data class Best(val stage: Int, val seconds: Int)

/** Best run per difficulty. Deeper stage wins; on the same stage, the longer run. */
data class Records(val best: Map<Difficulty, Best> = emptyMap()) {
    fun of(difficulty: Difficulty) = best[difficulty]
}

/** Settings and records in one small preferences file. Writes happen off the main thread. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("tessel", Context.MODE_PRIVATE)
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val settingsFlow = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = settingsFlow

    private val recordsFlow = MutableStateFlow(readRecords())
    val records: StateFlow<Records> = recordsFlow

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
            .apply()
    }

    /** Stores the run if it beats the best for its difficulty. Returns true when it did. */
    fun record(run: RunSummary): Boolean {
        val old = recordsFlow.value.of(run.difficulty)
        val better = old == null || run.stage > old.stage || (run.stage == old.stage && run.seconds > old.seconds)
        if (!better) return false
        recordsFlow.value = Records(recordsFlow.value.best + (run.difficulty to Best(run.stage, run.seconds)))
        prefs.edit()
            .putInt(KEY_BEST_STAGE + run.difficulty.name, run.stage)
            .putInt(KEY_BEST_SECONDS + run.difficulty.name, run.seconds)
            .apply()
        return true
    }

    private fun readSettings() = Settings(
        difficulty = enumOr(prefs.getString(KEY_DIFFICULTY, null), Difficulty.NORMAL),
        stickSize = enumOr(prefs.getString(KEY_STICK, null), StickSize.MEDIUM),
        leftHanded = prefs.getBoolean(KEY_LEFT, false),
        vibration = prefs.getBoolean(KEY_VIBRATION, true),
        shotVibration = prefs.getBoolean(KEY_SHOT_VIBRATION, true),
        showStats = prefs.getBoolean(KEY_STATS, debuggable),
        showLink = prefs.getBoolean(KEY_LINK, true),
    )

    private fun readRecords(): Records {
        val best = HashMap<Difficulty, Best>()
        for (d in Difficulty.entries) {
            val stage = prefs.getInt(KEY_BEST_STAGE + d.name, 0)
            if (stage > 0) best[d] = Best(stage, prefs.getInt(KEY_BEST_SECONDS + d.name, 0))
        }
        return Records(best)
    }

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
        const val KEY_BEST_STAGE = "best_stage_"
        const val KEY_BEST_SECONDS = "best_seconds_"
    }
}
